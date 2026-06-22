package iecd.a51597.common.protocol.parsers;

import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.exceptions.CommException;
import iecd.a51597.common.protocol.exceptions.MalformedMessageException;
import iecd.a51597.common.protocol.exceptions.MessageParseException;
import iecd.a51597.common.protocol.types.ActionType;
import iecd.a51597.common.protocol.types.ErrorCodeType;
import iecd.a51597.common.protocol.types.MessageType;
import iecd.a51597.common.store.PlayerStats;
import iecd.a51597.common.store.UserDTO;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * XML implementation of {@link CommParser} backed by {@code protocol.xsd} validation.
 */
public class XMLParser implements CommParser {

    private final Schema schema;
    private final DocumentBuilderFactory dbf;

    /**
     * Creates a parser and loads protocol schema resources from the classpath.
     */
    public XMLParser() {
        try {
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            schema = sf.newSchema(getClass().getResource("/schemas/protocol.xsd"));
        } catch (SAXException e) {
            throw new IllegalStateException("Failed to load protocol.xsd — ensure it is on the classpath", e);
        }

        dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setIgnoringComments(true);
        dbf.setNamespaceAware(true);
    }

    private DocumentBuilder getNewBuilder() {
        try {
            return dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("DocumentBuilder unavailable — check JAXP classpath configuration", e);
        }
    }

    private Validator getNewValidator() {
        return schema.newValidator();
    }

    /**
     * Parses and validates a single XML protocol message.
     *
     * @param input input stream with XML payload bytes
     * @return parsed immutable message
     * @throws CommException when parsing or schema validation fails
     */
    @Override
    public Message parseMessage(InputStream input) throws CommException {

        Validator validator = getNewValidator();
        DocumentBuilder builder;
        builder = getNewBuilder();

        Document doc;
        try {
            doc = builder.parse(input);
        } catch (Exception e) {
            throw new MessageParseException("Failed to parse XML message: " + e.getMessage(), e);
        }

        validateMessage(doc, validator);

        return createMessage(doc);
    }

    /**
     * Materializes a protocol message instance from a validated XML document.
     */
    private Message createMessage(Document doc) throws MalformedMessageException {
        Element root = doc.getDocumentElement();

        UUID messageId = UUID.fromString(root.getAttribute("id"));
        MessageType type = MessageType.fromString(root.getAttribute("type"));
        String version = root.getAttribute("version");

        Element header = (Element) doc.getElementsByTagName("header").item(0);
        Element body = (Element) doc.getElementsByTagName("body").item(0);

        String actionRaw = getField(header, "action");
        ActionType action = actionRaw != null ? ActionType.fromString(actionRaw) : ActionType.UNKNOWN;
        if (action == null) action = ActionType.UNKNOWN;

        String sessionTokenRaw = getField(header, "session");
        UUID sessionToken = sessionTokenRaw != null ? UUID.fromString(sessionTokenRaw) : null;

        return new Message(messageId, type, version, action, sessionToken, parseBody(type, action, body));
    }

    /**
     * Parses action-specific payload fields from the body element.
     */
    private MessageBody parseBody(MessageType type, ActionType action, Element body) throws MalformedMessageException {
        return switch (type) {
            case REQUEST -> parseRequestBody(action, body);
            case RESPONSE -> parseResponseBody(action, body);
            case PUSH -> parsePushBody(action, body);
        };
    }

    private MessageBody parseRequestBody(ActionType action, Element body) throws MalformedMessageException {
        return switch (action) {
            case REGISTER -> new MessageBody.Register(require(body, "username"), require(body, "password"));
            case LOGIN -> new MessageBody.LoginRequest(require(body, "username"), require(body, "password"));
            case LOGOUT -> new MessageBody.Logout();
            case UPDATE_PROFILE ->
                    new MessageBody.UpdateProfile(getField(body, "username"), getField(body, "password"), getBytes(body, "photo"), getField(body, "nationality"), getLocalDate(body, "dob"), getField(body, "favoriteColor"));
            case SEARCH_USERS -> new MessageBody.SearchUsersRequest(require(body, "query"));
            case GAME_INVITE -> new MessageBody.GameInviteRequest(requireUUID(body, "target-user-id"));
            case GAME_INVITE_RESPONSE ->
                    new MessageBody.GameInviteResponseRequest(requireUUID(body, "game-id"), Boolean.parseBoolean(require(body, "accept")));
            case GAME_INVITE_CANCEL -> new MessageBody.GameInviteCancelRequest(requireUUID(body, "game-id"));
            case GAME_MOVE ->
                    new MessageBody.GameMove(requireUUID(body, "game-id"), requireElement(body, "move").getTextContent());
            case SURRENDER -> new MessageBody.Surrender(requireUUID(body, "game-id"));
            case GAME_OVER, GAME_OVER_DRAW, UNKNOWN -> new MessageBody.Unknown();
        };
    }

    private MessageBody parseResponseBody(ActionType action, Element body) throws MalformedMessageException {
        String status = require(body, "status");
        MessageBody.ErrorDetail error = parseError(body);

        return switch (action) {
            case REGISTER -> new MessageBody.RegisterResponse(status, error);
            case LOGIN -> "OK".equals(status)
                    ? new MessageBody.LoginResponse(
                    status,
                    requireUUID(body, "session"),
                    parseUserDTO(requireElement(body, "user")),
                    null
            )
                    : new MessageBody.LoginResponse(status, null, null, error);
            case LOGOUT -> new MessageBody.LogoutResponse(status, error);
            case UPDATE_PROFILE -> "OK".equals(status)
                    ? new MessageBody.UpdateProfileResponse(status, parseUserDTO(requireElement(body, "user")), null)
                    : new MessageBody.UpdateProfileResponse(status, null, error);
            case SEARCH_USERS -> "OK".equals(status)
                    ? new MessageBody.SearchUsersResponse(status, parseUserResults(body), null)
                    : new MessageBody.SearchUsersResponse(status, null, error);
            case GAME_INVITE -> "OK".equals(status)
                    ? new MessageBody.GameInviteResponse(status, requireUUID(body, "game-id"), null)
                    : new MessageBody.GameInviteResponse(status, null, error);
            case GAME_INVITE_RESPONSE -> new MessageBody.GameInviteResponseResult(status, error);
            case GAME_INVITE_CANCEL -> new MessageBody.GameInviteCancelResponse(status, error);
            case GAME_MOVE -> new MessageBody.GameMoveResponse(status, error);
            case SURRENDER -> new MessageBody.GenericResponse(status, error);
            case UNKNOWN -> new MessageBody.GenericResponse(status, error);
            case GAME_OVER, GAME_OVER_DRAW -> new MessageBody.Unknown();
        };
    }

    private MessageBody parsePushBody(ActionType action, Element body) throws MalformedMessageException {
        return switch (action) {
            case GAME_INVITE -> new MessageBody.GameInvitePush(
                    requireUUID(body, "from-user-id"),
                    require(body, "from-username"),
                    requireUUID(body, "game-id")
            );
            case GAME_INVITE_RESPONSE -> new MessageBody.GameInviteResponsePush(
                    requireUUID(body, "game-id"),
                    Boolean.parseBoolean(require(body, "accepted")),
                    getField(body, "opponent-username")
            );
            case GAME_INVITE_CANCEL -> new MessageBody.GameInviteCancelPush(requireUUID(body, "game-id"));
            case GAME_MOVE ->
                    new MessageBody.GameMove(requireUUID(body, "game-id"), requireElement(body, "move").getTextContent());
            case GAME_OVER -> new MessageBody.GameOver(
                    requireUUID(body, "game-id"),
                    requireUUID(body, "winner-id"),
                    require(body, "winner-username"),
                    getField(body, "reason"),
                    parseUserDTO(requireElement(body, "user"))
            );
            case GAME_OVER_DRAW -> new MessageBody.GameOverDraw(requireUUID(body, "game-id"), parseUserDTO(requireElement(body, "user")));
            case SURRENDER, REGISTER, LOGIN, LOGOUT, UPDATE_PROFILE, SEARCH_USERS, UNKNOWN -> new MessageBody.Unknown();
        };
    }

    private UserDTO parseUserDTO(Element userEl) throws MalformedMessageException {
        LocalDate dob = getField(userEl, "dob") != null ? LocalDate.parse(getField(userEl, "dob")) : null;
        boolean online = getField(userEl, "online") != null && Boolean.parseBoolean(getField(userEl, "online"));
        return new UserDTO(
                requireUUID(userEl, "id"),
                require(userEl, "username"),
                getField(userEl, "photo"),
                getField(userEl, "nationality"),
                dob,
                parsePlayerStats(userEl),
                online,
                getField(userEl, "favoriteColor")
        );
    }

    private PlayerStats parsePlayerStats(Element userEl) throws MalformedMessageException {
        PlayerStats stats = new PlayerStats();
        Element statsEl = getElement(userEl, "stats");
        if (statsEl == null) return stats;

        NodeList matches = statsEl.getElementsByTagName("match");
        for (int i = 0; i < matches.getLength(); i++) {
            Element matchEl = (Element) matches.item(i);
            stats = stats.withMatch(
                    requireAttribute(matchEl, "result").equals("WON"),
                    requireDoubleAttribute(matchEl, "playtime"),
                    parseUuidAttribute(matchEl, "opponent-id"),
                    requireAttribute(matchEl, "opponent-username")
            );
        }

        return stats;
    }

    private java.util.List<UserDTO> parseUserResults(Element body) throws MalformedMessageException {
        java.util.List<UserDTO> results = new java.util.ArrayList<>();
        Element resultsEl = getElement(body, "results");
        if (resultsEl != null) {
            NodeList nodes = resultsEl.getElementsByTagName("user");
            for (int i = 0; i < nodes.getLength(); i++) {
                results.add(parseUserDTO((Element) nodes.item(i)));
            }
        }
        return results;
    }

    private MessageBody.ErrorDetail parseError(Element body) {
        Element errorEl = getElement(body, "error");
        if (errorEl == null) return null;

        String code = errorEl.getAttribute("code");
        ErrorCodeType errorCodeType = ErrorCodeType.valueOf(code);
        String message = errorEl.getTextContent() == null ? null : errorEl.getTextContent().trim();
        return new MessageBody.ErrorDetail(errorCodeType, message);
    }

    private String requireAttribute(Element element, String attr) throws MalformedMessageException {
        String value = element.getAttribute(attr);
        if (value == null || value.isBlank()) {
            throw new MalformedMessageException("Missing required attribute: @" + attr);
        }
        return value;
    }

    private UUID parseUuidAttribute(Element element, String attr) throws MalformedMessageException {
        try {
            return UUID.fromString(requireAttribute(element, attr));
        } catch (IllegalArgumentException e) {
            throw new MalformedMessageException("Invalid UUID in attribute @" + attr, e);
        }
    }

    private double requireDoubleAttribute(Element element, String attr) throws MalformedMessageException {
        try {
            return Double.parseDouble(requireAttribute(element, attr));
        } catch (NumberFormatException e) {
            throw new MalformedMessageException("Invalid decimal in attribute @" + attr, e);
        }
    }

    private Element getElement(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        return (Element) nodes.item(0);
    }

    /**
     * Reads a required textual field.
     */
    private String require(Element parent, String tag) throws MalformedMessageException {
        String value = getField(parent, tag);
        if (value == null)
            throw new MalformedMessageException("Missing required field: <" + tag + ">");
        return value;
    }

    /**
     * Reads a required UUID field.
     */
    private UUID requireUUID(Element parent, String tag) throws MalformedMessageException {
        try {
            return UUID.fromString(require(parent, tag));
        } catch (IllegalArgumentException e) {
            throw new MalformedMessageException("Invalid UUID in field <" + tag + ">", e);
        }
    }

    /**
     * Reads a required child element.
     */
    private Element requireElement(Element parent, String tag) throws MalformedMessageException {
        var nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0)
            throw new MalformedMessageException("Missing required element: <" + tag + ">");
        return (Element) nodes.item(0);
    }

    /**
     * Reads the first matching child tag text, or {@code null} when absent.
     */
    private String getField(Element rootElement, String tag) {
        NodeList nodes = rootElement.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent().trim();
    }

    private byte[] getBytes(Element rootElement, String tag) {
        NodeList nodes = rootElement.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        return Base64.getDecoder().decode(nodes.item(0).getTextContent());
    }

    private LocalDate getLocalDate(Element body, String tag) throws MalformedMessageException {
        String dobStr = getField(body, tag);
        if (dobStr == null) return null;
        try {
            return LocalDate.parse(dobStr);
        } catch (Exception e) {
            throw new MalformedMessageException("Invalid date format in field <" + tag + ">: " + dobStr, e);
        }
    }

    /**
     * Validates an XML document against the protocol schema.
     */
    private void validateMessage(Document document, Validator validator) throws MalformedMessageException {
        try {
            validator.validate(new DOMSource(document));
        } catch (Exception e) {
            throw new MalformedMessageException("XML message does not conform to schema: " + e.getMessage(), e);
        }
    }
}
