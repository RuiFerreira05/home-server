package iecd.a51597.server;

import iecd.a51597.common.game.dotsandboxes.DotsAndBoxesGameFactory;
import iecd.a51597.server.cli.CLIHandler;
import iecd.a51597.server.config.ServerConfiguration;
import iecd.a51597.common.game.GameFactory;
import iecd.a51597.server.game.GameManager;
import iecd.a51597.server.handlers.*;
import iecd.a51597.server.network.Connection;
import iecd.a51597.server.network.ListenerThread;
import iecd.a51597.server.persistence.PersistenceManager;
import iecd.a51597.common.protocol.builders.server.ServerMessageBuilder;
import iecd.a51597.common.protocol.builders.server.XMLServerMessageBuilder;
import iecd.a51597.common.protocol.parsers.CommParser;
import iecd.a51597.common.protocol.parsers.XMLParser;
import iecd.a51597.server.session.SessionManager;
import iecd.a51597.server.store.Leaderboard;
import iecd.a51597.server.store.UserStore;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Main server singleton composing networking, protocol handling, sessions, and persistence.
 */
public class Server {

    private static volatile Server instance;

    private int port;
    private ListenerThread listener;
    private final SessionManager sessionManager = new SessionManager();
    private final CommParser commParser;
    private final ServerMessageBuilder messageBuilder;
    private final MessageDispatcher messageDispatcher;
    private final GameManager gameManager = new GameManager();
    private final UserStore userStore;
    private final Leaderboard leaderboard;
    private final PersistenceManager persistenceManager;

    private final CLIHandler cliHandler;
    private static final Logger logger = LogManager.getLogger(Server.class);

    private final List<Connection> connections = new ArrayList<>();
    private boolean shutdownCompleted = false;

    private Server() {
        logger.info("Initializing Server...");
        
        // Ensure data, data/photos, and logs directories exist on startup
        new java.io.File("data").mkdirs();
        new java.io.File("data/photos").mkdirs();
        new java.io.File("logs").mkdirs();

        ServerConfiguration.load();

        this.port = ServerConfiguration.DEFAULT_PORT;
        this.cliHandler = new CLIHandler(this);
        this.messageBuilder = new XMLServerMessageBuilder(sessionManager);
        this.commParser = new XMLParser();
        this.userStore = new UserStore();
        this.leaderboard = new Leaderboard(userStore);
        this.persistenceManager = new PersistenceManager(userStore);

        persistenceManager.load();

        AuthHandler authHandler = new AuthHandler(messageBuilder, sessionManager, userStore, persistenceManager);
        ProfileHandler profileHandler = new ProfileHandler(messageBuilder, sessionManager, userStore, persistenceManager);
        SearchHandler searchHandler = new SearchHandler(messageBuilder, sessionManager, userStore, persistenceManager);
        GameHandler gameHandler = new GameHandler(messageBuilder, sessionManager, userStore, gameManager, persistenceManager);

        this.messageDispatcher = new MessageDispatcher(commParser, messageBuilder, authHandler, profileHandler, searchHandler, gameHandler);


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!shutdownCompleted) {
                logger.info("Shutdown signal received without previous graceful shutdown, initiating shutdown hook...");
                shutdown();
            }
        }));

        registerGameFactory(new DotsAndBoxesGameFactory());
    }

    /**
     * Registers a pluggable game factory used for invite/move handling.
     *
     * @param factory game factory implementation
     */
    public void registerGameFactory(GameFactory factory) {
        gameManager.registerFactory(factory);
    }

    /**
     * @return central frame/message handler
     */
    public MessageDispatcher getMessageHandler() {
        return messageDispatcher;
    }

    /**
     * @return listener thread or {@code null} when not started
     */
    public ListenerThread getListener() {
        return listener;
    }

    /**
     * @return message builder
     */
    public ServerMessageBuilder getMessageBuilder() {
        return messageBuilder;
    }

    /**
     * @return communication parser
     */
    public CommParser getCommParser() {
        return commParser;
    }

    /**
     * @return session manager
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * @return user store
     */
    public UserStore getUserStore() {
        return userStore;
    }

    /**
     * @return game manager
     */
    public GameManager getGameManager() {
        return gameManager;
    }

    /**
     * Returns the singleton server instance.
     */
    public static Server getInstance() {
        if (instance == null) {
            synchronized (Server.class) {
                if (instance == null) {
                    instance = new Server();
                }
            }
        }
        return instance;
    }

    /**
     * @return snapshots of currently tracked connections
     */
    public List<Connection> getConnections() {
        synchronized (connections) {
            return List.copyOf(connections);
        }
    }

    /**
     * Adds a connection to server tracking.
     * @param connection the connection to add
     */
    public void addConnection(Connection connection) {
        synchronized (connections) {
            connections.add(connection);
        }
    }

    /**
     * Removes a connection from server tracking.
     * @param connection the connection to remove
     */
    public void removeConnection(Connection connection) {
        synchronized (connections) {
            connections.remove(connection);
        }
    }

    private void handleCLIParams(String[] args) {
        try {
            if (args.length > 0) {
                this.port = Integer.parseInt(args[0]);
                logger.info("Server port assigned to: {}", this.port);
            }
        } catch (NumberFormatException e) {
            logger.error("Invalid port number", e);
        }
    }

    /**
     * Starts listener and CLI loops.
     */
    void loop() {
        Thread cliThread = new Thread(cliHandler::loop);
        this.startListener();
        cliThread.start();
    }

    /**
     * Starts listener on an explicit port.
     * @param port the port to listen on
     */
    public void startListener(int port) {
        if (!this.isListening()) {
            listener = new ListenerThread(port, this);
            listener.start();
            logger.info("Server listening on port: {}", port);
        }
    }

    /**
     * Starts listener on configured startup port.
     */
    public void startListener() {
        logger.info("Starting Listener thread with default port: {}", this.port);
        startListener(this.port);
    }

    /**
     * Stops accepting new connections.
     */
    public void stopListener() {
        if (this.isListening()) {
            this.listener.stopListener();
            logger.info("Server stopping listener");
        }
    }

    /**
     * @return {@code true} when listener thread is active
     */
    public boolean isListening() {
        return this.listener != null && this.listener.isRunning();
    }

    /**
     * Gracefully shuts down listener, persists data, and closes active connections.
     */
    public void shutdown() {
        stopListener();
        persistenceManager.save();
        persistenceManager.shutdownThread();

        // The reason we take a snapshot of the connections list here is to avoid a ConcurrentModificationException when
        // Connection calls server.removeConnection(). It's a little ugly but it works
        List<Connection> snapshot;
        synchronized (connections) {
            snapshot = List.copyOf(connections);
        }
        snapshot.forEach(Connection::closeConnection);
        logger.info("Server shutdown complete");
        shutdownCompleted = true;
    }

    /**
     * @return default startup port
     */
    public int getStartupPort() {
        return this.port;
    }

    /**
     * @return leaderboard projection
     */
    public Leaderboard getLeaderboard() {
        return leaderboard;
    }

    /**
     * Application entry point.
     * @param args CLI arguments (optional port)
     */
    public static void main(String[] args) {
        Server server = Server.getInstance();
        server.handleCLIParams(args);
        server.loop();
    }
}