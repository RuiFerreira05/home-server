package iecd.a51597.server.store.exceptions;

/**
 * Raised when attempting to create or rename to an already-used username.
 */
public class UsernameAlreadyTakenException extends StoreException {
    /**
     * @param message exception message
     */
    public UsernameAlreadyTakenException(String message) {
        super(message);
    }
}
