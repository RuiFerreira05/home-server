package iecd.a51597.web;

import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of ServerBridge instances associated with HTTP sessions.
 */
public class ServerBridgeManager {

    private static final Logger logger = LogManager.getLogger(ServerBridgeManager.class);
    private static final String SESSION_BRIDGE_KEY = "server_bridge";

    private final String serverHost;
    private final int serverPort;
    private final Map<String, ServerBridge> activeBridges;

    /**
     * Constructs a new ServerBridgeManager.
     *
     * @param serverHost the host name or IP address of the game server
     * @param serverPort the port number of the game server
     */
    public ServerBridgeManager(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.activeBridges = new ConcurrentHashMap<>();
    }

    /**
     * Gets an existing ServerBridge for the session, or creates a new connected one.
     *
     * @param session the HTTP session to associate the bridge with
     * @return the active ServerBridge for the session
     * @throws IOException if a new connection cannot be established
     */
    public ServerBridge getOrCreateBridge(HttpSession session) throws IOException {
        ServerBridge bridge = (ServerBridge) session.getAttribute(SESSION_BRIDGE_KEY);
        if (bridge == null || !bridge.isConnected()) {
            synchronized (session) {
                // Double check inside lock
                bridge = (ServerBridge) session.getAttribute(SESSION_BRIDGE_KEY);
                if (bridge == null || !bridge.isConnected()) {
                    if (bridge != null) {
                        bridge.shutdown();
                    }
                    
                    bridge = new ServerBridge(serverHost, serverPort);
                    bridge.connect();
                    session.setAttribute(SESSION_BRIDGE_KEY, bridge);
                    activeBridges.put(session.getId(), bridge);
                    logger.info("Bound new ServerBridge to HTTP Session ID: {}", session.getId());
                }
            }
        }
        return bridge;
    }

    /**
     * Gets a bridge if it already exists, or returns null.
     *
     * @param session the HTTP session to retrieve the bridge from
     * @return the associated ServerBridge, or null if none exists
     */
    public ServerBridge getBridge(HttpSession session) {
        return (ServerBridge) session.getAttribute(SESSION_BRIDGE_KEY);
    }

    /**
     * Disconnects and removes the bridge for the given session.
     *
     * @param session the HTTP session to destroy the bridge for
     */
    public void destroyBridge(HttpSession session) {
        synchronized (session) {
            ServerBridge bridge = (ServerBridge) session.getAttribute(SESSION_BRIDGE_KEY);
            if (bridge != null) {
                bridge.shutdown();
                session.removeAttribute(SESSION_BRIDGE_KEY);
                activeBridges.remove(session.getId());
                logger.info("Destroyed ServerBridge for Session ID: {}", session.getId());
            }
        }
    }

    /**
     * Cleans up a bridge by raw session ID (useful in session listeners).
     *
     * @param sessionId the raw HTTP session ID to clean up
     */
    public void destroyBridgeBySessionId(String sessionId) {
        ServerBridge bridge = activeBridges.remove(sessionId);
        if (bridge != null) {
            bridge.shutdown();
            logger.info("Cleaned up orphaned ServerBridge for Session ID: {}", sessionId);
        }
    }

    /**
     * Shuts down all active bridges. Called on application undeploy/shutdown.
     */
    public void shutdownAll() {
        logger.info("Shutting down all active ServerBridges (count={})", activeBridges.size());
        for (String sessionId : activeBridges.keySet()) {
            destroyBridgeBySessionId(sessionId);
        }
    }
}
