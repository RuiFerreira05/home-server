package iecd.a51597.common.protocol.builders.client;

import iecd.a51597.client.config.ClientConfiguration;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.types.ActionType;
import iecd.a51597.common.protocol.types.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * DOM-based {@link ClientMessageBuilder} implementation that emits XML REQUEST payloads
 * conforming to {@code protocol.xsd}.
 *
 * <p>Mirrors the patterns used in {@link iecd.a51597.common.protocol.builders.server.XMLServerMessageBuilder} (server-side) so both sides
 * of the wire produce structurally identical XML envelopes, just with opposite
 * {@code type} attributes (REQUEST here, RESPONSE/PUSH on the server).</p>
 *
 * <p>Instances are safe to share across threads — every build call creates its
 * own {@link Document} and {@link Transformer}.</p>
 */
public class XMLClientMessageBuilder implements ClientMessageBuilder {

    private final DocumentBuilderFactory dbf;
    private final TransformerFactory tf;

    private static final Logger logger = LogManager.getLogger(XMLClientMessageBuilder.class);

    /**
     * Creates a builder with namespace-aware JAXP factories.
     */
    public XMLClientMessageBuilder() {
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setIgnoringComments(true);
        dbf.setNamespaceAware(true);
        tf = TransformerFactory.newInstance();
    }

    /**
     * Serializes a generic REQUEST Message envelope into bytes for sending on the TCP wire.
     *
     * @param message the message request envelope to serialize
     * @return the serialized message bytes, or null if message is invalid
     */
    @Override
    public byte[] getMessageInBytes(Message message) {
        if (message == null) {
            logger.warn("Client message builder received null message");
            return null;
        }

        if (message.messageType() != MessageType.REQUEST) {
            logger.warn("Client message builder can only serialize REQUEST, got {}", message.messageType());
            return null;
        }

        MessageBody body = message.body();
        if (body == null) {
            logger.warn("Client message builder received REQUEST with null body for action {}", message.actionType());
            return null;
        }

        try {
            UUID messageId = message.messageId();
            switch (body) {
                case MessageBody.Register(String username, String password) -> {
                    return register(messageId, username, password);
                }
                case MessageBody.LoginRequest(String username, String password) -> {
                    return login(messageId, username, password);
                }
                case MessageBody.Logout ignored -> {
                    return logout(messageId, message.sessionToken());
                }
                case MessageBody.UpdateProfile(
                        String username, String password, byte[] photo, String nationality, LocalDate dob, String favoriteColor
                ) -> {
                    return updateProfile(messageId, message.sessionToken(), username, password, photo, nationality, dob, favoriteColor);
                }
                case MessageBody.SearchUsersRequest(String query) -> {
                    return searchUsers(messageId, query);
                }
                case MessageBody.GameInviteRequest(UUID targetUserId) -> {
                    return gameInvite(messageId, message.sessionToken(), targetUserId);
                }
                case MessageBody.GameInviteResponseRequest(UUID gameId, boolean accept) -> {
                    return gameInviteResponse(messageId, message.sessionToken(), gameId, accept);
                }
                case MessageBody.GameInviteCancelRequest(UUID gameId) -> {
                    return gameInviteCancel(messageId, message.sessionToken(), gameId);
                }
                case MessageBody.GameMove(UUID gameId, String rawMove) -> {
                    return gameMove(messageId, message.sessionToken(), gameId, rawMove);
                }
                case MessageBody.Surrender(UUID gameId) -> {
                    return surrender(messageId, message.sessionToken(), gameId);
                }
                default -> {
                    logger.warn("Unsupported client REQUEST body type {} for action {}",
                            body.getClass().getSimpleName(), message.actionType());
                    return null;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to build XML for action {}", message.actionType(), e);
            return null;
        }
    }

    // ====== PRIVATE HELPERS ======

    /**
     * Builds the common message skeleton: {@code <message>}, {@code <header>},
     * and an empty {@code <body>}.
     *
     * <p>The session element is only appended when {@code sessionToken} is
     * non-null, which keeps REGISTER and LOGIN requests schema-valid.</p>
     *
     * @param actionType   semantic action for the {@code <action>} element
     * @param sessionToken optional session token; {@code null} to omit
     * @return skeleton holding references to the document, header, and body elements
     */
    private MessageSkeleton getSkeleton(ActionType actionType, UUID sessionToken, UUID messageId) {
        try {
            Document doc = dbf.newDocumentBuilder().newDocument();
            doc.setXmlStandalone(true);

            Element root = doc.createElement("message");
            root.setAttribute("type", MessageType.REQUEST.name());
            root.setAttribute("id", messageId.toString());
            root.setAttribute("version", ClientConfiguration.PROTOCOL_VERSION);
            doc.appendChild(root);

            Element header = doc.createElement("header");
            root.appendChild(header);

            Element action = doc.createElement("action");
            action.setTextContent(actionType.name());
            header.appendChild(action);

            if (sessionToken != null) {
                Element session = doc.createElement("session");
                session.setTextContent(sessionToken.toString());
                header.appendChild(session);
            }

            Element timestamp = doc.createElement("timestamp");
            timestamp.setTextContent(Instant.now().toString());
            header.appendChild(timestamp);

            Element body = doc.createElement("body");
            root.appendChild(body);

            return new MessageSkeleton(doc, header, body);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("DocumentBuilder unavailable — check JAXP classpath configuration", e);
        }
    }

    /**
     * Serializes a completed DOM document to a UTF-8 byte array.
     */
    private byte[] serialize(Document doc) {
        try {
            Transformer transformer = tf.newTransformer();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        } catch (TransformerException e) {
            throw new IllegalStateException("Failed to serialize XML document", e);
        }
    }

    /**
     * Creates a simple text-content element: {@code <tag>text</tag>}.
     */
    private Element textElement(Document doc, String tag, String text) {
        Element e = doc.createElement(tag);
        e.setTextContent(text);
        return e;
    }

    private record MessageSkeleton(Document document, Element header, Element body) {
    }

    // ====== AUTH ======

    /**
     * {@inheritDoc}
     *
     * <p>No session element is included — REGISTER requests must be unauthenticated.</p>
     */
    @Override
    public byte[] register(String username, String password) {
        return register(UUID.randomUUID(), username, password);
    }

    private byte[] register(UUID messageId, String username, String password) {
        MessageSkeleton s = getSkeleton(ActionType.REGISTER, null, messageId);
        Document doc = s.document();
        Element body = s.body();

        body.appendChild(textElement(doc, "username", username));
        body.appendChild(textElement(doc, "password", password));

        return serialize(doc);
    }

    /**
     * {@inheritDoc}
     *
     * <p>No session element is included — LOGIN requests must be unauthenticated.</p>
     */
    @Override
    public byte[] login(String username, String password) {
        return login(UUID.randomUUID(), username, password);
    }

    private byte[] login(UUID messageId, String username, String password) {
        MessageSkeleton s = getSkeleton(ActionType.LOGIN, null, messageId);
        Document doc = s.document();
        Element body = s.body();

        body.appendChild(textElement(doc, "username", username));
        body.appendChild(textElement(doc, "password", password));

        return serialize(doc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] logout(UUID sessionToken) {
        return logout(UUID.randomUUID(), sessionToken);
    }

    private byte[] logout(UUID messageId, UUID sessionToken) {
        // Body is intentionally empty for LOGOUT
        return serialize(getSkeleton(ActionType.LOGOUT, sessionToken, messageId).document());
    }

    // ====== PROFILE ======

    /**
     * {@inheritDoc}
     *
     * <p>Only non-null, non-blank fields are written into the body so the server
     * treats absent elements as "no change", consistent with the protocol spec.</p>
     */
    @Override
    public byte[] updateProfile(UUID sessionToken, String username, String password, byte[] photo, String nationality, LocalDate dob, String favoriteColor) {
        return updateProfile(UUID.randomUUID(), sessionToken, username, password, photo, nationality, dob, favoriteColor);
    }

    private byte[] updateProfile(UUID messageId, UUID sessionToken, String username, String password, byte[] photo, String nationality, LocalDate dob, String favoriteColor) {
        MessageSkeleton s = getSkeleton(ActionType.UPDATE_PROFILE, sessionToken, messageId);
        Document doc = s.document();
        Element body = s.body();

        if (username != null && !username.isBlank())
            body.appendChild(textElement(doc, "username", username));
        if (password != null && !password.isBlank())
            body.appendChild(textElement(doc, "password", password));
        if (photo != null && photo.length != 0)
            body.appendChild(textElement(doc, "photo", Base64.getEncoder().encodeToString(photo)));
        if (nationality != null && !nationality.isBlank())
            body.appendChild(textElement(doc, "nationality", nationality));
        if (dob != null && !dob.toString().isBlank())
            body.appendChild(textElement(doc, "dob", dob.toString()));
        if (favoriteColor != null && !favoriteColor.isBlank())
            body.appendChild(textElement(doc, "favoriteColor", favoriteColor));

        return serialize(doc);
    }

    // ====== SEARCH ======

    /**
     * {@inheritDoc}
     *
     * <p>Session token is omitted — the server's {@link iecd.a51597.server.handlers.SearchHandler}
     * does not require authentication for search.</p>
     */
    @Override
    public byte[] searchUsers(String query) {
        return searchUsers(UUID.randomUUID(), query);
    }

    private byte[] searchUsers(UUID messageId, String query) {
        MessageSkeleton s = getSkeleton(ActionType.SEARCH_USERS, null, messageId);
        s.body().appendChild(textElement(s.document(), "query", query));
        return serialize(s.document());
    }

    // ====== GAME ======

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] gameInvite(UUID sessionToken, UUID targetUserId) {
        return gameInvite(UUID.randomUUID(), sessionToken, targetUserId);
    }

    private byte[] gameInvite(UUID messageId, UUID sessionToken, UUID targetUserId) {
        MessageSkeleton s = getSkeleton(ActionType.GAME_INVITE, sessionToken, messageId);
        s.body().appendChild(textElement(s.document(), "target-user-id", targetUserId.toString()));
        return serialize(s.document());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] gameInviteResponse(UUID sessionToken, UUID gameId, boolean accept) {
        return gameInviteResponse(UUID.randomUUID(), sessionToken, gameId, accept);
    }

    private byte[] gameInviteResponse(UUID messageId, UUID sessionToken, UUID gameId, boolean accept) {
        MessageSkeleton s = getSkeleton(ActionType.GAME_INVITE_RESPONSE, sessionToken, messageId);
        Document doc = s.document();
        Element body = s.body();

        body.appendChild(textElement(doc, "game-id", gameId.toString()));
        body.appendChild(textElement(doc, "accept", String.valueOf(accept)));

        return serialize(doc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] gameInviteCancel(UUID sessionToken, UUID gameId) {
        return gameInviteCancel(UUID.randomUUID(), sessionToken, gameId);
    }

    private byte[] gameInviteCancel(UUID messageId, UUID sessionToken, UUID gameId) {
        MessageSkeleton s = getSkeleton(ActionType.GAME_INVITE_CANCEL, sessionToken, messageId);
        s.body().appendChild(textElement(s.document(), "game-id", gameId.toString()));
        return serialize(s.document());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The raw move is wrapped in a CDATA section so that game-specific payloads
     * containing XML special characters ({@code &}, {@code <}, etc.) survive
     * serialization intact. The server's parser extracts CDATA content via
     * {@code getTextContent()}, which handles both plain text and CDATA transparently.</p>
     */
    @Override
    public byte[] gameMove(UUID sessionToken, UUID gameId, String rawMove) {
        return gameMove(UUID.randomUUID(), sessionToken, gameId, rawMove);
    }

    private byte[] gameMove(UUID messageId, UUID sessionToken, UUID gameId, String rawMove) {
        MessageSkeleton s = getSkeleton(ActionType.GAME_MOVE, sessionToken, messageId);
        Document doc = s.document();
        Element body = s.body();

        body.appendChild(textElement(doc, "game-id", gameId.toString()));

        Element moveEl = doc.createElement("move");
        moveEl.appendChild(doc.createCDATASection(rawMove));
        body.appendChild(moveEl);

        return serialize(doc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] surrender(UUID sessionToken, UUID gameId) {
        return surrender(UUID.randomUUID(), sessionToken, gameId);
    }

    private byte[] surrender(UUID messageId, UUID sessionToken, UUID gameId) {
        MessageSkeleton s = getSkeleton(ActionType.SURRENDER, sessionToken, messageId);
        Document doc = s.document();
        Element body = s.body();

        body.appendChild(textElement(doc, "game-id", gameId.toString()));

        return serialize(doc);
    }
}