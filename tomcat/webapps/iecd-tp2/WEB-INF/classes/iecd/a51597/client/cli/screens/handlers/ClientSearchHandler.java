package iecd.a51597.client.cli.screens.handlers;

import iecd.a51597.client.config.ClientConfiguration;
import iecd.a51597.client.network.ServerConnection;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.MessageFactory;
import iecd.a51597.common.store.UserDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Handler for performing user search requests on the server.
 */
public class ClientSearchHandler {

    private final ServerConnection connection;

    private static final Logger logger = LogManager.getLogger(ClientSearchHandler.class);

    /**
     * Creates a new search handler with the specified server connection.
     *
     * @param connection the server connection bridge
     */
    public ClientSearchHandler(ServerConnection connection) {
        this.connection = connection;
    }

    /**
     * Sealed interface representing outcomes of user search queries.
     */
    public sealed interface SearchPlayerResult {
        record SUCCESS(List<UserDTO> users) implements SearchPlayerResult {
        }

        ;

        record ERROR() implements SearchPlayerResult {
        }

        ;
    }

    /**
     * Queries the server for users matching the given search query string.
     *
     * @param query the partial or full username to search for
     * @return the search result list or error indicator
     */
    public SearchPlayerResult searchPlayers(String query) {
        Message request = MessageFactory.buildSearchRequest(ClientConfiguration.PROTOCOL_VERSION, query);

        Message response;
        try {
            response = connection.sendRequest(request).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error(e);
            return new SearchPlayerResult.ERROR();
        }

        if (response.body() instanceof MessageBody.SearchUsersResponse(
                String status, List<UserDTO> users,
                MessageBody.ErrorDetail error
        )) {
            if (status.equals("OK")) {
                return new SearchPlayerResult.SUCCESS(users);
            } else  {
                logger.error("SearchUsers received but status ERROR");
                return new SearchPlayerResult.ERROR();
            }
        } else {
            return new SearchPlayerResult.ERROR();
        }
    }
}
