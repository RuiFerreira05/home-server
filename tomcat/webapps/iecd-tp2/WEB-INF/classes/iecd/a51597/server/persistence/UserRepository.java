package iecd.a51597.server.persistence;

import iecd.a51597.server.store.UserStore;

/**
 * Interface for user persistence operations.
 */
public interface UserRepository {
    /**
     * Loads user data into the provided user store.
     * @param userStore the user store to populate
     */
    void loadInto(UserStore userStore);

    /**
     * Saves user data from the provided user store.
     * @param userStore the user store to save from
     */
    void saveFrom(UserStore userStore);

    /**
     * saves a photo in the way it's implementation decides (e.g. on the file filesystem).
     * @param photo the photo to be saved
     * @param oldPhoto the old photo reference to be replaced/deleted
     * @return a reference to the stored photo
     */
    String savePhoto(byte[] photo, String oldPhoto);
}
