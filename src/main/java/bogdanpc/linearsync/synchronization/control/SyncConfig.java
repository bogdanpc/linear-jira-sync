package bogdanpc.linearsync.synchronization.control;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

@ConfigMapping(prefix = "sync")
public interface SyncConfig {

    @WithName("dry-run")
    @WithDefault("false")
    boolean dryRun();

    Storage storage();

    interface Storage {

        Optional<String> location();

        @WithName("max-backups")
        @WithDefault("5")
        int maxBackups();
    }
}
