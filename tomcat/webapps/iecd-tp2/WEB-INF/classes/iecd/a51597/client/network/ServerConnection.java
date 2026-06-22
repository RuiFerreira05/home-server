package iecd.a51597.client.network;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.screens.handlers.ClientInviteHandler;
import iecd.a51597.client.cli.screens.handlers.ClientSearchHandler;
import iecd.a51597.client.config.ClientConfiguration;
import iecd.a51597.client.session.ClientSessionManager;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.builders.client.ClientMessageBuilder;
import iecd.a51597.common.protocol.exceptions.CommException;
import iecd.a51597.common.protocol.parsers.CommParser;
import iecd.a51597.common.protocol.types.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the TCP connection to the server on the client side.
 */
public class ServerConnection implements Runnable {

    private volatile boolean connected = false;
    private final Map<UUID, CompletableFuture<Message>> pendingRequests;

    private Socket socket;
    private final String serverHost;
    private final int serverPort;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private final CommParser parser;
    private final ClientMessageBuilder messageBuilder;
    private int reconnectAttempts;
    private final ClientSearchHandler searchHandler;
    private final ClientInviteHandler inviteHandler;
    private final Client client;

    private ClientSessionManager sessionManager = null;

    /**
     * Logger configured for TCP networking status reporting.
     */
    public final Logger logger = LogManager.getLogger(ServerConnection.class);

    /**
     * Creates a new connection coordinator to the remote TCP socket game server.
     *
     * @param client the parent client instance
     * @param serverHost the remote server hostname or IP address
     * @param serverPort the TCP port number
     * @param parser XML stream parser for deserializing messages
     * @param messageBuilder XML bytes builder for serializing messages
     */
    public ServerConnection(Client client, String serverHost, int serverPort, CommParser parser, ClientMessageBuilder messageBuilder) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.parser = parser;
        this.messageBuilder = messageBuilder;
        this.client = client;

        this.searchHandler = new ClientSearchHandler(this);
        this.inviteHandler = new ClientInviteHandler(this);
        this.pendingRequests = new ConcurrentHashMap<>();
        this.reconnectAttempts = ClientConfiguration.RECONNECT_ATTEMPTS;
    }
    /**
     * Sends an XML request message asynchronously over the TCP connection and correlation UUID mapping.
     *
     * @param request the protocol Message request envelopes
     * @return a CompletableFuture resolving to the server's response message
     */
    public CompletableFuture<Message> sendRequest(Message request) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        pendingRequests.put(request.messageId(), future);

        byte[] payload = messageBuilder.getMessageInBytes(request);
        if (payload == null) {
            pendingRequests.remove(request.messageId());
            future.completeExceptionally(new IllegalStateException("Failed to serialize request"));
            return future;
        }

        if (!writeFrame(payload)) {
            pendingRequests.remove(request.messageId());
            future.completeExceptionally(new IllegalStateException("Failed to send request to server"));
        }

        return future;
    }

    private synchronized boolean writeFrame(byte[] payload) {
        if (!connected) {
            logger.warn("Attempted to send message while not connected to server.");
            return false;
        }
        try {
            outputStream.writeInt(payload.length);
            outputStream.write(payload);
            outputStream.flush();
            return true;
        } catch (IOException e) {
            logger.error("Error sending message to server", e);
            closeConnection();
            return false;
        }
    }

    private void closeConnection() {
        if (socket == null || socket.isClosed()) return;
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("Error closing connection", e);
        }
    }

    /**
     * Executes the background reader loop reading length-prefixed XML frames from the TCP socket.
     */
    @Override
    public void run() {
        connected = true;
        while (connected) {
            try (Socket socket = new Socket(serverHost, serverPort)) {

                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                this.socket = socket;
                this.inputStream = inputStream;
                this.outputStream = outputStream;
                this.connected = true;

                logger.info("Connected to server at {}:{}", serverHost, serverPort);

                while (connected) {
                    int length = inputStream.readInt();
                    byte[] payload = new byte[length];
                    inputStream.readFully(payload);
                    Message message = parser.parseMessage(new ByteArrayInputStream(payload));
                    if (message != null) {
                        UUID messageId = message.messageId();
                        logger.info("Received message from server: {}", messageId);
                        if (pendingRequests.containsKey(messageId)) {
                            logger.info("Received message is a response to pending request: {}", messageId);
                            pendingRequests.remove(messageId).complete(message);
                        } else {
                            if (message.messageType() == MessageType.PUSH) {
                                logger.info("Message is push");
                                if (message.body() instanceof MessageBody.GameInvitePush) {
                                    logger.info("Message is a game invite");
                                    client.getPendingInvites().add((MessageBody.GameInvitePush) message.body());
                                } else if (message.body() instanceof MessageBody.GameInviteCancelPush cancelPush) {
                                    logger.info("Message is a game invite cancellation");
                                    client.getPendingInvites().removeIf(push -> push.gameId().equals(cancelPush.gameId()));
                                } else if (message.body() instanceof MessageBody.GameMove move) {
                                    logger.info("Message is a game move push");
                                    client.getActiveGame(move.gameId()).ifPresent(gc -> {
                                        try {
                                            iecd.a51597.common.game.dotsandboxes.DotsAndBoxesMove dbMove = (iecd.a51597.common.game.dotsandboxes.DotsAndBoxesMove) 
                                                    new iecd.a51597.common.game.dotsandboxes.DotsAndBoxesMoveCodec().deserialize(move.rawMove());
                                            gc.applyOpponentMove(dbMove);
                                        } catch (Exception e) {
                                            logger.error("Failed to parse or apply background move push", e);
                                        }
                                    });
                                } else if (message.body() instanceof MessageBody.GameOver go) {
                                    logger.info("Message is game over push");
                                    client.getActiveGame(go.gameId()).ifPresent(gc -> gc.getState().forceGameOver(go.winnerId()));
                                    client.getSessionManager().updateUser(go.user());
                                } else if (message.body() instanceof MessageBody.GameOverDraw god) {
                                    logger.info("Message is game over draw push");
                                    client.getActiveGame(god.gameId()).ifPresent(gc -> gc.getState().forceGameOver(null));
                                    client.getSessionManager().updateUser(god.user());
                                }
                                client.getCliHandler().getStateMachine().getCurrentScreen().handlePush(message);
                            }
                        }
                    } else {
                        logger.warn("Received invalid message from server");
                    }
                }
            } catch (IOException e) {
                logger.error("IO error in server connection", e);
                if (reconnectAttempts == 0) {
                    shutdown();
                    return;
                }
                logger.warn("Attempting to reconnect... ({} attempts remaining)", reconnectAttempts);
                reconnectAttempts--;
            } catch (CommException e) {
                logger.error("Protocol error in server connection", e);
            }
        }
    }

    /**
     * Closes the TCP connection and stops the execution loop.
     */
    public void shutdown() {
        logger.info("Shutting down server connection");
        connected = false;
    }

    /**
     * Gets the active client session manager.
     *
     * @return the session manager
     */
    public ClientSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Sets the client session manager.
     *
     * @param sessionManager the session manager to associate
     */
    public void setSessionManager(ClientSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Gets the client user search handler.
     *
     * @return the search handler
     */
    public ClientSearchHandler getSearchHandler() {
        return searchHandler;
    }

    /**
     * Gets the client game invitation handler.
     *
     * @return the invite handler
     */
    public ClientInviteHandler getInviteHandler() {
        return inviteHandler;
    }

    /**
     * Gets the parent Client bootstrap context.
     *
     * @return the parent client
     */
    public Client getClient() {
        return client;
    }
}
