package iecd.a51597.common.protocol.types;

/**
 * Top-level message envelope type.
 */
public enum MessageType {
    REQUEST,
    RESPONSE,
    PUSH;

    /**
     * Parses a message type string while accepting hyphen or underscore naming.
     *
     * @param string raw message type text
     * @return parsed enum value, or {@code null} when unknown
     */
    public static MessageType fromString(String string) {
        String normalized = string.replace("-", "_").toUpperCase();
        try { return MessageType.valueOf(normalized); }
        catch (IllegalArgumentException e) { return null; }
    }
}
