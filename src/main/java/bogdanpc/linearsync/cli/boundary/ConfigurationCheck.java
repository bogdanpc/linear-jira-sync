package bogdanpc.linearsync.cli.boundary;

import bogdanpc.linearsync.configuration.control.SyncConfiguration;
import bogdanpc.linearsync.configuration.entity.ConfigurationException;
import io.quarkus.logging.Log;

final class ConfigurationCheck {

    private ConfigurationCheck() {
    }

    static boolean isValid(SyncConfiguration config) {
        try {
            config.validate();
            return true;
        } catch (ConfigurationException e) {
            Log.error("Configuration error: " + e.getMessage());
            Log.error("");
            Log.error("Please ensure all required configuration is set via:");
            Log.error("1. Environment variables (recommended for credentials)");
            Log.error("2. application-local.properties file (see application-local.properties.example)");
            Log.error("3. application.properties file");
            return false;
        }
    }
}
