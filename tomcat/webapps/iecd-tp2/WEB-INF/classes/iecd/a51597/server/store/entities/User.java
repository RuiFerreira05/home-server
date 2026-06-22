package iecd.a51597.server.store.entities;

import iecd.a51597.common.store.PlayerStats;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Mutable user profile persisted in the user store.
 */
public class User {

    private final UUID userId;
    private String username;
    private String passwordHash;
    private String photo; // can be null
    private String nationality;
    private LocalDate dob;
    private PlayerStats stats;
    private String favoriteColor;

    /**
     * Creates a user with required identity and credential fields.
     *
     * @param userId user identifier
     * @param username display/login name
     * @param passwordHash password hash
     * @param photo optional photo reference
     */
    public User(UUID userId, String username, String passwordHash, String photo) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.photo = photo;
        this.stats = new PlayerStats();
    }

    /**
     * @return username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username new username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return user id
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * @return stored password hash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * @param passwordHash new password hash
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * @return photo reference or {@code null}
     */
    public String getPhoto() {
        return photo;
    }

    /**
     * @param photo new photo reference
     */
    public void setPhoto(String photo) {
        this.photo = photo;
    }

    /**
     * @return date of birth or {@code null}
     */
    public LocalDate getDob() {
        return dob;
    }

    /**
     * @param dob date of birth
     */
    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    /**
     * @return player statistics snapshot
     */
    public PlayerStats getStats() {
        return stats;
    }

    /**
     * @param stats player statistics
     */
    public void setStats(PlayerStats stats) {
        this.stats = stats;
    }

    /**
     * @return nationality or {@code null}
     */
    public String getNationality() {
        return nationality;
    }

    /**
     * @param nationality nationality value
     */
    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    /**
     * @return preferred game board background color or {@code null}
     */
    public String getFavoriteColor() {
        return favoriteColor;
    }

    /**
     * @param favoriteColor the preferred color value
     */
    public void setFavoriteColor(String favoriteColor) {
        this.favoriteColor = favoriteColor;
    }
}
