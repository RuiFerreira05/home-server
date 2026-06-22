package iecd.a51597.common.game;

import iecd.a51597.common.protocol.exceptions.MalformedMessageException;

/**
 * Serializes/deserializes game-specific move payloads.
 */
public interface MoveCodec {
    /**
     * Serializes a move to wire format.
     *
     * @param move move instance
     * @return serialized move string
     */
    String serialize(Move move);

    /**
     * Deserializes a move from wire format.
     *
     * @param rawMove serialized move payload
     * @return decoded move instance
     * @throws MalformedMessageException when the payload format is invalid
     */
    Move deserialize(String rawMove) throws MalformedMessageException;
}