package iecd.a51597.common.protocol;

import iecd.a51597.common.protocol.types.ActionType;
import iecd.a51597.common.protocol.types.MessageType;

import java.util.UUID;

/**
 * Immutable wire-level protocol message.
 *
 * @param messageId unique message identifier used to correlate requests and responses
 * @param messageType envelope type (request, response, or server push)
 * @param version protocol version declared by the sender
 * @param actionType semantic action represented by the message
 * @param sessionToken optional authenticated session token carried in the header
 * @param body action-specific payload
 */
public record Message(
        UUID messageId,
        MessageType messageType,
        String version,
        ActionType actionType,
        UUID sessionToken,
        MessageBody body) {
}