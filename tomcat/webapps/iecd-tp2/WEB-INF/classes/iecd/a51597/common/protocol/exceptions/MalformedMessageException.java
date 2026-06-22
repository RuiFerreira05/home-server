package iecd.a51597.common.protocol.exceptions;

/**
 * Signals that a message is syntactically parseable but violates protocol shape or schema.
 */
public class MalformedMessageException extends CommException {

    /**
     * Creates an empty exception.
     */
    public MalformedMessageException() {
        super();
    }

    /**
     * Creates an exception with message.
     *
     * @param message error description
     */
    public MalformedMessageException(String message) {
        super(message);
    }

    /**
     * Creates an exception with message and root cause.
     *
     * @param message error description
     * @param cause root cause
     */
    public MalformedMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
