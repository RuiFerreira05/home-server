package iecd.a51597.server.store;

import java.util.Comparator;
import java.util.List;

/**
 * Read-only ranking view derived from user statistics.
 */
public class Leaderboard {

    /**
     * Immutable leaderboard row.
     *
     * @param username player username
     * @param gamesWon number of wins
     * @param gamesLost number of losses
     * @param totalPlayTimeSecs accumulated playtime in seconds
     */
    public record Entry(String username, int gamesWon, int gamesLost, double totalPlayTimeSecs) {}

    private final UserStore userStore;

    /**
     * Creates a leaderboard backed by the given user store.
     *
     * @param userStore user source
     */
    public Leaderboard(UserStore userStore) {
        this.userStore = userStore;
    }

    /**
     * Returns top players ordered by wins descending and playtime ascending.
     *
     * @param limit maximum number of entries
     * @return ordered leaderboard slice
     */
    public List<Entry> getTopPlayers(int limit) {
        return userStore.getAllUsers().stream()
                .map(u -> new Entry(
                        u.getUsername(),
                        u.getStats().gamesWon(),
                        u.getStats().gamesLost(),
                        u.getStats().totalPlayTimeSecs()
                ))
                .sorted(Comparator
                        .comparingInt(Entry::gamesWon).reversed()
                        .thenComparingDouble(Entry::totalPlayTimeSecs))
                .limit(limit)
                .toList();
    }

    /**
     * Returns all players ranked.
     *
     * @return complete leaderboard
     */
    public List<Entry> getAll() {
        return getTopPlayers(Integer.MAX_VALUE);
    }
}