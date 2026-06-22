package iecd.a51597.client.cli.screens.handlers;

import iecd.a51597.client.config.ClientConfiguration;
import iecd.a51597.client.network.ServerConnection;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.MessageFactory;
import iecd.a51597.common.store.UserDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Handler for managing game invitations from the client side, including sending,
 * cancelling, and answering invites.
 */
public class ClientInviteHandler {

    private final ServerConnection serverConnection;

    private static final Logger logger = LogManager.getLogger(ClientInviteHandler.class);

    /**
     * Creates an invite handler with the specified server connection.
     *
     * @param serverConnection the active TCP connection
     */
    public ClientInviteHandler(ServerConnection serverConnection) {
        this.serverConnection = serverConnection;
    }

    /**
     * Sealed interface representing results of answering a game invitation.
     */
    public sealed interface AnswerInviteResponse {
        record Success() implements AnswerInviteResponse {}
        record Error(String message) implements AnswerInviteResponse {}
    }

    /**
     * Sends a response to an invite, accepting or declining it.
     *
     * @param messageBody the received invite push detail
     * @param answer true to accept, false to decline
     * @return the result response
     */
    public AnswerInviteResponse answerInvite(MessageBody.GameInvitePush messageBody, boolean answer) {
        Message request = MessageFactory.buildAcceptInviteRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                serverConnection.getSessionManager().getSessionUUID(),
                messageBody.gameId(),
                answer
        );

        Message response;
        try {
            response = serverConnection.sendRequest(request).get();
        } catch (Exception e) {
            logger.warn("Accept invite request interrupted: {}", e.getMessage());
            return new AnswerInviteResponse.Error("Failed to accept invite: " + e.getMessage());
        }

        if (response.body() instanceof MessageBody.GameInviteResponseResult(
                String status, MessageBody.ErrorDetail error
        )) {
            if (status.equals("OK")) {
                serverConnection.getClient().getPendingInvites().remove(messageBody);
                return new AnswerInviteResponse.Success();
            } else {
                return new AnswerInviteResponse.Error("Failed to accept invite: " + error.message());
            }
        } else {
            return new AnswerInviteResponse.Error("Invalid response from server");
        }

    }

    public sealed interface InviteResponseResult {
        record Accepted(java.util.UUID gameId) implements InviteResponseResult {}
        record Declined() implements InviteResponseResult {}
        record Error(String message) implements InviteResponseResult {}
    }

    public sealed interface InviteResult {
        record Success(UUID gameId) implements InviteResult {}
        record Error(String message) implements InviteResult {}
    }

    public sealed interface CancelInviteResponse {
        record Success() implements CancelInviteResponse {}
        record Error(String message) implements CancelInviteResponse {}
    }

    /**
     * Cancels a pending outgoing game invitation.
     *
     * @param gameId the unique invitation game UUID
     * @return the result of the cancellation request
     */
    public CancelInviteResponse cancelInvite(UUID gameId) {
        Message request = MessageFactory.buildCancelInviteRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                serverConnection.getSessionManager().getSessionUUID(),
                gameId
        );

        Message response;
        try {
            response = serverConnection.sendRequest(request).get();
        } catch (Exception e) {
            logger.warn("Cancel invite request interrupted: {}", e.getMessage());
            return new CancelInviteResponse.Error("Failed to cancel invite: " + e.getMessage());
        }

        if (response.body() instanceof MessageBody.GameInviteCancelResponse(
                String status, MessageBody.ErrorDetail error
        )) {
            if (status.equals("OK")) {
                return new CancelInviteResponse.Success();
            } else {
                return new CancelInviteResponse.Error("Failed to cancel invite: " + error.message());
            }
        } else {
            return new CancelInviteResponse.Error("Invalid response from server");
        }
    }

    /**
     * Sends an invitation to play a match to another user.
     *
     * @param target the user profile DTO to invite
     * @return the invite result (success containing gameId, or error description)
     */
    public InviteResult sendInvite(UserDTO target) {
        Message request = MessageFactory.buildSendInviteRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                serverConnection.getSessionManager().getSessionUUID(),
                target.userId()
        );

        Message response;
        try {
            response = serverConnection.sendRequest(request).get();
        } catch (Exception e) {
            logger.warn("Invite request interrupted: {}", e.getMessage());
            return new InviteResult.Error("Failed to send invite: " + e.getMessage());
        }

        if (response.body() instanceof MessageBody.GameInviteResponse(
                String status, java.util.UUID gameId, MessageBody.ErrorDetail error
        )) {
            if (status.equals("OK")) {
                return new InviteResult.Success(gameId);
            } else {
                switch (error.code()) {
                    case USER_NOT_FOUND -> {
                        return new InviteResult.Error("Target user not found");
                    }
                    case ALREADY_IN_GAME -> {
                        return new InviteResult.Error("Target user is already in a game");
                    }
                    case USER_NOT_ONLINE -> {
                        return new InviteResult.Error("Target user is not online");
                    }
                    default -> {
                        return new InviteResult.Error("Failed to send invite: " + error.message());
                    }
                }
            }
        } else {
            return new InviteResult.Error("Invalid response from server");
        }
    }
}
