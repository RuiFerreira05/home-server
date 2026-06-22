package iecd.a51597.server.session;

import iecd.a51597.server.network.Connection;
import iecd.a51597.server.store.entities.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Authenticated user session bound to a live network connection.
 */
public class Session {

    private final User user;
    private final UUID token;
    private final UUID userId;
    private final Connection connection;
    private volatile Instant lastActivity;

    /**
     * Creates a new session.
     *
     * @param user       authenticated user
     * @param connection connection currently associated with the user
     */
    public Session(User user, Connection connection) {
        this.token = UUID.randomUUID();
        this.user = user;
        this.userId = user.getUserId();
        this.connection = connection;
        this.lastActivity = Instant.now();
    }

    /**
     * Checks whether the session is expired.
     *
     * @param timeoutSeconds timeout window in seconds
     * @return {@code true} when expired
     */
    public boolean isExpired(long timeoutSeconds) {
        Instant expiresAt = lastActivity.plusSeconds(timeoutSeconds);
        return !Instant.now().isBefore(expiresAt);
    }

    /**
     * Refreshes activity timestamp to now.
     */
    public void refresh() {
        this.lastActivity = Instant.now();
    }

    /**
     * @return session token
     */
    public UUID getToken() {
        return token;
    }

    /**
     * @return user id
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * @return user
     */
    public User getUser() {
        return user;
    }

    /**
     * @return bound connection
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * @return last observed activity instant
     */
    public Instant getLastActivity() {
        return lastActivity;
    }
}