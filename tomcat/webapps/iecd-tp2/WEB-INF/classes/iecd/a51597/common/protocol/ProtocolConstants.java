package iecd.a51597.common.protocol;

import java.util.UUID;

/**
 * Constants shared by protocol parsing/building logic.
 */
public final class ProtocolConstants {

    // This class shouldn't be instantiated
    private ProtocolConstants() {}

    /**
     * Synthetic message id used when an invalid request cannot be correlated to a parsed id.
     */
    public static final UUID ERROR_NO_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
}