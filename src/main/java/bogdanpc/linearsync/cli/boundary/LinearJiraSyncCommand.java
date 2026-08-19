package bogdanpc.linearsync.cli.boundary;

import bogdanpc.linearsync.configuration.control.SyncConfiguration;
import bogdanpc.linearsync.configuration.entity.ConfigurationException;
import bogdanpc.linearsync.jira.boundary.Jira;
import bogdanpc.linearsync.linear.control.IssueOperations;
import bogdanpc.linearsync.linear.entity.LinearStateType;
import bogdanpc.linearsync.synchronization.control.Synchronizer;
import bogdanpc.linearsync.synchronization.entity.SyncResult;
import io.quarkus.logging.Log;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.Quarkus;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;

@TopCommand
@Command(name = "linear-jira-sync", description = "Synchronize Linear issues to Jira", mixinStandardHelpOptions = true, version = "1.0.0", subcommands = {ListLinearIssuesCommand.class, ReadLinearIssueCommand.class})
public class LinearJiraSyncCommand implements Callable<Integer> {

    private static final String FAILED = "✗ FAILED";
    private static final String ACTIONS = "sync, test-connection, list-issue-types";
    private static final int LOG_FLUSH_MILLIS = 100;

    private static final DateTimeFormatter SINCE_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm")
            .withZone(ZoneId.systemDefault());

    @Inject
    SyncConfiguration config;

    @Inject
    Synchronizer synchronizer;

    @Inject
    Jira jiraService;

    @Inject
    IssueOperations linearService;

    @Parameters(index = "0", description = "Action to perform: " + ACTIONS, defaultValue = "")
    String action;

    @Mixin
    OutputOptions output = new OutputOptions();

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

    @Option(names = {"--state-dir"}, description = "Custom directory for state file storage (overrides LINEARSYNC_STORAGE_LOCATION)")
    String stateDirectory;

    @SuppressWarnings("unused") // parsed pre-boot in Application.bootstrapConfigLocations; declared here so picocli accepts the flag
    @Option(names = {"--config"}, description = "Path to external configuration file (e.g., ~/.linear-jira-sync/config.properties)")
    String configFile;

    @Option(names = {"--jira-project-key"}, description = "Target Jira project key (overrides JIRA_PROJECT_KEY)")
    String jiraProjectKey;

    @Override
    public Integer call() {
        if (!output.applyLogLevel()) {
            Quarkus.asyncExit(1);
            return 1;
        }

        overrideProperty("sync.storage.location", stateDirectory);
        overrideProperty("jira.project.key", jiraProjectKey);

        try {
            return switch (action.toLowerCase()) {
                case "sync" -> performSync();
                case "test-connection" -> testConnection();
                case "list-issue-types" -> listIssueTypes();
                default -> unknownAction();
            };
        } finally {
            scheduleExit();
        }
    }

    private static void overrideProperty(String key, String value) {
        if (value != null && !value.isBlank()) {
            System.setProperty(key, value);
        }
    }

    /**
     * Quarkus tears the runtime down immediately on exit, which truncates buffered
     * console output, so the shutdown is pushed to the end of the log flush window.
     */
    private static void scheduleExit() {
        new Thread(() -> {
            try {
                Thread.sleep(LOG_FLUSH_MILLIS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            Quarkus.asyncExit();
        }).start();
    }

    private boolean configurationIsValid() {
        try {
            config.validate();
            return true;
        } catch (ConfigurationException e) {
            Log.error("Configuration error: " + e.getMessage());
            Log.error("");
            Log.error("Please ensure all required configuration is set via:");
            Log.error("1. Environment variables (recommended for credentials)");
            Log.error("2. application-local.properties file (see application-local.properties.example)");
            Log.error("3. application.properties file");
            return false;
        }
    }

    private Integer performSync() {
        if (!configurationIsValid()) {
            return 1;
        }

        Instant updatedAfterInstant;
        try {
            updatedAfterInstant = parseUpdatedAfter();
        } catch (DateTimeParseException _) {
            Log.error("Error: Invalid datetime format for --updated-after. Use ISO format like '2024-01-01T00:00:00Z'");
            return 1;
        }

        printSyncHeader(updatedAfterInstant);

        try {
            synchronizer.setDryRun(dryRun);

            var result = issueIdentifier != null
                    ? synchronizer.synchronizeSingleIssue(issueIdentifier)
                    : synchronizer.synchronize(teamKey, stateType != null ? stateType.getValue() : null, updatedAfterInstant, forceFullSync);

            printSyncResults(result);
            return result.success ? 0 : 1;

        } catch (RuntimeException e) {
            Log.error("Error: Synchronization failed - " + e.getMessage());
            Log.debug("Stack trace: " + Arrays.toString(e.getStackTrace()));
            return 1;
        }
    }

    private Instant parseUpdatedAfter() {
        return updatedAfter == null || updatedAfter.isBlank() ? null : Instant.parse(updatedAfter);
    }

    private void printSyncHeader(Instant updatedAfterInstant) {
        Log.info(dryRun ? "Linear → Jira Sync (dry-run)" : "Linear → Jira Sync");

        if (issueIdentifier != null) {
            Log.debugf("  Issue: %s", issueIdentifier);
            return;
        }

        Log.debugf("  Team: %s | State: %s | Since: %s",
                teamKey != null ? teamKey : "all",
                stateType != null ? stateType.getValue() : "all",
                formatSinceFilter(updatedAfterInstant));
    }

    private String formatSinceFilter(Instant updatedAfterInstant) {
        if (forceFullSync) return "full sync";
        if (updatedAfterInstant == null) return "last sync";
        return SINCE_FORMAT.format(updatedAfterInstant);
    }

    private void printSyncResults(SyncResult result) {
        Log.debug(result.getSummary());

        if (!result.issueResults.isEmpty()) {
            Log.debug("");
            Log.debug("Detailed Results:");
            result.issueResults.forEach(issueResult -> Log.debug("  " + issueResult));
        }

        var counts = new ArrayList<String>();
        if (result.createdCount > 0) counts.add(result.createdCount + " created");
        if (result.updatedCount > 0) counts.add(result.updatedCount + " updated");
        if (result.skippedCount > 0) counts.add(result.skippedCount + " skipped");
        if (!result.errors.isEmpty()) counts.add(result.errors.size() + " errors");

        Log.info(counts.isEmpty() ? "Done - no changes" : "Done - " + String.join(", ", counts));

        if (!result.errors.isEmpty()) {
            Log.error("Errors:");
            result.errors.forEach(error -> Log.error("  " + error));
        }
    }

    private Integer testConnection() {
        if (!configurationIsValid()) {
            return 1;
        }

        Log.info("Testing API connections...");

        var linearConnected = testConnection("Linear", linearService::testConnection);
        var jiraConnected = testConnection("Jira", jiraService::testConnection);

        Log.info("");
        if (linearConnected && jiraConnected) {
            Log.info("✓ All API connections are working correctly");
            return 0;
        }

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
        return 1;
    }

    private static boolean testConnection(String name, Callable<Boolean> probe) {
        Log.infof("Testing %s API connection... ", name);
        try {
            var connected = Boolean.TRUE.equals(probe.call());
            Log.info(connected ? "✓ SUCCESS" : FAILED);
            return connected;
        } catch (Exception e) {
            Log.error(FAILED);
            Log.error("  Error: " + e.getMessage());
            return false;
        }
    }

    private Integer listIssueTypes() {
        if (!configurationIsValid()) {
            return 1;
        }

        Log.info("Fetching available issue types from Jira project...");

        try {
            var issueTypes = jiraService.getProjectIssueTypes();

            if (issueTypes.isEmpty()) {
                Log.info("No issue types found for the configured project.");
                return 0;
            }

            Log.info("Available issue types:");
            for (var issueType : issueTypes) {
                Log.infof("  - %s%s", issueType.name(), issueType.subtask() ? " [subtask]" : "");
                if (issueType.description() != null && !issueType.description().isBlank()) {
                    Log.infof("      %s", issueType.description());
                }
            }

            Log.info("To configure, set environment variables:");
            Log.info("  JIRA_ISSUE_TYPE=<name>      (for regular issues)");
            Log.info("  JIRA_SUBTASK_TYPE=<name>    (for subtasks, use a [subtask] type)");

            return 0;
        } catch (RuntimeException e) {
            Log.error("Failed to fetch issue types: " + e.getMessage());
            return 1;
        }
    }

    private Integer unknownAction() {
        Log.error("Error: Unknown action '" + action + "'. Use: " + ACTIONS);
        return 1;
    }
}
