package bogdanpc.linearsync.cli.boundary;

import bogdanpc.linearsync.configuration.control.SyncConfiguration;
import bogdanpc.linearsync.linear.entity.LinearStateType;
import bogdanpc.linearsync.synchronization.control.Synchronizer;
import bogdanpc.linearsync.synchronization.entity.SyncAction;
import bogdanpc.linearsync.synchronization.entity.SyncResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Dependent
@CommandDefinition(name = "sync", description = "Synchronize Linear issues to Jira", generateHelp = true)
public class SyncCommand implements Command<CommandInvocation> {

    private static final DateTimeFormatter SINCE_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm")
            .withZone(ZoneId.systemDefault());

    @Inject
    SyncConfiguration config;

    @Inject
    Synchronizer synchronizer;

    @Mixin
    OutputOptions output;

    @Option(name = "team", shortName = 't', description = "Linear team key to sync (e.g., 'ENG')")
    String teamKey;

    @Option(name = "state", shortName = 's', converter = StateTypeConverter.class, description = "Filter by Linear issue state type (e.g., 'started', 'completed')")
    LinearStateType stateType;

    @Option(name = "issue", shortName = 'i', description = "Sync only a specific Linear issue by identifier (e.g., 'ENG-123')")
    String issueIdentifier;

    @Option(name = "updated-after", shortName = 'u', description = "Only sync issues updated after this ISO datetime (e.g., '2024-01-01T00:00:00Z')")
    String updatedAfter;

    @Option(name = "force-full-sync", shortName = 'f', hasValue = false, description = "Force full synchronization, ignoring last sync time")
    boolean forceFullSync;

    @Option(name = "dry-run", shortName = 'd', hasValue = false, description = "Show what would be done without making actual changes")
    boolean dryRun;

    @SuppressWarnings("unused") // parsed pre-boot in BootstrapConfig; declared here so aesh accepts the option
    @Option(name = "state-dir", description = "Custom directory for state file storage (overrides LINEARSYNC_STORAGE_LOCATION)")
    String stateDirectory;

    @SuppressWarnings("unused") // parsed pre-boot in BootstrapConfig; declared here so aesh accepts the option
    @Option(name = "jira-project-key", description = "Target Jira project key (overrides JIRA_PROJECT_KEY)")
    String jiraProjectKey;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        if (output.conflicting()) {
            return CommandResult.FAILURE;
        }

        if (!ConfigurationCheck.isValid(config)) {
            return CommandResult.FAILURE;
        }

        Instant updatedAfterInstant;
        try {
            updatedAfterInstant = parseUpdatedAfter();
        } catch (DateTimeParseException _) {
            Log.error("Error: Invalid datetime format for --updated-after. Use ISO format like '2024-01-01T00:00:00Z'");
            return CommandResult.FAILURE;
        }

        printSyncHeader(updatedAfterInstant);

        try {
            var result = issueIdentifier != null ? synchronizer.synchronizeSingleIssue(issueIdentifier, dryRun)
                    : synchronizer.synchronize(teamKey, stateType, updatedAfterInstant, forceFullSync, dryRun);

            printSyncResults(result);
            return result.success() ? CommandResult.SUCCESS : CommandResult.FAILURE;

        } catch (RuntimeException e) {
            Log.error("Error: Synchronization failed - " + e.getMessage());
            Log.debug("Stack trace: " + Arrays.toString(e.getStackTrace()));
            return CommandResult.FAILURE;
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

        Log.debugf("  Team: %s | State: %s | Since: %s", teamKey != null ? teamKey : "all",
                stateType != null ? stateType : "all", formatSinceFilter(updatedAfterInstant));
    }

    private String formatSinceFilter(Instant updatedAfterInstant) {
        if (forceFullSync)
            return "full sync";
        if (updatedAfterInstant == null)
            return "last sync";
        return SINCE_FORMAT.format(updatedAfterInstant);
    }

    private void printSyncResults(SyncResult result) {
        Log.debugf("Sync finished in %dms", result.duration().toMillis());
        result.issueResults().forEach(issueResult -> Log.debug("  " + issueResult));

        var counts = new ArrayList<String>();
        addCount(counts, result.count(SyncAction.CREATE), "created");
        addCount(counts, result.count(SyncAction.UPDATE), "updated");
        addCount(counts, result.count(SyncAction.RECOVER), "recovered");
        addCount(counts, result.count(SyncAction.SKIP), "skipped");
        var errors = result.allErrors();
        addCount(counts, errors.size(), "errors");

        Log.info(counts.isEmpty() ? "Done - no changes" : "Done - " + String.join(", ", counts));

        if (!errors.isEmpty()) {
            Log.error("Errors:");
            errors.forEach(error -> Log.error("  " + error));
        }
    }

    private static void addCount(List<String> counts, long count, String label) {
        if (count > 0) {
            counts.add(count + " " + label);
        }
    }
}
