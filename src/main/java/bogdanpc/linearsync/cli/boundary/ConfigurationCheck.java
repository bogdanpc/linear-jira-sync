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
            Log.error("2. ~/.linear-jira-sync/config.properties, or another file passed with --config");
            return false;
        }
    }
}
