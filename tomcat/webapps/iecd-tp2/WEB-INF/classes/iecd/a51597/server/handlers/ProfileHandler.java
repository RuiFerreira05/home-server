package iecd.a51597.server.handlers;

import iecd.a51597.server.network.Connection;
import iecd.a51597.server.persistence.PersistenceManager;
import iecd.a51597.server.session.Session;
import iecd.a51597.server.session.SessionManager;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.builders.server.ServerMessageBuilder;
import iecd.a51597.common.protocol.types.ErrorCodeType;
import iecd.a51597.server.store.entities.User;
import iecd.a51597.server.store.UserStore;
import iecd.a51597.server.store.exceptions.UsernameAlreadyTakenException;

import java.util.Optional;

/**
 * Handles user profile updates for authenticated sessions.
 */
public class ProfileHandler extends BaseHandler {

    private final UserStore userStore;
    private final java.util.Set<String> validCountryCodes = new java.util.HashSet<>();

    /**
     * Creates a profile handler.
     */
    public ProfileHandler(ServerMessageBuilder messageBuilder, SessionManager sessionManager, UserStore userStore, PersistenceManager persistenceManager) {
        super(messageBuilder, sessionManager, persistenceManager);
        this.userStore = userStore;
        loadCountryCodes();
    }

    private void loadCountryCodes() {
        try (java.io.InputStream is = getClass().getResourceAsStream("/country_codes.xml")) {
            if (is == null) {
                logger.error("Could not find /country_codes.xml on the classpath");
                return;
            }
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
            org.w3c.dom.Document doc = db.parse(is);
            org.w3c.dom.NodeList nodes = doc.getElementsByTagName("country");
            for (int i = 0; i < nodes.getLength(); i++) {
                org.w3c.dom.Element el = (org.w3c.dom.Element) nodes.item(i);
                String code = el.getAttribute("code");
                if (code != null) {
                    validCountryCodes.add(code.trim().toUpperCase());
                }
            }
            logger.info("Loaded {} valid country codes for profile verification", validCountryCodes.size());
        } catch (Exception e) {
            logger.error("Failed to load/parse country codes XML", e);
        }
    }

    /**
     * Applies profile changes from an update request.
     * @param message the update request message
     * @param connection the client connection
     */
    public void updateProfile(Message message, Connection connection) {
        logger.info("Received profile update request from connection");
        Optional<Session> sessionOpt = requireSession(message, connection);
        if (sessionOpt.isEmpty()) {
            logger.warn("Profile update request missing valid session token");
            return;
        };

        User user = sessionOpt.get().getUser();
        MessageBody.UpdateProfile body = (MessageBody.UpdateProfile) message.body();

        try {
            if (body.username() != null && !body.username().isBlank()) userStore.updateUsername(user, body.username());
            if (body.password() != null && !body.password().isBlank()) userStore.updatePassword(user, body.password());
            if (body.nationality() != null && !body.nationality().isBlank()) {
                String code = body.nationality().trim().toUpperCase();
                if (!validCountryCodes.isEmpty() && !validCountryCodes.contains(code)) {
                    logger.warn("Rejecting profile update: invalid nationality '{}'", code);
                    sendError(message, connection, ErrorCodeType.MALFORMED_REQUEST, "Invalid nationality country code: " + body.nationality());
                    return;
                }
                userStore.updateNationality(user, code);
            }
            if (body.photo() != null) {
                String reference = persistenceManager.savePhoto(body.photo(), user.getPhoto());
                userStore.updatePhoto(user, reference);
            }
            if (body.dob() != null) userStore.updateDob(user, body.dob());
            if (body.favoriteColor() != null) user.setFavoriteColor(body.favoriteColor());
        } catch (UsernameAlreadyTakenException e) {
            logger.error("Failed to update profile for user {}", user.getUserId(), e);
            sendError(message, connection, ErrorCodeType.USERNAME_TAKEN, "Username is already taken");
            return;
        }

        connection.sendMessage(messageBuilder.updateProfileSuccess(message.messageId(), user));
    }
}
