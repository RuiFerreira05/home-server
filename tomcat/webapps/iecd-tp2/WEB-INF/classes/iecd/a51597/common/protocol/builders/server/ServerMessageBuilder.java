package iecd.a51597.common.protocol.builders.server;

import iecd.a51597.server.store.entities.User;
import iecd.a51597.common.protocol.types.ActionType;
import iecd.a51597.common.protocol.types.ErrorCodeType;

import java.util.List;
import java.util.UUID;

/**
 * Builds framed protocol messages serialized as XML payload bytes.
 */
public interface ServerMessageBuilder {

    /**
     * Builds an error response for messages where no id could be parsed.
     *
     * @param errorCode protocol error code
     * @param description human-readable detail
     * @return serialized message payload
     */
    byte[] errorNoId(ErrorCodeType errorCode, String description);

    /**
     * Builds a correlated error response.
     *
     * @param messageId request message id
     * @param actionType request action
     * @param errorCode protocol error code
     * @param description human-readable detail
     * @return serialized message payload
     */
    byte[] error(UUID messageId, ActionType actionType, ErrorCodeType errorCode, String description);

    /**
     * Builds a correlated success response with no additional payload.
     *
     * @param messageId request message id
     * @param actionType request action
     * @return serialized message payload
     */
    byte[] ok(UUID messageId, ActionType actionType);

    /**
     * Builds an update profile success response payload containing the updated user.
     *
     * @param messageId request message id
     * @param user updated user
     * @return serialized message payload
     */
    byte[] updateProfileSuccess(UUID messageId, User user);

    /**
     * Builds a successful login response.
     *
     * @param messageId request message id
     * @param sessionToken newly created session token
     * @param user authenticated user details
     * @return serialized message payload
     */
    byte[] loginSuccess(UUID messageId, UUID sessionToken, User user);

    /**
     * Builds a successful user search response.
     *
     * @param messageId request message id
     * @param results matched users
     * @return serialized message payload
     */
    byte[] searchUsersSuccess(UUID messageId, List<User> results);

    /**
     * Builds a game invite acknowledgement for the inviter.
     *
     * @param messageId request message id
     * @param gameId created pending game id
     * @return serialized message payload
     */
    byte[] gameInviteResponse(UUID messageId, UUID gameId);

    /**
     * Builds a push notification sent to the invited player.
     *
     * @param gameId pending game id
     * @param fromUser inviting user
     * @return serialized message payload
     */
    byte[] gameInvitePush(UUID gameId, User fromUser);

    /**
     * Builds a push notification sent to the invited player when an invite is cancelled.
     *
     * @param gameId pending game id
     * @return serialized message payload
     */
    byte[] gameInviteCancelPush(UUID gameId);

    /**
     * Builds a push notification signaling invite acceptance.
     *
     * @param gameId accepted game id
     * @param user accepting user
     * @return serialized message payload
     */
    byte[] gameInviteAcceptedPush(UUID gameId, User user);

    /**
     * Builds a push notification signaling invite rejection.
     *
     * @param gameId declined game id
     * @return serialized message payload
     */
    byte[] gameInviteDeclinedPush(UUID gameId, User user);

    /**
     * Builds a push notification with an opponent's accepted move.
     *
     * @param gameId game id
     * @param rawMove serialized move payload
     * @return serialized message payload
     */
    byte[] gameMovePush(UUID gameId, String rawMove);

    /**
     * Builds a game-over push notification.
     *
     * @param gameId game id
     * @param winner winning user
     * @param user the receiving user's updated profile
     * @return serialized message payload
     */
    byte[] gameOverPush(UUID gameId, User winner, User user);

    /**
     * Builds a game-over push notification with a reason.
     *
     * @param gameId game id
     * @param winner winning user
     * @param reason the reason for game over (e.g. SURRENDER)
     * @param user the receiving user's updated profile
     * @return serialized message payload
     */
    byte[] gameOverPush(UUID gameId, User winner, String reason, User user);

    /**
     * builds a game-over-draw push notification
     *
     * @param gameId game id
     * @param user the receiving user's updated profile
     * @return serialized message payload
     */
    byte[] gameOverDrawPush(UUID gameId, User user);
}