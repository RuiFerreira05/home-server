package iecd.a51597.common.protocol.types;

/**
 * Canonical error codes returned by the server protocol.
 */
public enum ErrorCodeType {
    AUTH_FAILED,
    USERNAME_TAKEN,
    SESSION_EXPIRED,
    NOT_AUTHENTICATED,
    USER_NOT_FOUND,
    USER_NOT_ONLINE,
    ALREADY_IN_GAME,
    INVALID_MOVE,
    INVALID_PASSWORD,
    UNKNOWN_ACTION,
    INTERNAL_ERROR,
    MALFORMED_REQUEST,
    UNEXPECTED_MESSAGE_TYPE,
    UNEXPECTED_MESSAGE_ACTION,
    OUTDATED_PROTOCOL, GAME_NOT_FOUND;

    /**
     * Parses an error code string while accepting hyphen or underscore naming.
     *
     * @param string raw error code text
     * @return parsed enum value, or {@code null} when unknown
     */
    public static ErrorCodeType fromString(String string) {
        String normalized = string.replace("-", "_").toUpperCase();
        try { return ErrorCodeType.valueOf(normalized); }
        catch (IllegalArgumentException e) { return null; }
    }
}
