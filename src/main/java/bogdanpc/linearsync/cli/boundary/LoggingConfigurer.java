package bogdanpc.linearsync.cli.boundary;

/**
 * Configure logging levels in CLI commands.
 */
public final class LoggingConfigurer {

    public static final String QUARKUS_LOG_LEVEL = "quarkus.log.level";
    public static final String QUARKUS_LOG_CONSOLE_LEVEL = "quarkus.log.console.level";

    private LoggingConfigurer() {
        // Logging class - prevent instantiation
    }

    /**
     * Configures Quarkus logging levels based on quiet and verbose flags.
     *
     * @param quiet   if true, sets logging to WARN level
     * @param verbose if true, sets logging to DEBUG level
     */
    public static void configure(boolean quiet, boolean verbose) {
        var level = "INFO";

        if (quiet) {
            level = "WARN";
        } else if (verbose) {
            level = "DEBUG";
        }

        System.setProperty(QUARKUS_LOG_LEVEL, level);
        System.setProperty(QUARKUS_LOG_CONSOLE_LEVEL, level);
    }
}