package iecd.a51597.common.protocol.exceptions;

/**
 * Signals low-level parsing failures while decoding raw message bytes.
 */
public class MessageParseException extends CommException {

    /**
     * Creates an empty exception.
     */
    public MessageParseException() {
        super();
    }

    /**
     * Creates an exception with message.
     *
     * @param message error description
     */
    public MessageParseException(String message) {
        super(message);
    }

    /**
     * Creates an exception with message and root cause.
     *
     * @param message error description
     * @param cause root cause
     */
    public MessageParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
