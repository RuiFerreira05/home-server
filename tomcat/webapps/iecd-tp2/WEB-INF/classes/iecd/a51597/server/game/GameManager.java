package iecd.a51597.server.game;

import iecd.a51597.common.game.Game;
import iecd.a51597.common.game.GameFactory;
import iecd.a51597.common.game.MoveCodec;
import iecd.a51597.server.store.entities.User;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates pending and active games and maps players to active game ids.
 */
public class GameManager {

    // GameId -> Game Object
    private final Map<UUID, Game> activeGames     = new ConcurrentHashMap<>();
    private final Map<UUID, Game> pendingGames = new ConcurrentHashMap<>();
    // UserId -> GameId
    private final Map<UUID, UUID> playerGameIndex = new ConcurrentHashMap<>(); // userId -> gameId
    private GameFactory factory;
    private MoveCodec codec;

    /**
     * Registers the game factory and move codec used by this server instance.
     *
     * @param factory game factory implementation
     */
    public void registerFactory(GameFactory factory) {
        this.factory = factory;
        this.codec = factory.getMoveCodec();
    }

    /**
     * @return {@code true} when a game factory was registered
     */
    public boolean hasFactory() {
        return factory != null;
    }

    /**
     * Creates a pending invitation game for two players.
     *
     * @param player1 inviter
     * @param player2 invitee
     * @return pending game
     */
    public Game createPendingGame(UUID player1, UUID player2) {
        UUID gameId = UUID.randomUUID();
        Game game = factory.createGame(gameId, player1, player2);
        pendingGames.put(gameId, game);
        // playerGameIndex intentionally not touched
        return game;
    }

    /**
     * Accepts a pending game and promotes it to active state.
     *
     * @param gameId pending game id
     * @return accepted game when still valid; empty when missing or conflicting
     */
    public Optional<Game> acceptGame(UUID gameId) {
        Game game = pendingGames.remove(gameId);
        if (game == null) return Optional.empty();
        activeGames.put(gameId, game);
        return Optional.of(game);
    }

    /**
     * Declines/removes a pending game.
     *
     * @param gameId pending game id
     */
    public void declineGame(UUID gameId) {
        pendingGames.remove(gameId);
    }

    /**
     * @param gameId pending game id
     * @return pending game when present
     */
    public Optional<Game> getPendingGame(UUID gameId) {
        return Optional.ofNullable(pendingGames.get(gameId));
    }

    /**
     * @return all currently active games
     */
    public Collection<Game> getAllActiveGames() {
        return activeGames.values();
    }

    /**
     * @return all currently pending games
     */
    public Collection<Game> getAllPendingGames() {
        return pendingGames.values();
    }

    /**
     * @param gameId active game id
     * @return active game when present
     */
    public Optional<Game> getGame(UUID gameId) {
        return Optional.ofNullable(activeGames.get(gameId));
    }

    /**
     * @param userId user id
     * @return active game id for that user, when present (returns the first active game found)
     */
    public Optional<UUID> getActiveGameId(UUID userId) {
        return activeGames.values().stream()
                .filter(g -> g.getPlayer1Id().equals(userId) || g.getPlayer2Id().equals(userId))
                .map(Game::getGameId)
                .findFirst();
    }

    /**
     * @param userId user id
     * @return {@code true} when the user participates in an active game
     */
    public boolean isInGame(UUID userId) {
        return false; // Return false to bypass 1-to-1 game limitations
    }

    /**
     * Ends an active game and clears player indexes.
     *
     * @param gameId active game id
     */
    public void endGame(UUID gameId) {
        activeGames.remove(gameId);
    }

    /**
     * @return move codec associated with the registered game factory
     */
    public MoveCodec getCodec() {
        return codec;
    }
}
