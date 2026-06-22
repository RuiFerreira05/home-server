package iecd.a51597.server.store;

import iecd.a51597.server.store.entities.User;
import iecd.a51597.server.store.exceptions.UsernameAlreadyTakenException;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory user repository with username and id indexes.
 */
public class UserStore {

    // UUID -> User
    private final ConcurrentHashMap<UUID, User> userMap = new ConcurrentHashMap<>();

    //Username -> User
    private final ConcurrentHashMap<String, User> usernameIndex = new ConcurrentHashMap<>();

    /**
     * Registers a new user with a hashed password.
     *
     * @param username unique username
     * @param password plaintext password
     * @return created user
     * @throws UsernameAlreadyTakenException when username already exists
     */
    public User register(String username, String password) throws UsernameAlreadyTakenException {
        UUID userId = UUID.randomUUID();
        String passwordHash = hash(password);
        User user = new User(userId, username, passwordHash, null);

        if (usernameIndex.putIfAbsent(username, user) != null) {
            throw new UsernameAlreadyTakenException(username);
        }

        userMap.put(userId, user);
        return user;
    }

    // Package-private to allow controlled password hashing

    /**
     * Hashes a plaintext password using SHA-256 and 12 rounds of salt.
     *
     * @param password plaintext password
     * @return hex-encoded hash
     */
    public static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    /**
     * Verifies a plaintext password against a stored hash.
     * @param plaintext the unhashed password
     * @param hash the stored hash
     * @return true if they match, false otherwise
     */
    public static boolean checkPassword(String plaintext, String hash) {
        return BCrypt.checkpw(plaintext, hash);
    }

    /**
     * Looks up a user by username and plaintext password.
     *
     * @param username username
     * @param password plaintext password
     * @return matching user when credentials are valid
     */
    public Optional<User> findByCredentials(String username, String password) {
        User user = usernameIndex.get(username);
        if (user != null && checkPassword(password, user.getPasswordHash())) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * @param userId user id
     * @return user when present
     */
    public Optional<User> findById(UUID userId) {
        return Optional.ofNullable(userMap.get(userId));
    }

    /**
     * @param username username
     * @return user when present
     */
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(usernameIndex.get(username));
    }

    /**
     * Performs case-insensitive username substring search.
     *
     * @param query search text
     * @return matching users
     */
    public List<User> searchByUsername(String query) {
        List<User> users = new ArrayList<>();
        query = query.toLowerCase();
        for (User user : userMap.values()) {
            if (user.getUsername().toLowerCase().contains(query)) {
                users.add(user);
            }
        }
        return users;
    }

    /**
     * Renames a user.
     *
     * @param user        target user
     * @param newUsername new unique username
     * @throws UsernameAlreadyTakenException when target username is already taken
     */
    public void updateUsername(User user, String newUsername) throws UsernameAlreadyTakenException {
        if (usernameIndex.putIfAbsent(newUsername, user) != null) {
            throw new UsernameAlreadyTakenException(newUsername);
        }
        usernameIndex.remove(user.getUsername());
        user.setUsername(newUsername);
    }

    /**
     * Updates a user's password. The provided password will be hashed internally.
     *
     * @param user                 The user to update
     * @param newPlaintextPassword The new plaintext password (will be hashed)
     */
    public void updatePassword(User user, String newPlaintextPassword) {
        user.setPasswordHash(hash(newPlaintextPassword));
    }

    /**
     * Updates user photo metadata.
     *
     * @param user  target user
     * @param photo new photo value
     */
    public void updatePhoto(User user, String photo) {
        user.setPhoto(photo);
    }

    /**
     * Deletes a user from all indexes.
     *
     * @param user target user
     */
    public void delete(User user) {
        userMap.remove(user.getUserId());
        usernameIndex.remove(user.getUsername());
    }

    /**
     * Loads a user from persistence into the in-memory indexes.
     *
     * @param user user to add
     */
    public void loadUser(User user) {
        userMap.put(user.getUserId(), user);
        usernameIndex.put(user.getUsername(), user);
    }

    /**
     * @return live collection of all stored users
     */
    public Collection<User> getAllUsers() {
        return userMap.values();
    }

    /**
     * Updates a user's nationality.
     * @param user target user
     * @param nationality new nationality
     */
    public void updateNationality(User user, String nationality) {
        user.setNationality(nationality);
    }

    /**
     * Updates a user's date of birth.
     * @param user target user
     * @param dob new date of birth
     */
    public void updateDob(User user, LocalDate dob) {
        user.setDob(dob);
    }
}
