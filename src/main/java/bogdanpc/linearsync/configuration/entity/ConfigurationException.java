package bogdanpc.linearsync.configuration.entity;

/**
 * Exception thrown when configuration is invalid or missing required values.
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
