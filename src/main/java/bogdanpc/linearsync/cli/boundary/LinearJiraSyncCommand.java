package bogdanpc.linearsync.cli.boundary;

import bogdanpc.linearsync.configuration.entity.ConfigurationException;
import bogdanpc.linearsync.configuration.entity.SyncConfiguration;
import bogdanpc.linearsync.jira.control.JiraOperations;
import bogdanpc.linearsync.linear.control.IssueOperations;
import bogdanpc.linearsync.linear.entity.LinearStateType;
import bogdanpc.linearsync.synchronization.control.Synchronizer;
import bogdanpc.linearsync.synchronization.entity.SyncResult;
import io.quarkus.logging.Log;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Callable;

@TopCommand
@Command(name = "linear-jira-sync", description = "Synchronize Linear issues to Jira", mixinStandardHelpOptions = true, version = "1.0.0", subcommands = {ListLinearIssuesCommand.class, ReadLinearIssueCommand.class})
public class LinearJiraSyncCommand implements Callable<Integer> {

    public static final String FAILED = "✗ FAILED";

    @Inject
    SyncConfiguration config;

    @Inject
    Synchronizer synchronizer;

    @Inject
    JiraOperations jiraService;

    @Inject
    IssueOperations linearService;

    @Parameters(index = "0", description = "Action to perform: sync, status, reset, test-connection", defaultValue = "sync")
    String action = "sync";

    @Option(names = {"-t", "--team"}, description = "Linear team key to sync (e.g., 'ENG')")
    String teamKey;

    @Option(names = {"-s", "--state"}, description = "Filter by Linear issue state type (e.g., 'started', 'completed')")
    LinearStateType stateType;

    @Option(names = {"-i", "--issue"}, description = "Sync only a specific Linear issue by identifier (e.g., 'ENG-123')")
    String issueIdentifier;

    @Option(names = {"-u", "--updated-after"}, description = "Only sync issues updated after this ISO datetime (e.g., '2024-01-01T00:00:00Z')")
    String updatedAfter;

    @Option(names = {"-f", "--force-full-sync"}, description = "Force full synchronization, ignoring last sync time")
    boolean forceFullSync = false;

    @Option(names = {"-d", "--dry-run"}, description = "Show what would be done without making actual changes")
    boolean dryRun = false;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose = false;

    @Option(names = {"-q", "--quiet"}, description = "Suppress non-error output")
    boolean quiet = false;

    @Option(names = {"--state-dir"}, description = "Custom directory for state file storage (overrides LINEARSYNC_STORAGE_LOCATION)")
    String stateDirectory;

    @Override
    public Integer call() {
        if (quiet && verbose) {
            Log.error("Error: Cannot use both --quiet and --verbose options");
            return 1;
        }

        LoggingConfigurer.configure(quiet, verbose);

        // Apply state directory override if provided
        if (stateDirectory != null && !stateDirectory.isBlank()) {
            System.setProperty("sync.storage.location", stateDirectory);
        }

        return switch (action.toLowerCase()) {
            case "sync" -> performSync();
            case "status" -> showStatus();
            case "reset" -> resetState();
            case "test-connection" -> testConnection();
            default -> unknownAction();
        };
    }


    private Integer performSync() {
        var configValid = validateConfiguration();
        if (configValid != null) {
            return configValid;
        }

        var updatedAfterInstant = parseUpdatedAfterTimestamp();
        if (updatedAfterInstant == null && updatedAfter != null && !updatedAfter.isEmpty()) {
            return 1;
        }

        printSyncHeader(updatedAfterInstant);

        try {
            synchronizer.setDryRun(dryRun);

            var result = issueIdentifier != null
                    ? synchronizer.synchronizeSingleIssue(issueIdentifier)
                    : synchronizer.synchronize(teamKey, stateType.getValue(), updatedAfterInstant, forceFullSync);

            printSyncResults(result);
            return result.success ? 0 : 1;

        } catch (Exception e) {
            Log.error("Error: Synchronization failed - " + e.getMessage());
            Log.debug("Stack trace: " + java.util.Arrays.toString(e.getStackTrace()));
            return 1;
        }
    }

    private Integer validateConfiguration() {
        try {
            config.validate();
            return null;
        } catch (ConfigurationException e) {
            Log.error("Configuration error: " + e.getMessage());
            Log.error("");
            Log.error("Please ensure all required configuration is set via:");
            Log.error("1. Environment variables (recommended for credentials)");
            Log.error("2. application-local.properties file (see application-local.properties.example)");
            Log.error("3. application.properties file");
            return 1;
        }
    }

    private Instant parseUpdatedAfterTimestamp() {
        if (updatedAfter == null || updatedAfter.isEmpty()) {
            return null;
        }

        try {
            return Instant.parse(updatedAfter);
        } catch (DateTimeParseException _) {
            Log.error("Error: Invalid datetime format for --updated-after. Use ISO format like '2024-01-01T00:00:00Z'");
            return null;
        }
    }

    private void printSyncHeader(Instant updatedAfterInstant) {
        Log.info("Starting Linear-Jira synchronization...");
        if (dryRun) {
            Log.info("DRY RUN MODE - No actual changes will be made");
        }

        Log.debug("Configuration:");
        if (issueIdentifier != null) {
            Log.debug("  Single Issue: " + issueIdentifier);
        } else {
            Log.debug("  Team: " + (teamKey != null ? teamKey : "all"));
            Log.debug("  State: " + (stateType != null ? stateType.getValue() : "all"));
            Log.debug("  Updated After: " + (updatedAfterInstant != null ? updatedAfterInstant : "last sync"));
            Log.debug("  Force Full Sync: " + forceFullSync);
        }
    }

    private void printSyncResults(SyncResult result) {
        Log.debug(result.getSummary());

        if (!result.issueResults.isEmpty()) {
            Log.debug("");
            Log.debug("Detailed Results:");
            for (var issueResult : result.issueResults) {
                Log.debug("  " + issueResult);
            }
        }

        Log.info("Sync completed: %d created, %d updated, %d skipped, %d errors".formatted(
                result.createdCount, result.updatedCount, result.skippedCount, result.errors.size()));

        if (!result.errors.isEmpty()) {
            Log.error("Errors occurred during synchronization:");
            for (var error : result.errors) {
                Log.error("  " + error);
            }
        }
    }

    private Integer showStatus() {
        Log.info("Sync status functionality not yet implemented");
        return 0;
    }

    private Integer resetState() {
        Log.info("Reset state functionality not yet implemented");
        return 0;
    }

    private Integer testConnection() {
        Log.info("Testing API connections...");

        try {
            config.validate();
        } catch (ConfigurationException e) {
            Log.error("Configuration error: " + e.getMessage());
            Log.error("");
            Log.error("Please ensure all required configuration is set via:");
            Log.error("1. Environment variables (recommended for credentials)");
            Log.error("2. application-local.properties file (see application-local.properties.example)");
            Log.error("3. application.properties file");
            return 1;
        }

        var allConnected = true;

        Log.info("Testing Linear API connection... ");

        var linearConnected = false;
        try {
            linearConnected = linearService.testConnection();
            Log.info(linearConnected ? "✓ SUCCESS" : FAILED);
        } catch (Exception e) {
            Log.error(FAILED);
            Log.error("  Error: " + e.getMessage());
        }

        Log.info("Testing Jira API connection... ");

        var jiraConnected = false;
        try {
            jiraConnected = jiraService.testConnection();
            Log.info(jiraConnected ? "✓ SUCCESS" : FAILED);
        } catch (Exception e) {
            Log.info(FAILED);
            Log.debug("  Error: " + e.getMessage());
        }

        allConnected = linearConnected && jiraConnected;

        Log.info("");
        if (allConnected) {
            Log.info("✓ All API connections are working correctly");
        } else {
            Log.info("✗ One or more API connections failed");
            Log.info("");
            Log.info("Please check:");
            if (!linearConnected) {
                Log.info("- LINEAR_API_TOKEN environment variable is set and valid");
                Log.info("- Linear API URL is accessible: https://api.linear.app/graphql");
            }
            if (!jiraConnected) {
                Log.info("- JIRA_API_TOKEN and JIRA_USERNAME environment variables are set and valid");
                Log.info("- JIRA_API_URL environment variable is set to your Jira instance URL");
            }
        }

        return allConnected ? 0 : 1;
    }

    private Integer unknownAction() {
        Log.error("Error: Unknown action '" + action + "'. Use: sync, status, reset, or test-connection");
        return 1;
    }
}
