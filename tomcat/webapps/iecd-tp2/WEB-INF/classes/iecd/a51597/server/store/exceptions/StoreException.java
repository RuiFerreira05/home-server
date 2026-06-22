package iecd.a51597.server.store.exceptions;

/**
 * Base runtime exception for user store operations.
 */
public class StoreException extends RuntimeException {
    /**
     * @param message exception message
     */
    public StoreException(String message) {
        super(message);
    }
}
