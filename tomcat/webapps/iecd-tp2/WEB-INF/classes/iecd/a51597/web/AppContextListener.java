package iecd.a51597.web;

import iecd.a51597.client.config.ClientConfiguration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Initializes and shuts down global web resources for the application scope.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger logger = LogManager.getLogger(AppContextListener.class);
    
    /**
     * ServletContext attribute key for retrieving the shared ServerBridgeManager.
     */
    public static final String BRIDGE_MANAGER_KEY = "BridgeManager";

    /**
     * Initializes the web context by loading configuration and binding the bridge manager.
     *
     * @param sce context event metadata
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("Initializing Dots & Boxes Web Application Context");

        // 1. Load client configuration (IP, port, etc.) from config/client_config.xml
        try {
            ClientConfiguration.load();
            logger.info("Client configuration loaded: host={}, port={}", 
                    ClientConfiguration.SERVER_IP, ClientConfiguration.SERVER_PORT);
        } catch (Exception e) {
            logger.error("Error loading ClientConfiguration — using defaults", e);
        }

        // 2. Instantiate global ServerBridgeManager
        ServerBridgeManager manager = new ServerBridgeManager(
                ClientConfiguration.SERVER_IP,
                ClientConfiguration.SERVER_PORT
        );

        // 3. Bind to ServletContext for servlet access
        ServletContext context = sce.getServletContext();
        context.setAttribute(BRIDGE_MANAGER_KEY, manager);
        logger.info("ServerBridgeManager initialized and bound to ServletContext");
    }

    /**
     * Cleans up web resources by shutting down all active bridge connections on context destruction.
     *
     * @param sce context event metadata
     */
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("Destroying Dots & Boxes Web Application Context");
        ServletContext context = sce.getServletContext();
        ServerBridgeManager manager = (ServerBridgeManager) context.getAttribute(BRIDGE_MANAGER_KEY);
        if (manager != null) {
            manager.shutdownAll();
        }
        logger.info("Web Application Context cleanup complete.");
    }
}
