package iecd.a51597.common.protocol;

import iecd.a51597.common.protocol.types.ErrorCodeType;
import iecd.a51597.common.store.UserDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Marker interface for all supported protocol payload bodies.
 */
public sealed interface MessageBody {

    /**
     * User match summary included in user stats collections.
     */
    record UserMatch(boolean result, double playtime, UUID opponentId, String opponentUsername) {}

    /**
     * Error payload included in failed responses.
     */
    record ErrorDetail(ErrorCodeType code, String message) {}

    /**
     * Generic response payload used when the action is unknown.
     */
    record GenericResponse(String status, ErrorDetail error) implements MessageBody {}

    /**
     * Registration payload.
     */
    record Register(String username, String password) implements MessageBody {}

    /**
     * Registration response payload.
     */
    record RegisterResponse(String status, ErrorDetail error) implements MessageBody {}

    /**
     * Login request payload.
     */
    record LoginRequest(String username, String password) implements MessageBody {}

    /**
     * Login response payload.
     */
    record LoginResponse(String status, UUID session, UserDTO user, ErrorDetail error) implements MessageBody {}

    /**
     * Logout payload.
     */
    record Logout() implements MessageBody {}

    /**
     * Logout response payload.
     */
    record LogoutResponse(String status, ErrorDetail error) implements MessageBody {}

    /**
     * Profile update payload.
     */
    record UpdateProfile(String username, String password, byte[] photo, String nationality, LocalDate dob, String favoriteColor) implements MessageBody {}

    /**
     * Profile update response payload.
     */
    record UpdateProfileResponse(String status, UserDTO user, ErrorDetail error) implements MessageBody {}

    /**
     * User search request.
     */
    record SearchUsersRequest(String query) implements MessageBody {}

    /**
     * User search response.
     */
    record SearchUsersResponse(String status, List<UserDTO> results, ErrorDetail error) implements MessageBody {}

    /**
     * Game invitation request (Client -> Server).
     */
    record GameInviteRequest(UUID targetUserId) implements MessageBody {}

    /**
     * Game invitation response (Server -> Inviter).
     */
    record GameInviteResponse(String status, UUID gameId, ErrorDetail error) implements MessageBody {}

    /**
     * Game invitation push (Server -> Target Client).
     */
    record GameInvitePush(UUID fromUserId, String fromUsername, UUID gameId) implements MessageBody {}

    /**
     * Invitation response request (Client -> Server).
     */
    record GameInviteResponseRequest(UUID gameId, boolean accept) implements MessageBody {}

    /**
     * Invitation response acknowledgment (Server -> Invitee).
     */
    record GameInviteResponseResult(String status, ErrorDetail error) implements MessageBody {}

    /**
     * Cancel an active game invite request (Client -> Server)
     */
    record GameInviteCancelRequest(UUID gameId) implements MessageBody {}
    
    /**
     * Acknowledgment of invite cancellation (Server -> Inviter)
     */
    record GameInviteCancelResponse(String status, ErrorDetail error) implements MessageBody {}
    
    /**
     * Push notification that an invite was cancelled (Server -> Invitee)
     */
    record GameInviteCancelPush(UUID gameId) implements MessageBody {}

    /**
     * Invitation response push (Server -> Original Inviter).
     */
    record GameInviteResponsePush(UUID gameId, boolean accepted, String opponentUsername) implements MessageBody {}

    /**
     * Game move payload (Bidirectional).
     */
    record GameMove(UUID gameId, String rawMove) implements MessageBody {}

    /**
     * Game move response acknowledgment (Server -> Move sender).
     */
    record GameMoveResponse(String status, ErrorDetail error) implements MessageBody {}

    /**
     * Server-initiated game over payload.
     */
    record GameOver(UUID gameId, UUID winnerId, String winnerUsername, String reason, UserDTO user) implements MessageBody {}

    /**
     * Server-initiated game over draw payload.
     */
    record GameOverDraw(UUID gameId, UserDTO user) implements MessageBody {}

    /**
     * Surrender request payload.
     */
    record Surrender(UUID gameId) implements MessageBody {}

    /**
     * Fallback payload for unknown/unmapped actions.
     */
    record Unknown() implements MessageBody {}
}
