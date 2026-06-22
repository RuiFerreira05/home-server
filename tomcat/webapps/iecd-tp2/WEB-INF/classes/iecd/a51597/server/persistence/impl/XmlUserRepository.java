package iecd.a51597.server.persistence.impl;

import iecd.a51597.server.config.ServerConfiguration;
import iecd.a51597.server.persistence.UserRepository;
import iecd.a51597.common.store.PlayerStats;
import iecd.a51597.server.store.entities.User;
import iecd.a51597.server.store.UserStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * XML-based implementation of the {@link UserRepository}.
 */
public class XmlUserRepository implements UserRepository {

    private final DocumentBuilderFactory dbf;
    private final TransformerFactory tf;
    private final Schema userSchema;

    private final File userStorePath;

    private static final Logger logger = LogManager.getLogger(XmlUserRepository.class);

    /**
     * Creates an XML user repository.
     * @param logger the logger to use
     */
    public XmlUserRepository(Logger logger) {
        this.dbf = DocumentBuilderFactory.newInstance();
        this.tf = TransformerFactory.newInstance();

        try {
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            this.userSchema = sf.newSchema(getClass().getResource("/schemas/users.xsd"));
        } catch (SAXException e) {
            throw new IllegalStateException("Failed to load users.xsd — ensure it is on the classpath", e);
        }

        this.userStorePath = new File(ServerConfiguration.USER_STORE);
        this.userStorePath.getParentFile().mkdirs();
    }

    private Validator newValidator() {
        return userSchema.newValidator();
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Loads persisted user data from the XML store and populates the in-memory UserStore.
     *
     * @param userStore the target in-memory user storage to populate
     */
    @Override
    public void loadInto(UserStore userStore) {

        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document doc = builder.parse(userStorePath);
            doc.getDocumentElement().normalize();

            try {
                newValidator().validate(new DOMSource(doc));
            } catch (SAXException e) {
                logger.error("Users file '{}' failed schema validation — aborting load: {}",
                        ServerConfiguration.USER_STORE, e.getMessage());
                return;
            }

            NodeList userNodes = doc.getElementsByTagName("user");
            int count = 0;

            for (int i = 0; i < userNodes.getLength(); i++) {
                Element el = (Element) userNodes.item(i);
                try {
                    UUID userId = UUID.fromString(el.getAttribute("id"));
                    String username = el.getAttribute("username");
                    String passwordHash = el.getAttribute("passwordHash");
                    String photo = el.hasAttribute("photo") ? el.getAttribute("photo") : null;
                    String nationality = el.hasAttribute("nationality") ? el.getAttribute("nationality") : null;
                    LocalDate dob = el.hasAttribute("dob") ? LocalDate.parse(el.getAttribute("dob")) : null;
                    String favoriteColor = el.hasAttribute("favoriteColor") ? el.getAttribute("favoriteColor") : null;

                    PlayerStats stats = new PlayerStats();
                    NodeList statsNodes = el.getElementsByTagName("stats");
                    if (statsNodes.getLength() > 0) {
                        Element statsEl = (Element) statsNodes.item(0);
                        List<PlayerStats.MatchRecord> matches = new ArrayList<>();

                        NodeList matchNodes = statsEl.getElementsByTagName("match");
                        for (int j = 0; j < matchNodes.getLength(); j++) {
                            Element matchEl = (Element) matchNodes.item(j);
                            boolean won = "WON".equals(matchEl.getAttribute("result"));
                            double playtime = parseDouble(matchEl.getAttribute("playtime"), 0.0);
                            UUID oppId = UUID.fromString(matchEl.getAttribute("opponent-id"));
                            String oppName = matchEl.getAttribute("opponent-username");
                            matches.add(new PlayerStats.MatchRecord(won, playtime, oppId, oppName));
                        }
                        stats = new PlayerStats(matches);
                    }

                    User user = new User(userId, username, passwordHash, photo);
                    user.setNationality(nationality);
                    user.setDob(dob);
                    user.setFavoriteColor(favoriteColor);
                    user.setStats(stats);

                    userStore.loadUser(user);
                    count++;
                } catch (Exception e) {
                    logger.warn("Skipping malformed user entry at index {}", i, e);
                }
            }

            logger.info("Loaded {} user(s) from '{}'", count, ServerConfiguration.USER_STORE);
        } catch (Exception e) {
            logger.error("Failed to load users from '{}'", ServerConfiguration.USER_STORE, e);
        }
    }

    /**
     * Saves user accounts and matching statistics from the UserStore to the XML file.
     *
     * @param userStore the source in-memory user storage
     */
    @Override
    public void saveFrom(UserStore userStore) {
        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document doc = builder.newDocument();
            doc.setXmlStandalone(true);

            Element root = doc.createElement("users");
            doc.appendChild(root);

            for (User user : userStore.getAllUsers()) {
                Element userEl = doc.createElement("user");
                userEl.setAttribute("id", user.getUserId().toString());
                userEl.setAttribute("username", user.getUsername());
                userEl.setAttribute("passwordHash", user.getPasswordHash());
                if (user.getPhoto() != null) userEl.setAttribute("photo", user.getPhoto());
                if (user.getNationality() != null) userEl.setAttribute("nationality", user.getNationality());
                if (user.getDob() != null) userEl.setAttribute("dob", user.getDob().toString());
                if (user.getFavoriteColor() != null) userEl.setAttribute("favoriteColor", user.getFavoriteColor());

                Element statsEl = doc.createElement("stats");
                for (PlayerStats.MatchRecord match : user.getStats().matches()) {
                    Element matchEl = doc.createElement("match");
                    matchEl.setAttribute("result", match.won() ? "WON" : "LOST");
                    matchEl.setAttribute("playtime", String.valueOf(match.playtimeSecs()));
                    matchEl.setAttribute("opponent-id", match.opponentId().toString());
                    matchEl.setAttribute("opponent-username", match.opponentUsername());
                    statsEl.appendChild(matchEl);
                }
                userEl.appendChild(statsEl);
                root.appendChild(userEl);
            }

            try {
                newValidator().validate(new DOMSource(doc));
            } catch (SAXException e) {
                logger.error("Generated users document failed schema validation — aborting save", e);
                return;
            }

            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            
            // Safe atomic write pattern (Requirement 5)
            File tmpFile = new File(userStorePath.getAbsolutePath() + ".tmp");
            transformer.transform(new DOMSource(doc), new StreamResult(tmpFile));
            java.nio.file.Files.move(tmpFile.toPath(), userStorePath.toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, 
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            logger.info("Saved {} user(s) to '{}'", userStore.getAllUsers().size(), ServerConfiguration.USER_STORE);
        } catch (Exception e) {
            logger.error("Failed to save users to '{}'", ServerConfiguration.USER_STORE, e);
        }
    }

    /**
     * Saves a profile photo payload as a file on disk, deleting the previous photo if specified.
     *
     * @param photo the profile photo bytes to write
     * @param oldPhoto the filename of the previous photo to delete (or null)
     * @return the unique random reference identifier of the newly saved photo file
     */
    @Override
    public String savePhoto(byte[] photo, String oldPhoto) {
        if (oldPhoto != null && !oldPhoto.isBlank()) {
            File oldPhotoFile = new File(ServerConfiguration.PHOTO_STORE + oldPhoto);
            if (oldPhotoFile.exists()) {
                if (oldPhotoFile.delete()) {
                    logger.info("Deleted old photo '{}'", oldPhotoFile.getName());
                } else {
                    logger.warn("Failed to delete old photo '{}'", oldPhotoFile.getName());
                }
            }
        }

        String reference = UUID.randomUUID().toString() + readFileSignature(photo);
        new Thread(() -> {
            try (FileOutputStream fos = new FileOutputStream(ServerConfiguration.PHOTO_STORE + reference)) {
                fos.write(photo);
                fos.flush();
                logger.info("Saved photo to '{}'", ServerConfiguration.PHOTO_STORE);
            } catch (Exception e) {
                logger.error("Failed to save photo to '{}'", ServerConfiguration.PHOTO_STORE, e);
            }
        }).start();

        return reference;
    }

    private String readFileSignature(byte[] photo) {
        // FIX B: Guard against array length exceptions
        if (photo == null || photo.length < 4) {
            return "";
        }

        byte[] jpg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        if (Arrays.equals(Arrays.copyOfRange(photo, 0, jpg.length), jpg)) {
            return ".jpg";
        }
        byte[] png = new byte[] {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A};
        if (photo.length >= png.length && Arrays.equals(Arrays.copyOfRange(photo, 0, png.length), png)) {
            return ".png";
        }
        byte[] webp = new byte[] {(byte) 0x42, (byte) 0x49, (byte) 0x46, (byte) 0x46};
        if (Arrays.equals(Arrays.copyOfRange(photo, 0, webp.length), webp)) {
            return ".webp";
        }
        return ""; // Unknown
    }
}
