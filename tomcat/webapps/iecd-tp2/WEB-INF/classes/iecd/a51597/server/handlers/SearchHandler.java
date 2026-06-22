package iecd.a51597.server.handlers;

import iecd.a51597.server.network.Connection;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.builders.server.ServerMessageBuilder;
import iecd.a51597.server.persistence.PersistenceManager;
import iecd.a51597.server.session.SessionManager;
import iecd.a51597.server.store.UserStore;

/**
 * Handles username search requests.
 */
public class SearchHandler extends BaseHandler {

    private final ServerMessageBuilder messageBuilder;
    private final UserStore userStore;

    /**
     * Creates a search handler.
     */
    public SearchHandler(ServerMessageBuilder messageBuilder, SessionManager sessionManager, UserStore userStore, PersistenceManager persistenceManager) {
        super(messageBuilder, sessionManager, persistenceManager);
        this.messageBuilder = messageBuilder;
        this.userStore = userStore;
    }

    /**
     * Executes a username search and returns matching users.
     * @param message the search request message
     * @param connection the client connection
     */
    public void searchUsers(Message message, Connection connection) {
        logger.info("Received search users request from connection");

        MessageBody.SearchUsersRequest body = (MessageBody.SearchUsersRequest) message.body();
        connection.sendMessage(messageBuilder.searchUsersSuccess(
                message.messageId(),
                userStore.searchByUsername(body.query())
        ));
    }
}
