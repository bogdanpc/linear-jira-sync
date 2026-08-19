package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.jira.control.JiraApiException;
import bogdanpc.linearsync.linear.control.IssueOperations;
import bogdanpc.linearsync.synchronization.entity.SyncResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class Synchronizer {

    private final IssueOperations linearService;
    private final SyncCoordinator syncCoordinator;
    private final IssueProcessor issueProcessor;

    public Synchronizer(IssueOperations linearService, SyncCoordinator syncCoordinator, IssueProcessor issueProcessor) {
        this.linearService = linearService;
        this.syncCoordinator = syncCoordinator;
        this.issueProcessor = issueProcessor;
    }

    public void setDryRun(boolean dryRun) {
        syncCoordinator.setDryRun(dryRun);
    }

    public SyncResult synchronizeSingleIssue(String issueIdentifier) {
        var dryRun = syncCoordinator.isDryRun();
        Log.debugf("Single issue sync - Issue: %s, DryRun: %s", issueIdentifier, dryRun);

        var result = new SyncResult();
        result.startTime = Instant.now();

        try {
            var state = syncCoordinator.prepareSync();

            var linearIssue = linearService.getIssue(issueIdentifier);
            if (linearIssue.isEmpty()) {
                result.endTime = Instant.now();
                result.success = false;
                result.addError("Linear issue '%s' not found. Please verify the issue identifier is correct."
                        .formatted(issueIdentifier));
                return result;
            }

            var issueResult = issueProcessor.processIssue(linearIssue.get(), state, dryRun);
            result.addIssueResult(issueResult);

            syncCoordinator.completeSync(state, result.hasChanges());

            result.success = result.errors.isEmpty();

        } catch (JiraApiException e) {
            Log.warnf(e, "Jira API error during sync");
            result.success = false;
            result.addError("Jira API error (HTTP %d): %s".formatted(e.getStatusCode(), e.getMessage()));
        } catch (RuntimeException e) {
            Log.warnf(e, "Sync failed");
            result.success = false;
            result.addError("Sync failed: " + e.getMessage());
        }

        result.endTime = Instant.now();
        return result;
    }

    public SyncResult synchronize(String teamKey, String stateType, Instant updatedAfter, boolean forceFullSync) {
        var dryRun = syncCoordinator.isDryRun();
        Log.debugf("Sync params - Team: %s, State: %s, UpdatedAfter: %s, ForceFullSync: %s, DryRun: %s",
                teamKey, stateType, updatedAfter, forceFullSync, dryRun);

        var result = new SyncResult();
        result.startTime = Instant.now();

        try {
            var state = syncCoordinator.prepareSync();
            var effectiveUpdatedAfter = syncCoordinator.determineUpdatedAfter(state, updatedAfter, forceFullSync);

            var linearIssues = linearService.getIssues(teamKey, stateType, effectiveUpdatedAfter);

            var parentIssues = linearIssues.stream()
                    .filter(issue -> issue.parent() == null)
                    .toList();

            Log.infof("Found %d issue%s (%d parent, %d children)",
                    linearIssues.size(), linearIssues.size() == 1 ? "" : "s",
                    parentIssues.size(), linearIssues.size() - parentIssues.size());

            parentIssues.stream()
                    .map(linearIssue -> issueProcessor.processIssue(linearIssue, state, dryRun))
                    .forEach(result::addIssueResult);

            syncCoordinator.completeSync(state, result.hasChanges());

            result.success = true;
        } catch (JiraApiException e) {
            Log.warnf(e, "Jira API error during sync");
            result.success = false;
            result.addError("Jira API error (HTTP %d): %s".formatted(e.getStatusCode(), e.getMessage()));
        } catch (RuntimeException e) {
            Log.errorf(e, "Sync failed");
            result.success = false;
            result.addError("Sync failed: " + e.getMessage());
        }

        result.endTime = Instant.now();
        return result;
    }
}