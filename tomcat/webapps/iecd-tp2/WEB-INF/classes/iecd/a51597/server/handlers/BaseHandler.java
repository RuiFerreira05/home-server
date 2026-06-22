package iecd.a51597.server.handlers;

import iecd.a51597.server.network.Connection;
import iecd.a51597.server.persistence.PersistenceManager;
import iecd.a51597.server.session.Session;
import iecd.a51597.server.session.SessionManager;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.builders.server.ServerMessageBuilder;
import iecd.a51597.common.protocol.types.ErrorCodeType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * Base class for request handlers with shared response/session helpers.
 */
public abstract class BaseHandler {

    /**
     * XML server message response builder helper.
     */
    protected final ServerMessageBuilder messageBuilder;

    /**
     * Server session manager cache layer.
     */
    protected final SessionManager sessionManager;

    /**
     * Persistence repository manager for disk reads and writes.
     */
    protected final PersistenceManager persistenceManager;

    /**
     * Logger inherited by concrete handlers, tagged with subclass type.
     */
    protected final Logger logger = LogManager.getLogger(getClass());

    /**
     * Creates a base handler.
     *
     * @param messageBuilder response builder
     * @param sessionManager session manager
     */
    protected BaseHandler(ServerMessageBuilder messageBuilder, SessionManager sessionManager, PersistenceManager persistenceManager) {
        this.messageBuilder = messageBuilder;
        this.sessionManager = sessionManager;
        this.persistenceManager = persistenceManager;
    }

    /**
     * Sends a correlated protocol error.
     * @param message the message being responded to
     * @param connection the client connection
     * @param errorCode the error code
     * @param description human-readable description of the error
     */
    protected void sendError(Message message, Connection connection, ErrorCodeType errorCode, String description) {
        connection.sendMessage(messageBuilder.error(
                message.messageId(),
                message.actionType(),
                errorCode,
                description
        ));
    }

    /**
     * Validates that a request carries a live session.
     *
     * @param message the message to validate
     * @param connection the client connection
     * @return valid session when available; empty after sending an error otherwise
     */
    protected Optional<Session> requireSession(Message message, Connection connection) {
        if (message.sessionToken() == null) {
            sendError(message, connection, ErrorCodeType.NOT_AUTHENTICATED, "No session token provided");
            return Optional.empty();
        }
        Optional<Session> session = sessionManager.validate(message.sessionToken());
        if (session.isEmpty()) {
            sendError(message, connection, ErrorCodeType.SESSION_EXPIRED, "Session token is invalid or expired");
        }
        return session;
    }
}
