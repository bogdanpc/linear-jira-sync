package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.jira.control.JiraOperations;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.synchronization.entity.SyncResult;
import bogdanpc.linearsync.synchronization.entity.SyncState;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class IssueProcessor {

    private final JiraOperations jiraService;
    private final IssueDataTransfer issueDataTransfer;
    private final AttachmentSyncCoordinator attachmentSyncCoordinator;

    public IssueProcessor(JiraOperations jiraService, IssueDataTransfer issueDataTransfer, AttachmentSyncCoordinator attachmentSyncCoordinator) {
        this.jiraService = jiraService;
        this.issueDataTransfer = issueDataTransfer;
        this.attachmentSyncCoordinator = attachmentSyncCoordinator;
    }

    public SyncResult.IssueResult processIssue(LinearIssue linearIssue, SyncState state, boolean dryRun) {
        Log.debugf("Processing Linear issue: %s", linearIssue.identifier());

        var syncedIssue = state.getSyncedIssue(linearIssue.id());

        if (syncedIssue != null) {
            return handleExistingIssue(linearIssue, syncedIssue, state, dryRun);
        } else {
            return handleNewIssue(linearIssue, state, dryRun);
        }
    }

    private SyncResult.IssueResult handleNewIssue(LinearIssue linearIssue, SyncState state, boolean dryRun) {
        var result = new SyncResult.IssueResult();
        result.linearIssueId = linearIssue.id();
        result.linearIdentifier = linearIssue.identifier();
        result.action = "create";

        Log.infof("Creating new Jira issue for Linear issue: %s", linearIssue.identifier());

        if (dryRun) {
            result.success = true;
            result.message = "Would create new Jira issue";
            Log.infof("[DRY RUN] Would create Jira issue for Linear issue: %s", linearIssue.identifier());
            return result;
        }

        try {
            var jiraIssueInput = issueDataTransfer.mapToJiraIssueInput(linearIssue);
            var createdIssue = jiraService.createIssue(jiraIssueInput);

            syncCommentsAndAttachments(createdIssue.key(), jiraIssueInput, linearIssue, state);

            state.addSyncedIssue(linearIssue.id(), createdIssue.key(), createdIssue.id());
            var syncedIssue = state.getSyncedIssue(linearIssue.id());
            syncedIssue.linearUpdatedAt = linearIssue.updatedAt();

            result.success = true;
            result.jiraIssueKey = createdIssue.key();
            result.message = "Created Jira issue: " + createdIssue.key();

            Log.infof("Successfully created Jira issue %s for Linear issue %s",
                    createdIssue.key(), linearIssue.identifier());

        } catch (Exception e) {
            result.success = false;
            result.message = "Failed to create Jira issue: " + e.getMessage();
            Log.errorf(e, "Failed to create Jira issue for Linear issue: %s", linearIssue.identifier());
        }

        return result;
    }

    private SyncResult.IssueResult handleExistingIssue(LinearIssue linearIssue, SyncState.SyncedIssue syncedIssue, SyncState state, boolean dryRun) {
        var result = new SyncResult.IssueResult();
        result.linearIssueId = linearIssue.id();
        result.linearIdentifier = linearIssue.identifier();
        result.jiraIssueKey = syncedIssue.jiraIssueKey;
        result.action = "update";

        if (!needsUpdate(linearIssue, syncedIssue)) {
            result.action = "skip";
            result.success = true;
            result.message = "No updates needed";
            Log.debugf("Skipping Linear issue %s - no updates needed", linearIssue.identifier());
            return result;
        }

        Log.infof("Updating Jira issue %s for Linear issue: %s", syncedIssue.jiraIssueKey, linearIssue.identifier());

        if (dryRun) {
            result.success = true;
            result.message = "Would update Jira issue";
            Log.infof("[DRY RUN] Would update Jira issue %s for Linear issue: %s",
                    syncedIssue.jiraIssueKey, linearIssue.identifier());
            return result;
        }

        try {
            var jiraIssueInput = issueDataTransfer.mapToJiraIssueInput(linearIssue);
            jiraService.updateIssue(syncedIssue.jiraIssueKey, jiraIssueInput);

            syncCommentsAndAttachments(syncedIssue.jiraIssueKey, jiraIssueInput, linearIssue, state);

            syncedIssue.linearUpdatedAt = linearIssue.updatedAt();
            syncedIssue.lastSyncTime = Instant.now();
            syncedIssue.status = SyncState.SyncStatus.SYNCED;

            result.success = true;
            result.message = "Updated Jira issue: " + syncedIssue.jiraIssueKey;

            Log.infof("Successfully updated Jira issue %s for Linear issue %s",
                    syncedIssue.jiraIssueKey, linearIssue.identifier());

        } catch (Exception e) {
            result.success = false;
            result.message = "Failed to update Jira issue: " + e.getMessage();
            syncedIssue.status = SyncState.SyncStatus.ERROR;
            Log.errorf(e, "Failed to update Jira issue %s for Linear issue: %s",
                    syncedIssue.jiraIssueKey, linearIssue.identifier());
        }

        return result;
    }

    private void syncCommentsAndAttachments(String jiraIssueKey, JiraIssueInput jiraIssueInput, LinearIssue linearIssue, SyncState state) {
        try {
            jiraService.syncComments(jiraIssueKey, jiraIssueInput);
            attachmentSyncCoordinator.syncAttachments(jiraIssueKey, jiraIssueInput, linearIssue, state);
        } catch (Exception e) {
            Log.warnf(e, "Failed to sync comments/attachments for issue %s, but issue operation was successful", jiraIssueKey);
        }
    }

    private boolean needsUpdate(LinearIssue linearIssue, SyncState.SyncedIssue syncedIssue) {
        if (linearIssue.updatedAt() != null && syncedIssue.linearUpdatedAt != null) {
            return linearIssue.updatedAt().isAfter(syncedIssue.linearUpdatedAt);
        }
        return syncedIssue.linearUpdatedAt == null;
    }
}