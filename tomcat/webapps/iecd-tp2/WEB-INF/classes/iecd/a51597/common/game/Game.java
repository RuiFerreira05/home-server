package iecd.a51597.common.game;

import java.util.UUID;

/**
 * Abstraction of a two-player game instance managed by the server.
 */
public interface Game {
    /**
     * @return unique identifier for this game instance
     */
    UUID getGameId();

    /**
     * @return first player uuid (inviter/origin player)
     */
    UUID getPlayer1Id();

    /**
     * @return second player uuid (invitee)
     */
    UUID getPlayer2Id();

    /**
     * @return timestamp when the game was created
     */
    long getStartTimeMillis();

    /**
     * Applies a move for one player.
     *
     * @param playerId playerId issuing the move
     * @param move move to apply
     * @return domain result indicating acceptance, rejection, or game completion
     */
    MoveResult applyMove(UUID playerId, Move move);
}