package bogdanpc.linearsync.cli.boundary;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parses the CLI options that affect configuration and sets them as system properties
 * before Quarkus initializes. Aesh parses arguments only after boot, when config mappings
 * are resolved and log levels applied, so a property set from a command is never seen.
 * Aesh still declares these options, which keeps validation and help in one place.
 */
final class BootstrapConfig {

    static final String APP_LOG_LEVEL = "quarkus.log.category.\"bogdanpc.linearsync\".level";
    static final String CONSOLE_LOG_LEVEL = "quarkus.log.console.level";

    private static final Map<String, String> VALUE_OPTIONS = Map.of("--config", "smallrye.config.locations",
            "--state-dir", "sync.storage.location", "--jira-project-key", "jira.project.key");

    /** Aesh accepts bundled switches such as {@code -dv}; only options without a value can be bundled. */
    private static final Pattern BUNDLED_SWITCHES = Pattern.compile("-[adfqv]{2,}");

    private BootstrapConfig() {
    }

    static void apply(String... args) {
        from(args).forEach(System::setProperty);
    }

    static Map<String, String> from(String... rawArgs) {
        var args = Arrays.stream(rawArgs).flatMap(BootstrapConfig::unbundle).toList();
        var properties = new HashMap<String, String>();
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            switch (arg) {
            case "--verbose", "-v" -> {
                properties.put(APP_LOG_LEVEL, "DEBUG");
                properties.put(CONSOLE_LOG_LEVEL, "DEBUG");
            }
            case "--quiet", "-q" -> properties.put(APP_LOG_LEVEL, "WARN");
            default -> {
                var separator = arg.indexOf('=');
                var name = separator < 0 ? arg : arg.substring(0, separator);
                var property = VALUE_OPTIONS.get(name);
                if (property == null) {
                    continue;
                }
                var value = separator >= 0 ? arg.substring(separator + 1) : i + 1 < args.size() ? args.get(++i) : "";
                if (!value.isBlank()) {
                    properties.put(property, value);
                }
            }
            }
        }
        return properties;
    }

    private static Stream<String> unbundle(String arg) {
        if (!BUNDLED_SWITCHES.matcher(arg).matches()) {
            return Stream.of(arg);
        }
        return arg.chars().skip(1).mapToObj(flag -> "-" + (char) flag);
    }
}
