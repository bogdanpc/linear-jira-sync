package bogdanpc.linearsync.cli.boundary;

import bogdanpc.linearsync.configuration.entity.ConfigurationException;
import bogdanpc.linearsync.configuration.control.SyncConfiguration;
import bogdanpc.linearsync.jira.boundary.Jira;
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
    Jira jiraService;

    @Inject
    IssueOperations linearService;

    @Parameters(index = "0", description = "Action to perform: sync, status, reset, test-connection", defaultValue = "")
    String action;

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
            io.quarkus.runtime.Quarkus.asyncExit(1);
            return 1;
        }

        LoggingConfig.configure(quiet, verbose);

        // Apply state directory override if provided
        if (stateDirectory != null && !stateDirectory.isBlank()) {
            System.setProperty("sync.storage.location", stateDirectory);
        }

        try {
            int exitCode = switch (action.toLowerCase()) {
                case "sync" -> performSync();
                case "status" -> showStatus();
                case "reset" -> resetState();
                case "test-connection" -> testConnection();
                case "list-issue-types" -> listIssueTypes();
                default -> unknownAction();
            };

            return exitCode;
        } finally {
            // Schedule async exit to allow logs to flush
            new Thread(() -> {
                try {
                    Thread.sleep(100); // Small delay to allow logs to flush
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                io.quarkus.runtime.Quarkus.asyncExit();
            }).start();
        }
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
                    : synchronizer.synchronize(teamKey, stateType != null ? stateType.getValue() : null, updatedAfterInstant, forceFullSync);

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
        if (dryRun) {
            Log.info("Linear → Jira Sync (dry-run)");
        } else {
            Log.info("Linear → Jira Sync");
        }

        if (issueIdentifier != null) {
            Log.debugf("  Issue: %s", issueIdentifier);
        } else {
            Log.debugf("  Team: %s | State: %s | Since: %s",
                    teamKey != null ? teamKey : "all",
                    stateType != null ? stateType.getValue() : "all",
                    formatSinceFilter(updatedAfterInstant));
        }
    }

    private String formatSinceFilter(Instant updatedAfter) {
        if (forceFullSync) return "full sync";
        if (updatedAfter == null) return "last sync";
        return formatTimestamp(updatedAfter);
    }

    private String formatTimestamp(Instant instant) {
        if (instant == null) return "-";
        var formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm")
                .withZone(java.time.ZoneId.systemDefault());
        return formatter.format(instant);
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

        var summary = new StringBuilder();
        if (result.createdCount > 0) summary.append(result.createdCount).append(" created");
        if (result.updatedCount > 0) {
            if (!summary.isEmpty()) summary.append(", ");
            summary.append(result.updatedCount).append(" updated");
        }
        if (result.skippedCount > 0) {
            if (!summary.isEmpty()) summary.append(", ");
            summary.append(result.skippedCount).append(" skipped");
        }
        if (!result.errors.isEmpty()) {
            if (!summary.isEmpty()) summary.append(", ");
            summary.append(result.errors.size()).append(" errors");
        }

        if (summary.isEmpty()) {
            Log.info("Done - no changes");
        } else {
            Log.infof("Done - %s", summary);
        }

        if (!result.errors.isEmpty()) {
            Log.error("Errors:");
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

    private Integer listIssueTypes() {
        System.out.println("Fetching available issue types from Jira project...");

        try {
            config.validate();
        } catch (ConfigurationException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return 1;
        }

        try {
            var issueTypes = jiraService.getProjectIssueTypes();

            if (issueTypes.isEmpty()) {
                System.out.println("No issue types found for the configured project.");
                return 0;
            }

            System.out.println();
            System.out.println("Available issue types:");
            System.out.println();
            for (var issueType : issueTypes) {
                var subtaskMarker = issueType.subtask() ? " [subtask]" : "";
                System.out.printf("  - %s%s%n", issueType.name(), subtaskMarker);
                if (issueType.description() != null && !issueType.description().isBlank()) {
                    System.out.printf("      %s%n", issueType.description());
                }
            }

            System.out.println();
            System.out.println("To configure, set environment variables:");
            System.out.println("  JIRA_ISSUE_TYPE=<name>      (for regular issues)");
            System.out.println("  JIRA_SUBTASK_TYPE=<name>    (for subtasks, use a [subtask] type)");

            return 0;
        } catch (Exception e) {
            System.err.println("Failed to fetch issue types: " + e.getMessage());
            return 1;
        }
    }

    private Integer unknownAction() {
        Log.error("Error: Unknown action '" + action + "'. Use: sync, status, reset, test-connection, or list-issue-types");
        return 1;
    }
}
