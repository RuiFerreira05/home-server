package iecd.a51597.web;

import iecd.a51597.client.config.ClientConfiguration;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.builders.client.ClientMessageBuilder;
import iecd.a51597.common.protocol.builders.client.XMLClientMessageBuilder;
import iecd.a51597.common.protocol.exceptions.CommException;
import iecd.a51597.common.protocol.parsers.CommParser;
import iecd.a51597.common.protocol.parsers.XMLParser;
import iecd.a51597.common.protocol.types.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Acts as an HTTP-to-TCP bridge for a specific HTTP user session.
 * Manages a persistent TCP socket to the Dots and Boxes game server.
 */
public class ServerBridge implements Runnable {

    private static final Logger logger = LogManager.getLogger(ServerBridge.class);

    private final String serverHost;
    private final int serverPort;
    private final CommParser parser;
    private final ClientMessageBuilder messageBuilder;

    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private volatile boolean connected = false;
    private Thread listenerThread;

    // Track request-response pairs using correlated UUIDs
    private final Map<UUID, CompletableFuture<Message>> pendingRequests;
    
    // Store server push messages to be retrieved by AJAX polling
    private final Queue<Message> pushQueue;

    /**
     * Constructs a new ServerBridge.
     *
     * @param serverHost the host name or IP address of the game server
     * @param serverPort the port number of the game server
     */
    public ServerBridge(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.parser = new XMLParser();
        this.messageBuilder = new XMLClientMessageBuilder();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.pushQueue = new ConcurrentLinkedQueue<>();
    }

    /**
     * Connects to the TCP server and starts the background reader thread.
     *
     * @throws IOException if a network error occurs while establishing the connection
     */
    public synchronized void connect() throws IOException {
        if (connected) return;

        logger.info("Connecting to game server at {}:{}", serverHost, serverPort);
        this.socket = new Socket(serverHost, serverPort);
        this.inputStream = new DataInputStream(socket.getInputStream());
        this.outputStream = new DataOutputStream(socket.getOutputStream());
        this.connected = true;

        this.listenerThread = new Thread(this, "server-bridge-reader-" + socket.getLocalPort());
        this.listenerThread.setDaemon(true);
        this.listenerThread.start();
        logger.info("Connected successfully. Reader thread started.");
    }

    /**
     * Sends a request to the server and waits synchronously for the response.
     *
     * @param request the request message to send
     * @param timeoutSeconds the maximum time to wait for a response, in seconds
     * @return the response message received from the server
     * @throws Exception if sending fails or the timeout is exceeded
     */
    public Message sendRequest(Message request, long timeoutSeconds) throws Exception {
        if (!connected) {
            throw new IllegalStateException("Not connected to game server");
        }

        CompletableFuture<Message> future = new CompletableFuture<>();
        pendingRequests.put(request.messageId(), future);

        byte[] payload = messageBuilder.getMessageInBytes(request);
        if (payload == null) {
            pendingRequests.remove(request.messageId());
            throw new IllegalArgumentException("Failed to serialize request XML");
        }

        // Send frame (4-byte length + payload)
        synchronized (this) {
            outputStream.writeInt(payload.length);
            outputStream.write(payload);
            outputStream.flush();
        }

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingRequests.remove(request.messageId());
            throw e;
        }
    }

    /**
     * Sends a request to the server and waits synchronously for the response with a default 10 seconds timeout.
     *
     * @param request the request message to send
     * @return the response message received from the server
     * @throws Exception if sending fails or the timeout is exceeded
     */
    public Message sendRequest(Message request) throws Exception {
        return sendRequest(request, 10);
    }

    /**
     * Polls and drains all pending server pushes.
     *
     * @return a list of all parsed push messages received since the last poll
     */
    public List<Message> pollPushMessages() {
        List<Message> pushes = new ArrayList<>();
        Message push;
        while ((push = pushQueue.poll()) != null) {
            pushes.add(push);
        }
        return pushes;
    }

    /**
     * Background thread reader loop. Listens for server messages, parses them,
     * and dispatches them to pending request futures or the push message queue.
     */
    @Override
    public void run() {
        while (connected) {
            try {
                // Read frame length
                int length = inputStream.readInt();
                if (length <= 0) {
                    throw new IOException("Received invalid frame length: " + length);
                }

                // Read full payload
                byte[] payload = new byte[length];
                inputStream.readFully(payload);

                // Parse XML
                Message message = parser.parseMessage(new ByteArrayInputStream(payload));
                if (message == null) {
                    logger.warn("Received null/invalid protocol message from server");
                    continue;
                }

                UUID messageId = message.messageId();
                if (message.messageType() == MessageType.RESPONSE) {
                    CompletableFuture<Message> future = pendingRequests.remove(messageId);
                    if (future != null) {
                        future.complete(message);
                    } else {
                        logger.warn("Received response for unknown/expired request ID: {}", messageId);
                    }
                } else if (message.messageType() == MessageType.PUSH) {
                    logger.info("Received PUSH message from server: action={}", message.actionType());
                    pushQueue.add(message);
                }

            } catch (IOException e) {
                if (connected) {
                    logger.error("IO exception in server bridge reader loop", e);
                    shutdown();
                }
                break;
            } catch (CommException e) {
                logger.error("Protocol parser error in server bridge", e);
            }
        }
    }

    /**
     * Closes the TCP connection and shuts down thread resources.
     */
    public synchronized void shutdown() {
        if (!connected) return;
        logger.info("Shutting down ServerBridge TCP connection.");
        connected = false;

        // Complete any pending futures exceptionally
        for (UUID id : pendingRequests.keySet()) {
            CompletableFuture<Message> future = pendingRequests.remove(id);
            if (future != null) {
                future.completeExceptionally(new IOException("Connection closed to game server"));
            }
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing socket", e);
        }
    }

    /**
     * Checks whether the bridge is currently connected to the game server.
     *
     * @return true if connected and socket is open, false otherwise
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}
