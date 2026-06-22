package iecd.a51597.client.config;

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
 * Global client configuration loaded from {@code client_config.xml}.
 */
public final class ClientConfiguration {

    private static final String CONFIG_FILE = "/config/client_config.xml";
    private static final Logger logger = LogManager.getLogger(ClientConfiguration.class);

    private ClientConfiguration() {}

    // CONSTS

    /** Public facing server IP */
    public static String SERVER_IP = "127.0.0.1";

    /** Public facing server port */
    public static int SERVER_PORT = 5555;

    /** Default prompt style for use in the cli (This value has no effect over screen that override their own prompt) */
    public static String DEFAULT_PROMPT = ">> ";

    /** Client current communication protocol version */
    public static String PROTOCOL_VERSION = "1.0";

    /** Number of attempts to reconnect to the server on connection failure */
    public static int RECONNECT_ATTEMPTS = 3;

    /**
     * Loads configuration overrides from {@code client_config.xml} when present and valid.
     */
    public static void load() {
        try (var is = ClientConfiguration.class.getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                logger.info("No config file found on classpath at '{}' — using defaults", CONFIG_FILE);
                return;
            }

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(is);

            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = sf.newSchema(ClientConfiguration.class.getResource("/schemas/config/client_config.xsd"));
            schema.newValidator().validate(new DOMSource(doc));

            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();

            SERVER_IP = parseString(root, "serverIP", SERVER_IP);
            SERVER_PORT = parseInt(root, "serverPort", SERVER_PORT);
            DEFAULT_PROMPT = parseString(root, "defaultPrompt", DEFAULT_PROMPT) + " ";
            PROTOCOL_VERSION = parseString(root, "protocolVersion", PROTOCOL_VERSION);
            RECONNECT_ATTEMPTS = parseInt(root, "reconnectAttempts", RECONNECT_ATTEMPTS);

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