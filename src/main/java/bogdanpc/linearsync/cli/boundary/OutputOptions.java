package bogdanpc.linearsync.cli.boundary;

import io.quarkus.logging.Log;
import picocli.CommandLine.Option;

/**
 * Verbosity options shared by every command, mixed in by picocli so the flags
 * and their mutual exclusion are declared once.
 */
public class OutputOptions {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;

    @Option(names = {"-q", "--quiet"}, description = "Suppress non-error output")
    boolean quiet;

    /**
     * @return false when the flags contradict each other, after reporting the conflict
     */
    boolean applyLogLevel() {
        if (quiet && verbose) {
            Log.error("Error: Cannot use both --quiet and --verbose options");
            return false;
        }

        LoggingConfig.configure(quiet, verbose);
        return true;
    }
}
