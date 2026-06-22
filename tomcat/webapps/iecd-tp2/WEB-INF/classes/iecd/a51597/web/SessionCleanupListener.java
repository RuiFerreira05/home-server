package iecd.a51597.web;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Listens for HTTP session lifecycle events to prevent TCP connection leaks on session timeout/expiry.
 */
@WebListener
public class SessionCleanupListener implements HttpSessionListener {

    private static final Logger logger = LogManager.getLogger(SessionCleanupListener.class);

    /**
     * Receives notification that an HTTP session has been created.
     *
     * @param se the session event containing the created session
     */
    @Override
    public void sessionCreated(HttpSessionEvent se) {
        logger.debug("HTTP Session created: ID={}", se.getSession().getId());
    }

    /**
     * Receives notification that an HTTP session has been destroyed/invalidated.
     * Cleans up the associated TCP server bridge connection.
     *
     * @param se the session event containing the destroyed session
     */
    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        String sessionId = se.getSession().getId();
        logger.info("HTTP Session destroyed/timed out: ID={}", sessionId);

        ServletContext context = se.getSession().getServletContext();
        ServerBridgeManager manager = (ServerBridgeManager) context.getAttribute(AppContextListener.BRIDGE_MANAGER_KEY);
        if (manager != null) {
            manager.destroyBridgeBySessionId(sessionId);
        }
    }
}
