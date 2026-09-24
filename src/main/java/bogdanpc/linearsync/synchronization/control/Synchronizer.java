package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.jira.control.JiraApiException;
import bogdanpc.linearsync.linear.control.IssueOperations;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.linear.entity.LinearStateType;
import bogdanpc.linearsync.synchronization.entity.SyncResult;
import bogdanpc.linearsync.synchronization.entity.SyncState;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

@ApplicationScoped
public class Synchronizer {

    private final IssueOperations linearService;
    private final SyncCoordinator syncCoordinator;
    private final IssueSync issueSync;

    public Synchronizer(IssueOperations linearService, SyncCoordinator syncCoordinator, IssueSync issueSync) {
        this.linearService = linearService;
        this.syncCoordinator = syncCoordinator;
        this.issueSync = issueSync;
    }

    public SyncResult synchronizeSingleIssue(String issueIdentifier, boolean dryRunRequested) {
        var dryRun = syncCoordinator.isDryRun(dryRunRequested);
        Log.debugf("Single issue sync - Issue: %s, DryRun: %s", issueIdentifier, dryRun);

        return linearService.getIssue(issueIdentifier)
                .map(issue -> run(dryRun, false, _ -> List.of(issue)))
                .orElseGet(() -> SyncResult.aborted(
                        "Linear issue '%s' not found. Please verify the issue identifier is correct."
                                .formatted(issueIdentifier),
                        Duration.ZERO));
    }

    public SyncResult synchronize(String teamKey, LinearStateType stateType, Instant updatedAfter,
            boolean forceFullSync, boolean dryRunRequested) {
        var dryRun = syncCoordinator.isDryRun(dryRunRequested);
        Log.debugf("Sync params - Team: %s, State: %s, UpdatedAfter: %s, ForceFullSync: %s, DryRun: %s",
                teamKey, stateType, updatedAfter, forceFullSync, dryRun);

        return run(dryRun, true, state -> {
            var effectiveUpdatedAfter = syncCoordinator.determineUpdatedAfter(state, updatedAfter, forceFullSync);
            var issues = linearService.getIssues(teamKey, stateType, effectiveUpdatedAfter);
            var children = issues.stream().filter(issue -> issue.parent() != null).count();
            Log.infof("Found %d issue%s (%d parent, %d children)", issues.size(), issues.size() == 1 ? "" : "s",
                    issues.size() - children, children);
            return issues;
        });
    }

    /**
     * @param advancesSyncTime whether a clean run moves the incremental-sync baseline. A single-issue run must not:
     *        other issues changed since the last full run would never be fetched.
     */
    private SyncResult run(boolean dryRun, boolean advancesSyncTime,
            Function<SyncState, List<LinearIssue>> issueSource) {
        var start = Instant.now();
        try {
            var state = syncCoordinator.prepareSync(dryRun);
            var issueResults = new SyncRun(issueSync, linearService, state, dryRun, issueSource.apply(state))
                    .syncAll();
            var result = new SyncResult(issueResults, List.of(), Duration.between(start, Instant.now()));
            if (advancesSyncTime) {
                syncCoordinator.advanceSyncTime(state, result, start);
            }
            syncCoordinator.completeSync(state, result.hasChanges(), dryRun);
            return result;
        } catch (JiraApiException e) {
            Log.warnf(e, "Jira API error during sync");
            return SyncResult.aborted("Jira API error (HTTP %d): %s".formatted(e.getStatusCode(), e.getMessage()),
                    Duration.between(start, Instant.now()));
        } catch (RuntimeException e) {
            Log.errorf(e, "Sync failed");
            return SyncResult.aborted("Sync failed: " + e.getMessage(), Duration.between(start, Instant.now()));
        }
    }
}
