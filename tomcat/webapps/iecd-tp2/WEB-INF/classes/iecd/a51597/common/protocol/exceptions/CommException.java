package iecd.a51597.common.protocol.exceptions;

/**
 * Base checked exception for protocol transport/parsing failures.
 */
public abstract class CommException extends Exception {

    /**
     * Creates an empty exception.
     */
    public CommException() {
        super();
    }

    /**
     * Creates an exception with message.
     *
     * @param message error description
     */
    public CommException(String message) {
        super(message);
    }

    /**
     * Creates an exception with message and root cause.
     *
     * @param message error description
     * @param cause root cause
     */
    public CommException(String message, Throwable cause) {
        super(message, cause);
    }
}
