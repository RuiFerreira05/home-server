package iecd.a51597.common.game;

import iecd.a51597.server.store.entities.User;

import java.util.UUID;

/**
 * Factory used to plug a concrete game implementation into the server.
 */
public interface GameFactory {
    /**
     * Creates a game instance for two players.
     *
     * @param gameId externally generated game id
     * @param player1Id first player
     * @param player2Id second player
     * @return created game instance
     */
    Game createGame(UUID gameId, UUID player1Id, UUID player2Id);

    /**
     * @return codec used to serialize and deserialize game moves
     */
    MoveCodec getMoveCodec();
}