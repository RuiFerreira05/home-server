package iecd.a51597.client;

import iecd.a51597.client.cli.ClientCliHandler;
import iecd.a51597.client.config.ClientConfiguration;
import iecd.a51597.client.game.GameController;
import iecd.a51597.client.network.ServerConnection;
import iecd.a51597.client.session.ClientSessionManager;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.builders.client.XMLClientMessageBuilder;
import iecd.a51597.common.protocol.parsers.XMLParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side entry point.
 */
public class Client {

    private final ServerConnection serverConnection;
    private final ClientCliHandler cliHandler;
    private final ClientSessionManager sessionManager;
    private static volatile Client instance;
    private final List<MessageBody.GameInvitePush> pendingInvites = new CopyOnWriteArrayList<>();
    private final List<GameController> activeGames = new CopyOnWriteArrayList<>();

    private static final Logger logger = LogManager.getLogger(Client.class);

    private Client() {
        logger.info("Initializing Client Configuration");
        ClientConfiguration.load();
        serverConnection = new ServerConnection(
                this,
                ClientConfiguration.SERVER_IP,
                ClientConfiguration.SERVER_PORT,
                new XMLParser(),
                new XMLClientMessageBuilder()
        );
        sessionManager = new ClientSessionManager(serverConnection);
        serverConnection.setSessionManager(sessionManager);

        cliHandler = new ClientCliHandler(this);
        logger.info("Client bootstrapping complete");
    }

    /**
     * Retrieves the thread-safe singleton instance of the Client.
     *
     * @return the active Client instance
     */
    public static Client getInstance() {
        if (instance == null) {
            synchronized (Client.class) {
                if (instance == null) {
                    instance = new Client();
                }
            }
        }
        return instance;
    }

    /**
     * Performs clean exit and shutdown of the Client CLI and connection.
     */
    public void exit() {
        logger.info("Shutting down client");
        cliHandler.running = false;
        serverConnection.shutdown();
    }

    /**
     * Gets the active TCP server connection controller.
     *
     * @return the server connection
     */
    public ServerConnection getServerConnection() {
        return serverConnection;
    }

    /**
     * Gets the client session manager holding authenticated user credentials.
     *
     * @return the session manager
     */
    public ClientSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Gets the CLI input handler processing state changes.
     *
     * @return the CLI handler
     */
    public ClientCliHandler getCliHandler() {
        return cliHandler;
    }

    /**
     * Gets the list of current pending game invites received from the server.
     *
     * @return the list of pending invites
     */
    public List<MessageBody.GameInvitePush> getPendingInvites() {
        return pendingInvites;
    }

    /**
     * Gets all concurrent active game controllers mapped for this client session.
     *
     * @return the list of active games
     */
    public List<GameController> getActiveGames() {
        return activeGames;
    }

    /**
     * Locates a specific active game controller by its unique match UUID.
     *
     * @param gameId the unique match UUID
     * @return an Optional holding the game controller if found, or empty otherwise
     */
    public java.util.Optional<GameController> getActiveGame(java.util.UUID gameId) {
        return activeGames.stream().filter(g -> g.getState().getGameId().equals(gameId)).findFirst();
    }

    /**
     * Main entry point for starting the Client CLI.
     *
     * @param args CLI arguments (unused)
     */
    public static void main(String[] args) {
        Client client = Client.getInstance();
        Thread connectionThread = new Thread(client.serverConnection, "client-server-connection");
        connectionThread.setDaemon(true);
        connectionThread.start();
        client.cliHandler.loop();
    }
}
