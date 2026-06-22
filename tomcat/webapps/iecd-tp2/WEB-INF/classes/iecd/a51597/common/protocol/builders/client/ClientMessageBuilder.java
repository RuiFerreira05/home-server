package iecd.a51597.common.protocol.builders.client;

import iecd.a51597.common.protocol.Message;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Builds framed REQUEST messages sent from the client to the server.
 *
 * <p>Every method generates a fresh random message id, sets {@code type="REQUEST"},
 * and embeds the current protocol version. The caller owns the resulting byte array
 * and is responsible for framing it on the wire (4-byte int length prefix).</p>
 *
 * <p>Session token rules follow the protocol reference:
 * <ul>
 *   <li>REGISTER and LOGIN must not carry a session token.</li>
 *   <li>All other actions require one.</li>
 *   <li>SEARCH_USERS is intentionally session-free (the server does not enforce one).</li>
 * </ul>
 * </p>
 */
public interface ClientMessageBuilder {

    // ── Auth ─────────────────────────────────────────────────────────────────

    /**
     * Builds a REGISTER request.
     *
     * @param username desired username
     * @param password plaintext password (client is responsible for any pre-hashing)
     * @return serialized REQUEST payload
     */
    byte[] register(String username, String password);

    /**
     * Builds a LOGIN request.
     *
     * @param username account username
     * @param password plaintext password
     * @return serialized REQUEST payload
     */
    byte[] login(String username, String password);

    /**
     * Builds a LOGOUT request.
     *
     * @param sessionToken active session token
     * @return serialized REQUEST payload
     */
    byte[] logout(UUID sessionToken);

    // ── Profile ───────────────────────────────────────────────────────────────

    /**
     * Builds an UPDATE_PROFILE request.
     *
     * <p>All profile fields are optional — pass {@code null} to leave a field
     * unchanged. At least one non-null field should be provided.</p>
     *
     * @param sessionToken active session token
     * @param username     new username, or {@code null}
     * @param password     new password, or {@code null}
     * @param photo        new photo bytes, or {@code null}
     * @return serialized REQUEST payload
     */
    byte[] updateProfile(UUID sessionToken, String username, String password, byte[] photo, String Nationality, LocalDate dob, String favoriteColor);

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Builds a SEARCH_USERS request. No session token is required.
     *
     * @param query partial username to search for
     * @return serialized REQUEST payload
     */
    byte[] searchUsers(String query);

    // ── Game ──────────────────────────────────────────────────────────────────

    /**
     * Builds a GAME_INVITE request.
     *
     * @param sessionToken  active session token
     * @param targetUserId  id of the user being invited
     * @return serialized REQUEST payload
     */
    byte[] gameInvite(UUID sessionToken, UUID targetUserId);

    /**
     * Builds a GAME_INVITE_RESPONSE request.
     *
     * @param sessionToken active session token
     * @param gameId       pending game id received in the invitation push
     * @param accept       {@code true} to accept, {@code false} to decline
     * @return serialized REQUEST payload
     */
    byte[] gameInviteResponse(UUID sessionToken, UUID gameId, boolean accept);

    /**
     * Builds a GAME_INVITE_CANCEL request.
     *
     * @param sessionToken active session token
     * @param gameId       pending game id to cancel
     * @return serialized REQUEST payload
     */
    byte[] gameInviteCancel(UUID sessionToken, UUID gameId);

    /**
     * Builds a GAME_MOVE request.
     *
     * <p>The move payload is wrapped in a CDATA section so that game-specific
     * content (e.g. XML fragments, special characters) does not interfere with
     * the outer protocol envelope.</p>
     *
     * @param sessionToken active session token
     * @param gameId       active game id
     * @param rawMove      serialized move string produced by the game codec
     * @return serialized REQUEST payload
     */
    byte[] gameMove(UUID sessionToken, UUID gameId, String rawMove);

    /**
     * Builds a SURRENDER request.
     *
     * @param sessionToken active session token
     * @param gameId       active game id
     * @return serialized REQUEST payload
     */
    byte[] surrender(UUID sessionToken, UUID gameId);

    /**
     * Serializes a generic Message envelope into bytes for sending on the TCP wire.
     *
     * @param message the message envelope to serialize
     * @return the serialized message bytes
     */
    byte[] getMessageInBytes(Message message);
}