package iecd.a51597.common.protocol.parsers;

import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.exceptions.CommException;

import java.io.InputStream;

/**
 * Parses a framed input stream into protocol messages.
 */
public interface CommParser {
    /**
     * Parses a single protocol message from the provided stream.
     *
     * @param input input stream containing XML payload bytes
     * @return parsed message
     * @throws CommException when parsing or validation fails
     */
    Message parseMessage(InputStream input) throws CommException;
}
