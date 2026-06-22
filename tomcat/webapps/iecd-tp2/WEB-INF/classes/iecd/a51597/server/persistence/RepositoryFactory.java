package iecd.a51597.server.persistence;

import iecd.a51597.server.config.ServerConfiguration;
import iecd.a51597.server.persistence.impl.XmlUserRepository;
import org.apache.logging.log4j.Logger;

/**
 * Factory for creating repository instances.
 */
public final class RepositoryFactory {
    /**
     * Creates a user repository based on the specified type.
     * @param type the repository type (e.g., "xml")
     * @param logger the logger to use
     * @return a user repository instance
     */
    public static UserRepository createUserRepository(String type, Logger logger) {
        return switch (type) {
            case "xml" -> new XmlUserRepository(logger);
            // case "sql"
            default -> throw new IllegalStateException("Unexpected persistence type: " + ServerConfiguration.PERSISTENCE_TYPE);
        };
    }
}