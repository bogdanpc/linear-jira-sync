package bogdanpc.linearsync.synchronization.control;

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
        Log.infof("Starting single issue synchronization - Issue: %s, DryRun: %s", issueIdentifier, dryRun);

        var result = new SyncResult();
        result.startTime = Instant.now();

        try {
            var state = syncCoordinator.prepareSync();

            var linearIssue = linearService.getIssueByIdentifier(issueIdentifier).orElseThrow();

            Log.infof("Found Linear issue: %s - %s", linearIssue.identifier(), linearIssue.title());

            var issueResult = issueProcessor.processIssue(linearIssue, state, dryRun);
            result.addIssueResult(issueResult);

            syncCoordinator.completeSync(state, result.hasChanges());

            result.endTime = Instant.now();
            result.success = result.errors.isEmpty();

            Log.infof("Single issue synchronization completed - Created: %d, Updated: %d, Skipped: %d, Errors: %d",
                    result.createdCount, result.updatedCount, result.skippedCount, result.errors.size());

        } catch (Exception e) {
            Log.errorf(e, "Single issue synchronization failed");
            result.endTime = Instant.now();
            result.success = false;
            result.addError("Single issue synchronization failed: " + e.getMessage());
        }

        return result;
    }

    public SyncResult synchronize(String teamKey, String stateType, Instant updatedAfter, boolean forceFullSync) {
        var dryRun = syncCoordinator.isDryRun();
        Log.infof("Starting synchronization - Team: %s, State: %s, UpdatedAfter: %s, ForceFullSync: %s, DryRun: %s",
                teamKey, stateType, updatedAfter, forceFullSync, dryRun);

        var result = new SyncResult();
        result.startTime = Instant.now();

        try {
            var state = syncCoordinator.prepareSync();
            var effectiveUpdatedAfter = syncCoordinator.determineUpdatedAfter(state, updatedAfter, forceFullSync);

            var linearIssues = linearService.getIssues(teamKey, stateType, effectiveUpdatedAfter);
            Log.infof("Found %d Linear issues to process", linearIssues.size());

            linearIssues.stream()
                    .map(linearIssue -> issueProcessor.processIssue(linearIssue, state, dryRun))
                    .forEach(result::addIssueResult);

            syncCoordinator.completeSync(state, result.hasChanges());

            result.endTime = Instant.now();
            result.success = true;

            Log.infof("Synchronization completed - Created: %d, Updated: %d, Skipped: %d, Errors: %d",
                    result.createdCount, result.updatedCount, result.skippedCount, result.errors.size());

        } catch (Exception e) {
            Log.errorf(e, "Synchronization failed");
            result.endTime = Instant.now();
            result.success = false;
            result.addError("Synchronization failed: " + e.getMessage());
        }

        return result;
    }
}