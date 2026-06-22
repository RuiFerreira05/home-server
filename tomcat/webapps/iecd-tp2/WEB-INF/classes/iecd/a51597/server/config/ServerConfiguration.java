package iecd.a51597.server.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;

/**
 * Global server configuration loaded from {@code config.xml}.
 */
public final class ServerConfiguration {

    private static final String CONFIG_FILE = "/config/config.xml";
    private static final Logger logger = LogManager.getLogger(ServerConfiguration.class);

    private ServerConfiguration() {}

    // CONSTS

    /** Default TCP listening port. */
    public static int DEFAULT_PORT = 5555;
    /** Maximum accepted frame payload size in bytes. */
    public static int MAX_FRAME_SIZE = 1024 * 1024;
    /** Session timeout in seconds. */
    public static long SESSION_TIMEOUT_SECONDS = 60 * 30; // 30 mins
    /** Width used by CLI status box rendering. */
    public static int STATUS_BOX_WIDTH = 42;
    /** Supported protocol version string. */
    public static String PROTOCOL_VERSION = "1.0";
    /** Which way to store data */
    public static String PERSISTENCE_TYPE = "xml";
    /** User persistence file path. (only has effect if PERSISTENCE_TYPE=xml) */
    public static String USER_STORE = "data/users.xml";
    /** Photo persistence folder path. (only has effect if PERSISTENCE_TYPE=xml)*/
    public static String PHOTO_STORE = "data/photos/";
    /** number of Milliseconds between save intervals */
    public static long PERSISTENCE_INTERVAL_MS = 60 * 1000; // 1 min
    /** Turn move timeout in seconds */
    public static int TURN_TIMEOUT_SECONDS = 30;

    /**
     * Loads configuration overrides from {@code config.xml} when present and valid.
     */
    public static void load() {
        try (var is = ServerConfiguration.class.getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                logger.info("No config file found on classpath at '{}' — using defaults", CONFIG_FILE);
                return;
            }

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(is);

            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = sf.newSchema(ServerConfiguration.class.getResource("/schemas/config/config.xsd"));
            schema.newValidator().validate(new DOMSource(doc));

            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();

            DEFAULT_PORT = parseInt(root, "defaultPort", DEFAULT_PORT);
            MAX_FRAME_SIZE = parseInt(root, "maxFrameSize", MAX_FRAME_SIZE);
            SESSION_TIMEOUT_SECONDS = parseLong(root, "sessionTimeoutSeconds", SESSION_TIMEOUT_SECONDS);
            STATUS_BOX_WIDTH = parseInt(root, "statusBoxWidth", STATUS_BOX_WIDTH);
            PROTOCOL_VERSION = parseString(root, "protocolVersion", PROTOCOL_VERSION);
            PERSISTENCE_TYPE = parseString(root, "persistenceType", PERSISTENCE_TYPE);
            USER_STORE = parseString(root, "userStore", USER_STORE);
            PHOTO_STORE = parseString(root, "photoStore", PHOTO_STORE);
            PERSISTENCE_INTERVAL_MS = parseLong(root, "persistenceIntervalMs", PERSISTENCE_INTERVAL_MS);
            TURN_TIMEOUT_SECONDS = parseInt(root, "turnTimeoutSeconds", TURN_TIMEOUT_SECONDS);

            logger.info("Configuration loaded from classpath '{}'", CONFIG_FILE);
        } catch (Exception e) {
            logger.error("Failed to load config from classpath '{}', using defaults", CONFIG_FILE, e);
        }
    }

    private static String parseString(Element root, String tag, String defaultValue) {
        var nodes = root.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return defaultValue;
        String value = nodes.item(0).getTextContent().trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private static int parseInt(Element root, String tag, int defaultValue) {
        try {
            return Integer.parseInt(parseString(root, tag, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid value for <{}>, using default: {}", tag, defaultValue);
            return defaultValue;
        }
    }

    private static long parseLong(Element root, String tag, long defaultValue) {
        try {
            return Long.parseLong(parseString(root, tag, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid value for <{}>, using default: {}", tag, defaultValue);
            return defaultValue;
        }
    }
}