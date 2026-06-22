package iecd.a51597.server.handlers;

import iecd.a51597.server.config.ServerConfiguration;
import iecd.a51597.server.network.Connection;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.builders.server.ServerMessageBuilder;
import iecd.a51597.common.protocol.exceptions.CommException;
import iecd.a51597.common.protocol.exceptions.MalformedMessageException;
import iecd.a51597.common.protocol.exceptions.MessageParseException;
import iecd.a51597.common.protocol.parsers.CommParser;
import iecd.a51597.common.protocol.types.ErrorCodeType;
import iecd.a51597.common.protocol.types.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;

/**
 * Parses incoming frames and dispatches protocol actions to specialized handlers.
 */
public class MessageDispatcher {

    private final CommParser commParser;
    private final ServerMessageBuilder messageBuilder;
    private final AuthHandler authHandler;
    private final ProfileHandler profileHandler;
    private final SearchHandler searchHandler;
    private final GameHandler gameHandler;

    private static final Logger logger = LogManager.getLogger(MessageDispatcher.class);
    private static final Logger auditLogger = LogManager.getLogger("iecd.a51597.server.audit");

    /**
     * Creates a dispatcher with all action handlers.
     */
    public MessageDispatcher(CommParser commParser, ServerMessageBuilder messageBuilder,
                             AuthHandler authHandler, ProfileHandler profileHandler,
                             SearchHandler searchHandler, GameHandler gameHandler) {
        this.commParser = commParser;
        this.messageBuilder = messageBuilder;
        this.authHandler = authHandler;
        this.profileHandler = profileHandler;
        this.searchHandler = searchHandler;
        this.gameHandler = gameHandler;
    }

    /**
     * Handles one inbound frame.
     *
     * @param frameBytes raw framed payload bytes
     * @param connection source connection
     */
    public void handleBytes(byte[] frameBytes, Connection connection) {
        logger.info("Received frame of length {} from connection", frameBytes.length);
        try {
            // Audit Log (Requirement 8)
            String rawMessage = new String(frameBytes, java.nio.charset.StandardCharsets.UTF_8);
            auditLogger.info("[INBOUND] [{}] - {}", connection.getClientSocket().getRemoteSocketAddress(), rawMessage.trim());

            Message message = commParser.parseMessage(new ByteArrayInputStream(frameBytes));
            dispatch(message, connection);
        } catch (MalformedMessageException e) {
            connection.sendMessage(messageBuilder.errorNoId(
                    ErrorCodeType.MALFORMED_REQUEST,
                    "The message does not conform to protocol"
            ));
            logger.warn("Message received was malformed", e);
        } catch (MessageParseException e) {
            connection.sendMessage(messageBuilder.errorNoId(
                    ErrorCodeType.MALFORMED_REQUEST,
                    "The message sent could not be parsed"
            ));
            logger.warn("Failed to parse message from connection");
        } catch (CommException e) {
            connection.sendMessage(messageBuilder.errorNoId(
                ErrorCodeType.INTERNAL_ERROR,
                "An internal error occurred while processing the message"
            ));
            logger.warn("Communication error while handling message from connection");
        }
    }

    private void dispatch(Message message, Connection connection) {
        if (message.messageType() != MessageType.REQUEST) {
            connection.sendMessage(messageBuilder.error(
                    message.messageId(),
                    message.actionType(),
                    ErrorCodeType.UNEXPECTED_MESSAGE_TYPE,
                    "Server only accepts REQUEST messages"
            ));
            logger.warn("Received message with invalid type {} from connection", message.messageType());
            return;
        }

        if (!message.version().equals(ServerConfiguration.PROTOCOL_VERSION)) {
            connection.sendMessage(messageBuilder.error(
                    message.messageId(),
                    message.actionType(),
                    ErrorCodeType.OUTDATED_PROTOCOL,
                    "Unsupported protocol version"
            ));
            logger.warn("Received message with unsupported protocol version {} from connection", message.version());
            return;
        }

        switch (message.actionType()) {
            case REGISTER -> authHandler.register(message, connection);
            case LOGIN -> authHandler.login(message, connection);
            case LOGOUT -> authHandler.logout(message, connection);
            case UPDATE_PROFILE -> profileHandler.updateProfile(message, connection);
            case SEARCH_USERS -> searchHandler.searchUsers(message, connection);
            case GAME_INVITE -> gameHandler.gameInvite(message, connection);
            case GAME_INVITE_RESPONSE -> gameHandler.gameInviteResponse(message, connection);
            case GAME_INVITE_CANCEL -> gameHandler.gameInviteCancel(message, connection);
            case GAME_MOVE -> gameHandler.gameMove(message, connection);
            case SURRENDER -> gameHandler.surrender(message, connection);
            case GAME_OVER -> gameHandler.gameOver(message, connection);
            case UNKNOWN -> connection.sendMessage(messageBuilder.error(
                message.messageId(),
                message.actionType(),
                ErrorCodeType.UNEXPECTED_MESSAGE_ACTION,
                "Action not expected by server"
            ));
            default -> connection.sendMessage(messageBuilder.error(
                message.messageId(),
                message.actionType(),
                ErrorCodeType.UNKNOWN_ACTION,
                "Unknown action type"
            ));
        }
    }
}