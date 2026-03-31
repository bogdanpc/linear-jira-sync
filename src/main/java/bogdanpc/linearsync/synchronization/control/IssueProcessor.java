package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.jira.boundary.Jira;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import bogdanpc.linearsync.linear.control.IssueOperations;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.synchronization.entity.SyncResult;
import bogdanpc.linearsync.synchronization.entity.SyncState;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class IssueProcessor {

    private final Jira jiraService;
    private final IssueDataTransfer issueDataTransfer;
    private final AttachmentSyncCoordinator attachmentSyncCoordinator;
    private final IssueOperations linearService;

    public IssueProcessor(Jira jiraService, IssueDataTransfer issueDataTransfer,
                          AttachmentSyncCoordinator attachmentSyncCoordinator, IssueOperations linearService) {
        this.jiraService = jiraService;
        this.issueDataTransfer = issueDataTransfer;
        this.attachmentSyncCoordinator = attachmentSyncCoordinator;
        this.linearService = linearService;
    }

    public SyncResult.IssueResult processIssue(LinearIssue linearIssue, SyncState state, boolean dryRun) {
        var childCount = linearIssue.children() != null && linearIssue.children().getNodes() != null
                ? linearIssue.children().getNodes().size()
                : 0;
        var hasParent = linearIssue.parent() != null;

        // Check if this issue has a parent and ensure parent is synced first
        String parentJiraKey = null;
        if (hasParent) {
            parentJiraKey = ensureParentIsSynced(linearIssue.parent().id(), state, dryRun);
            Log.debugf("Issue %s parent Jira key: %s", linearIssue.identifier(), parentJiraKey);
        }

        var syncedIssue = state.getSyncedIssue(linearIssue.id());

        SyncResult.IssueResult result;
        if (syncedIssue != null) {
            result = handleExistingIssue(linearIssue, syncedIssue, state, dryRun, parentJiraKey);
        } else {
            result = handleNewIssue(linearIssue, state, dryRun, parentJiraKey);
        }

        // After processing this issue, process its children if any
        if (result.success && linearIssue.children() != null && linearIssue.children().getNodes() != null) {
            syncChildren(linearIssue,  state, dryRun, childCount);
        }

        return result;
    }

    private String ensureParentIsSynced(String parentLinearId, SyncState state, boolean dryRun) {
        var parentSyncedIssue = state.getSyncedIssue(parentLinearId);
        if (parentSyncedIssue != null) {
            return parentSyncedIssue.jiraIssueKey;
        }

        Log.debugf("Syncing parent issue first: %s", parentLinearId);
        var parentIssue = linearService.getIssueById(parentLinearId);
        if (parentIssue.isPresent()) {
            var parentResult = processIssue(parentIssue.get(), state, dryRun);
            return parentResult.jiraIssueKey;
        } else {
            Log.warnf("Parent issue %s not found", parentLinearId);
            return null;
        }
    }

    private void syncChildren(LinearIssue parentIssue, SyncState state, boolean dryRun,
                              int childCount) {
        var children = parentIssue.children().getNodes();
        if (children.isEmpty()) {
            return;
        }

        Log.debugf("Syncing %d subtask(s) for %s", childCount, parentIssue.identifier());

        for (var child : children) {
            try {
                var childIssue = linearService.getIssueById(child.id());
                if (childIssue.isPresent()) {
                    processIssue(childIssue.get(), state, dryRun);
                } else {
                    Log.warnf("Child issue %s not found", child.identifier());
                }
            } catch (Exception e) {
                Log.errorf(e, "Failed to sync child %s", child.identifier());
            }
        }
    }

    private SyncResult.IssueResult handleNewIssue(LinearIssue linearIssue, SyncState state, boolean dryRun,
                                                  String parentJiraKey) {
        var result = new SyncResult.IssueResult();
        result.linearIssueId = linearIssue.id();
        result.linearIdentifier = linearIssue.identifier();

        var issueType = parentJiraKey != null ? "subtask" : "task";
        var title = truncateTitle(linearIssue.title(), 50);

        // Check if issue already exists in Jira (safety net for lost sync state)
        var existingIssue = jiraService.findIssueByIdentifier(linearIssue.identifier());
        if (existingIssue.isPresent()) {
            var jiraIssue = existingIssue.get();
            Log.infof("  ↔ %s → %s  %s  (recovered from Jira)", linearIssue.identifier(), jiraIssue.key(), title);

            // Restore sync state
            state.addSyncedIssue(linearIssue.id(), jiraIssue.key(), jiraIssue.id());
            var syncedIssue = state.getSyncedIssue(linearIssue.id());
            syncedIssue.linearUpdatedAt = linearIssue.updatedAt();

            result.action = "recover";
            result.success = true;
            result.jiraIssueKey = jiraIssue.key();
            result.message = "Recovered existing Jira issue: " + jiraIssue.key();
            return result;
        }

        result.action = "create";

        if (dryRun) {
            result.success = true;
            result.jiraIssueKey = "[WOULD-CREATE]";
            result.message = "Would create new Jira " + issueType;
            Log.infof("  + %s  %s  (dry-run)", linearIssue.identifier(), title);
            return result;
        }

        try {
            var jiraIssueInput = issueDataTransfer.mapToJiraIssueInput(linearIssue, parentJiraKey);
            var createdIssue = jiraService.createIssue(jiraIssueInput);

            syncCommentsAndAttachments(createdIssue.key(), jiraIssueInput, linearIssue, state);
            syncStatus(createdIssue.key(), jiraIssueInput);

            state.addSyncedIssue(linearIssue.id(), createdIssue.key(), createdIssue.id());
            var syncedIssue = state.getSyncedIssue(linearIssue.id());
            syncedIssue.linearUpdatedAt = linearIssue.updatedAt();

            result.success = true;
            result.jiraIssueKey = createdIssue.key();
            result.message = "Created Jira issue: " + createdIssue.key();

            Log.infof("  + %s → %s  %s", linearIssue.identifier(), createdIssue.key(), title);

        } catch (Exception e) {
            result.success = false;
            result.message = "Failed to create Jira issue: " + e.getMessage();
            Log.errorf("  ✗ %s  %s - %s", linearIssue.identifier(), title, e.getMessage());
        }

        return result;
    }

    private String truncateTitle(String title, int maxLength) {
        if (title == null)
            return "";
        if (title.length() <= maxLength)
            return title;
        return title.substring(0, maxLength - 3) + "...";
    }

    private SyncResult.IssueResult handleExistingIssue(LinearIssue linearIssue, SyncState.SyncedIssue syncedIssue,
                                                       SyncState state, boolean dryRun, String parentJiraKey) {
        var result = new SyncResult.IssueResult();
        result.linearIssueId = linearIssue.id();
        result.linearIdentifier = linearIssue.identifier();
        result.jiraIssueKey = syncedIssue.jiraIssueKey;
        result.action = "update";

        var title = truncateTitle(linearIssue.title(), 50);

        if (!needsUpdate(linearIssue, syncedIssue)) {
            result.action = "skip";
            result.success = true;
            result.message = "No updates needed";
            Log.infof("  · %s → %s  %s  (skipped: unchanged)", linearIssue.identifier(), syncedIssue.jiraIssueKey,
                    title);
            return result;
        }

        if (dryRun) {
            result.success = true;
            result.message = "Would update Jira issue";
            Log.infof("  ~ %s → %s  %s  (dry-run)", linearIssue.identifier(), syncedIssue.jiraIssueKey, title);
            return result;
        }

        try {
            var jiraIssueInput = issueDataTransfer.mapToJiraIssueInput(linearIssue, parentJiraKey);
            jiraService.updateIssue(syncedIssue.jiraIssueKey, jiraIssueInput);

            syncCommentsAndAttachments(syncedIssue.jiraIssueKey, jiraIssueInput, linearIssue, state);
            syncStatus(syncedIssue.jiraIssueKey, jiraIssueInput);

            syncedIssue.linearUpdatedAt = linearIssue.updatedAt();
            syncedIssue.lastSyncTime = Instant.now();
            syncedIssue.status = SyncState.SyncStatus.SYNCED;

            result.success = true;
            result.message = "Updated Jira issue: " + syncedIssue.jiraIssueKey;

            Log.infof("  ~ %s → %s  %s", linearIssue.identifier(), syncedIssue.jiraIssueKey, title);

        } catch (Exception e) {
            result.success = false;
            result.message = "Failed to update Jira issue: " + e.getMessage();
            syncedIssue.status = SyncState.SyncStatus.ERROR;
            Log.errorf("  ✗ %s → %s  %s - %s", linearIssue.identifier(), syncedIssue.jiraIssueKey, title,
                    e.getMessage());
        }

        return result;
    }

    private void syncCommentsAndAttachments(String jiraIssueKey, JiraIssueInput jiraIssueInput, LinearIssue linearIssue,
                                            SyncState state) {
        try {
            jiraService.syncComments(jiraIssueKey, jiraIssueInput);
            attachmentSyncCoordinator.syncAttachments(jiraIssueKey, jiraIssueInput, linearIssue, state);
        } catch (Exception e) {
            Log.warnf(e, "Failed to sync comments/attachments for issue %s, but issue operation was successful",
                    jiraIssueKey);
        }
    }

    private void syncStatus(String jiraIssueKey, JiraIssueInput jiraIssueInput) {
        try {
            jiraService.transitionIssueStatus(jiraIssueKey, jiraIssueInput.stateType());
        } catch (Exception e) {
            Log.warnf(e, "Failed to sync status for issue %s, but issue operation was successful", jiraIssueKey);
        }
    }

    private boolean needsUpdate(LinearIssue linearIssue, SyncState.SyncedIssue syncedIssue) {
        if (linearIssue.updatedAt() != null && syncedIssue.linearUpdatedAt != null) {
            return linearIssue.updatedAt().isAfter(syncedIssue.linearUpdatedAt);
        }
        return syncedIssue.linearUpdatedAt == null;
    }
}
