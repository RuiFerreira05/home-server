package iecd.a51597.common.protocol;

import iecd.a51597.common.protocol.types.ActionType;
import iecd.a51597.common.protocol.types.MessageType;
import iecd.a51597.common.store.UserDTO;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Factory class for creating various protocol Message objects.
 */
public class MessageFactory {

    private MessageFactory() {
    }

    /**
     * Builds a login request message.
     * @param protocolVersion protocol version
     * @param uuid message ID (random if null)
     * @param username username
     * @param password password
     * @return login request message
     */
    public static Message buildLoginRequest(String protocolVersion, UUID uuid, String username, String password) {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }

        return new Message(
                uuid,
                MessageType.REQUEST,
                protocolVersion,
                ActionType.LOGIN,
                null,
                new MessageBody.LoginRequest(username, password)
        );
    }

    /**
     * Builds a registration request message.
     * @param protocolVersion protocol version
     * @param uuid message ID (random if null)
     * @param username username
     * @param password password
     * @return registration request message
     */
    public static Message buildRegisterRequest(String protocolVersion, UUID uuid, String username, String password) {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }

        return new Message(
                uuid,
                MessageType.REQUEST,
                protocolVersion,
                ActionType.REGISTER,
                null,
                new MessageBody.Register(username, password)
        );
    }

    /**
     * Builds a logout request message.
     * @param protocolVersion protocol version
     * @param uuid message ID (random if null)
     * @param sessionToken session token
     * @return logout request message
     */
    public static Message buildLogoutRequest(String protocolVersion, UUID uuid, UUID sessionToken) {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }

        return new Message(
                uuid,
                MessageType.REQUEST,
                protocolVersion,
                ActionType.LOGOUT,
                sessionToken,
                new MessageBody.Logout()
        );
    }

    /**
     * Builds a profile update request message.
     * @param protocolVersion protocol version
     * @param sessionToken session token
     * @param username new username
     * @param password new password
     * @param photo new photo bytes
     * @param nationality new nationality
     * @param dob new date of birth
     * @return update profile request message
     */
    public static Message buildUpdateProfileRequest(String protocolVersion, UUID sessionToken, String username, String password, byte[] photo, String nationality, LocalDate dob) {
        return buildUpdateProfileRequest(protocolVersion, sessionToken, username, password, photo, nationality, dob, null);
    }

    /**
     * Builds a profile update request message (with favorite color).
     */
    public static Message buildUpdateProfileRequest(String protocolVersion, UUID sessionToken, String username, String password, byte[] photo, String nationality, LocalDate dob, String favoriteColor) {
        return new Message(
                UUID.randomUUID(),
                MessageType.REQUEST,
                protocolVersion,
                ActionType.UPDATE_PROFILE,
                sessionToken,
                new MessageBody.UpdateProfile(username, password, photo, nationality, dob, favoriteColor));
    }

    /**
     * Builds a user search request message.
     * @param protocolVersion protocol version
     * @param query search query
     * @return search request message
     */
    public static Message buildSearchRequest(String protocolVersion, String query) {
        return new Message(
                UUID.randomUUID(),
                MessageType.REQUEST,
                protocolVersion,
                ActionType.SEARCH_USERS,
                null,
                new MessageBody.SearchUsersRequest(
                        query
                )
        );
    }

    /**
     * Builds a game move request message.
     * @param protocolVersion protocol version
     * @param sessionUUID session token
     * @param gameId game ID
     * @param rawMove serialized move string
     * @return game move request message
     */
    public static Message createMoveRequest(String protocolVersion, UUID sessionUUID, UUID gameId, String rawMove) {
        return new Message(
                UUID.randomUUID(),
                MessageType.REQUEST,
                protocolVersion,
                ActionType.GAME_MOVE,
                sessionUUID,
                new MessageBody.GameMove(gameId, rawMove)
        );
    }

    /**
     * Builds a surrender request message.
     * @param protocolVersion protocol version
     * @param sessionUUID session token
     * @param gameId game ID
     * @return surrender request message
     */
    public static Message buildSurrenderRequest(String protocolVersion, UUID sessionUUID, UUID gameId) {
        return new Message(
                UUID.randomUUID(),
                MessageType.REQUEST,
                protocolVersion,
                ActionType.SURRENDER,
                sessionUUID,
                new MessageBody.Surrender(gameId)
        );
    }

    /**
     * Builds a game invite request message.
     * @param protocolVersion protocol version
     * @param sessionToken session token
     * @param targetId ID of the user to invite
     * @return game invite request message
     */
    public static Message buildSendInviteRequest(String protocolVersion, UUID sessionToken, UUID targetId) {
        return new Message(
                UUID.randomUUID(),
                MessageType.REQUEST,
                protocolVersion,
                ActionType.GAME_INVITE,
                sessionToken,
                new MessageBody.GameInviteRequest(targetId)
        );
    }

    /**
     * Builds a cancel invite request message.
     * @param protocolVersion protocol version
     * @param sessionToken session token
     * @param gameId game ID of the invite to cancel
     * @return cancel invite request message
     */
    public static Message buildCancelInviteRequest(String protocolVersion, UUID sessionToken, UUID gameId) {
        return new Message(
                UUID.randomUUID(),
                MessageType.REQUEST,
                protocolVersion,
                ActionType.GAME_INVITE_CANCEL,
                sessionToken,
                new MessageBody.GameInviteCancelRequest(gameId)
        );
    }

    /**
     * Builds an invite response request message.
     * @param protocolVersion protocol version
     * @param sessionUUID session token
     * @param gameId game ID
     * @param response true to accept, false to reject
     * @return invite response request message
     */
    public static Message buildAcceptInviteRequest(String protocolVersion, UUID sessionUUID, UUID gameId, boolean response) {
        return new Message(
                UUID.randomUUID(),
                MessageType.REQUEST,
                protocolVersion,
                ActionType.GAME_INVITE_RESPONSE,
                sessionUUID,
                new MessageBody.GameInviteResponseRequest(gameId, response)
        );
    }
}
