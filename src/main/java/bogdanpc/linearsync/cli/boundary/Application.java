package bogdanpc.linearsync.cli.boundary;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import picocli.CommandLine;

@QuarkusMain
public class Application implements QuarkusApplication {

    @Inject
    CommandLine.IFactory factory;

    @Inject
    @TopCommand
    LinearJiraSyncCommand command;

    @Override
    public int run(String... args) throws Exception {
        return new CommandLine(command, factory).execute(args);
    }

    static void main(String... args) {
        bootstrapConfigLocations(args);
        Quarkus.run(Application.class, args);
    }

    /**
     * Parses --config from CLI args and sets smallrye.config.locations
     * before Quarkus initializes, so external config files are available
     * to the CDI and config subsystems at startup.
     */
    static void bootstrapConfigLocations(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                System.setProperty("smallrye.config.locations", args[i + 1]);
                return;
            }
            if (args[i].startsWith("--config=")) {
                System.setProperty("smallrye.config.locations", args[i].substring("--config=".length()));
                return;
            }
        }
    }
}
