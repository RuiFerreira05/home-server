package iecd.a51597.server.persistence;

import iecd.a51597.server.config.ServerConfiguration;
import iecd.a51597.server.store.UserStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles XML persistence of server state (currently user repository).
 */
public class PersistenceManager {

    private static final Logger logger = LogManager.getLogger(PersistenceManager.class);
    private final UserRepository userRepository;
    private final Thread persistenceThread;

    private final UserStore userStore;

    /**
     * Creates a persistence manager for a user store.
     *
     * @param userStore target user store
     */
    public PersistenceManager(UserStore userStore) {
        this.userStore = userStore;
        this.userRepository = RepositoryFactory.createUserRepository(ServerConfiguration.PERSISTENCE_TYPE, logger);

        // this would probably be much better with a ScheduledThreadExecutor, but it's good for now.
        this.persistenceThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(ServerConfiguration.PERSISTENCE_INTERVAL_MS);
                    save();
                } catch (InterruptedException e) {
                    logger.info("Persistence thread interrupted, shutting down...");
                    break;
                } catch (Exception e) {
                    logger.error("Error during periodic persistence", e);
                }
            }
        });

        this.persistenceThread.start();
    }

    /**
     * Loads all persisted data into memory.
     */
    public void load() {
        userRepository.loadInto(userStore);
    }

    /**
     * Saves in-memory state to persistence files.
     */
    public synchronized void save() {
        userRepository.saveFrom(userStore);
    }

    /**
     * Saves a photo and returns its reference.
     * @param photo the photo bytes
     * @param oldPhoto the old photo reference (to be deleted)
     * @return the new photo reference
     */
    public String savePhoto(byte[] photo, String oldPhoto) {return userRepository.savePhoto(photo, oldPhoto);}

    /**
     * Interrupts and halts the background thread executing periodic XML persistence.
     */
    public void shutdownThread() {
        this.persistenceThread.interrupt();
    }
}