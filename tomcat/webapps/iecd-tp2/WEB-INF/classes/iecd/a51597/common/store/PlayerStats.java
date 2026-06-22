package iecd.a51597.common.store;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immutable aggregate of a player's historical match records.
 *
 * @param matches ordered match history
 */
public record PlayerStats(List<MatchRecord> matches) {

    /**
     * Immutable single match record.
     *
     * @param won              whether the match was won
     * @param playtimeSecs     match duration in seconds
     * @param opponentId       opponent user id
     * @param opponentUsername opponent username at match time
     */
    public record MatchRecord(boolean won, double playtimeSecs, UUID opponentId, String opponentUsername) {
    }

    /**
     * Creates an empty stats set.
     */
    public PlayerStats() {
        this(new ArrayList<>());
    }

    /**
     * Returns a new stats instance with one appended match.
     */
    public PlayerStats withMatch(boolean won, double playtimeSecs, UUID opponentId, String opponentUsername) {
        var newMatches = new ArrayList<>(matches);
        newMatches.add(new MatchRecord(won, playtimeSecs, opponentId, opponentUsername));
        return new PlayerStats(newMatches);
    }

    /**
     * @return number of recorded games
     */
    public int gamesPlayed() {
        return matches.size();
    }

    /**
     * @return number of wins
     */
    public int gamesWon() {
        return (int) matches.stream().filter(MatchRecord::won).count();
    }

    /**
     * @return number of losses
     */
    public int gamesLost() {
        return (int) matches.stream().filter(m -> !m.won()).count();
    }

    /**
     * @return ratio of wins to total games played
     */
    public float winRate() {
        return gamesPlayed() == 0 ? 0 : (float) gamesWon() / gamesPlayed();
    }

    /**
     * @return total play time across all matches, in seconds
     */
    public double totalPlayTimeSecs() {
        return matches.stream().mapToDouble(MatchRecord::playtimeSecs).sum();
    }

    /**
     * @return average play time across all matches, in seconds
     */
    public double averagePlayTimeSecs() {
        return gamesPlayed() == 0 ? 0.0 : totalPlayTimeSecs() / gamesPlayed();
    }
}