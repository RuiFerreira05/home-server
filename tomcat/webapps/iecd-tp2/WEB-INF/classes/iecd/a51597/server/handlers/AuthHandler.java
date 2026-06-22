package iecd.a51597.server.handlers;

import iecd.a51597.server.network.Connection;
import iecd.a51597.server.persistence.PersistenceManager;
import iecd.a51597.server.session.Session;
import iecd.a51597.server.session.SessionManager;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.builders.server.ServerMessageBuilder;
import iecd.a51597.common.protocol.types.ErrorCodeType;
import iecd.a51597.server.store.UserStore;
import iecd.a51597.server.store.exceptions.UsernameAlreadyTakenException;

/**
 * Handles registration/login/logout protocol actions.
 */
public class AuthHandler extends BaseHandler {

    private final UserStore userStore;

    /**
     * Creates an auth handler.
     */
    public AuthHandler(ServerMessageBuilder messageBuilder, SessionManager sessionManager, UserStore userStore, PersistenceManager persistenceManager) {
        super(messageBuilder, sessionManager, persistenceManager);
        this.userStore = userStore;
    }

    /**
     * Handles user registration requests.
     * @param message the registration request message
     * @param connection the client connection
     */
    public void register(Message message, Connection connection) {
        logger.info("Received registration request from connection");
        MessageBody.Register body = (MessageBody.Register) message.body();

        try {
            userStore.register(body.username(), body.password());
            connection.sendMessage(messageBuilder.ok(message.messageId(), message.actionType()));
            logger.info("Successfully registered new user with username '{}'", body.username());
        } catch (UsernameAlreadyTakenException e) {
            sendError(message, connection, ErrorCodeType.USERNAME_TAKEN, "Username is already taken");
            logger.warn("Failed to register user with username '{}'", body.username(), e);
        }
    }

    /**
     * Handles user login requests.
     * @param message the login request message
     * @param connection the client connection
     */
    public void login(Message message, Connection connection) {
        logger.info("Received login request from connection");
        MessageBody.LoginRequest body = (MessageBody.LoginRequest) message.body();

        userStore.findByCredentials(body.username(), body.password()).ifPresentOrElse(
                user -> {
                    Session session = sessionManager.createSession(user, connection);
                    connection.sendMessage(messageBuilder.loginSuccess(
                            message.messageId(),
                            session.getToken(),
                            user
                    ));
                },
                () -> sendError(message, connection, ErrorCodeType.AUTH_FAILED, "Invalid username or password")
        );
    }

    /**
     * Handles logout requests by invalidating the current session.
     * @param message the logout request message
     * @param connection the client connection
     */
    public void logout(Message message, Connection connection) {
        logger.info("Received logout request from connection");
        if (requireSession(message, connection).isEmpty()) {
            logger.warn("Logout request missing valid session token");
            return;
        };

        sessionManager.invalidate(message.sessionToken());
        connection.sendMessage(messageBuilder.ok(message.messageId(), message.actionType()));
    }
}
