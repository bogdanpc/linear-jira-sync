package bogdanpc.linearsync.cli.boundary;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusMainIntegrationTest
class LinearJiraSyncCommandIT {

    @Test
    @Launch(value = "unknown-action", exitCode = 2)
    void unknownSubcommandIsRejected(LaunchResult result) {
        assertTrue(result.getErrorOutput().contains("is not a subcommand"),
                () -> "Expected an unknown subcommand message, got: " + result.getErrorOutput());
    }

    @Test
    @Launch(value = "--version")
    void versionPrintsSuccessfully(LaunchResult result) {
        assertTrue(result.getOutput().contains("1.0.0"),
                () -> "Expected version in output, got: " + result.getOutput());
    }

    @Test
    @Launch(value = { "sync", "--dry-run", "--team", "ENG" })
    void syncDryRunExercisesFullSyncPipeline(LaunchResult result) {
        assertFalse(result.getOutput().isBlank(),
                () -> "Expected sync output but got blank. stderr: " + result.getErrorOutput());
    }

    @Test
    @Launch(value = { "sync", "--help" })
    void helpDisplaysUsageInformation(LaunchResult result) {
        assertTrue(result.getOutput().contains("--dry-run"),
                () -> "Expected '--dry-run' in help output, got: " + result.getOutput());
    }

    @Test
    @Launch(value = { "test-connection" })
    void testConnection(LaunchResult result) {
        assertTrue(result.getOutput().contains("SUCCESS"),
                () -> "Expected 'SUCCESS' in output, got: " + result.getOutput());
    }
}
