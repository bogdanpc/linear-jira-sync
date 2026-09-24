package bogdanpc.linearsync.cli.boundary;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BootstrapConfigTest {

    @Test
    void translatesConfigFlagsInBothSyntaxes() {
        var properties = BootstrapConfig.from("sync", "--state-dir", "/tmp/state", "--jira-project-key=TST", "--config",
                "/tmp/app.properties");

        assertEquals(Map.of("sync.storage.location", "/tmp/state", "jira.project.key", "TST",
                "smallrye.config.locations", "/tmp/app.properties"), properties);
    }

    @Test
    void verboseEnablesApplicationDebugOutput() {
        var properties = BootstrapConfig.from("sync", "-dv");

        assertEquals(Map.of(BootstrapConfig.APP_LOG_LEVEL, "DEBUG", "quarkus.log.console.level", "DEBUG"), properties);
    }

    @Test
    void quietRaisesApplicationLogLevel() {
        assertEquals(Map.of(BootstrapConfig.APP_LOG_LEVEL, "WARN"), BootstrapConfig.from("sync", "--quiet"));
    }
}
