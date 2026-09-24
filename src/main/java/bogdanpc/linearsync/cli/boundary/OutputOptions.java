package bogdanpc.linearsync.cli.boundary;

import io.quarkus.logging.Log;
import org.aesh.command.option.Option;

/**
 * Verbosity options shared by every command, mixed in by aesh so the flags
 * and their mutual exclusion are declared once.
 */
public class OutputOptions {

    @Option(name = "verbose", shortName = 'v', hasValue = false, description = "Enable verbose output")
    public boolean verbose;

    @Option(name = "quiet", shortName = 'q', hasValue = false, description = "Suppress non-error output")
    public boolean quiet;

    @SuppressWarnings("unused") // parsed pre-boot in Application.bootstrapConfigLocations; declared here so aesh accepts the flag
    @Option(name = "config", description = "Path to external configuration file (e.g., ~/.linear-jira-sync/config.properties)")
    public String configFile;

    /**
     * @return false when the flags contradict each other, after reporting the conflict
     */
    boolean applyLogLevel() {
        if (quiet && verbose) {
            Log.error("Error: Cannot use both --quiet and --verbose options");
            return true;
        }

        LoggingConfig.configure(quiet, verbose);
        return false;
    }
}
