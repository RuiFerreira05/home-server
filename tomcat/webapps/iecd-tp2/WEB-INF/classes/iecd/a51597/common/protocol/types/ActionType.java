package iecd.a51597.common.protocol.types;

/**
 * Supported semantic actions in the application protocol.
 */
public enum ActionType {
    UNKNOWN,
    REGISTER,
    LOGIN,
    LOGOUT,
    UPDATE_PROFILE,
    SEARCH_USERS,
    GAME_INVITE,
    GAME_INVITE_RESPONSE,
    GAME_INVITE_CANCEL,
    GAME_MOVE,
    GAME_OVER,
    GAME_OVER_DRAW,
    SURRENDER;

    /**
     * Parses an action string while accepting hyphen or underscore naming.
     *
     * @param string raw action text
     * @return parsed enum value, or {@code null} when unknown
     */
    public static ActionType fromString(String string) {
        String normalized = string.replace("-", "_").toUpperCase();
        try { return ActionType.valueOf(normalized); }
        catch (IllegalArgumentException e) { return null; }
    }
}
