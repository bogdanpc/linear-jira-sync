package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.jira.boundary.Jira;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.synchronization.entity.IssueResult;
import bogdanpc.linearsync.synchronization.entity.SyncAction;
import bogdanpc.linearsync.synchronization.entity.SyncState;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Brings a single Jira issue in line with its Linear source. Comments, attachments and status are best effort: their
 * failure is logged but does not fail the issue, because the next update retries them.
 */
@ApplicationScoped
public class IssueSync {

    private static final int TITLE_LOG_LENGTH = 50;

    private final Jira jira;
    private final IssueDataTransfer issueDataTransfer;
    private final AttachmentSyncCoordinator attachmentSyncCoordinator;

    public IssueSync(Jira jira, IssueDataTransfer issueDataTransfer,
            AttachmentSyncCoordinator attachmentSyncCoordinator) {
        this.jira = jira;
        this.issueDataTransfer = issueDataTransfer;
        this.attachmentSyncCoordinator = attachmentSyncCoordinator;
    }

    IssueResult sync(LinearIssue linearIssue, String parentJiraKey, SyncState state, boolean dryRun) {
        return state.syncedIssue(linearIssue.id())
                .map(synced -> update(linearIssue, synced, parentJiraKey, state, dryRun))
                .orElseGet(() -> create(linearIssue, parentJiraKey, state, dryRun));
    }

    private IssueResult create(LinearIssue linearIssue, String parentJiraKey, SyncState state, boolean dryRun) {
        var identifier = linearIssue.identifier();
        var title = truncate(linearIssue.title());

        try {
            var existing = jira.findExistingIssue(linearIssue.id(), identifier);
            if (existing.isPresent()) {
                var jiraIssue = existing.get();
                state.recordSync(linearIssue.id(), jiraIssue.key(), jiraIssue.id(), linearIssue.updatedAt());
                Log.infof("  ↔ %s → %s  %s  (recovered from Jira)", identifier, jiraIssue.key(), title);
                return IssueResult.succeeded(identifier, jiraIssue.key(), SyncAction.RECOVER,
                        "Recovered existing Jira issue: " + jiraIssue.key());
            }

            if (dryRun) {
                var issueType = parentJiraKey != null ? "subtask" : "task";
                Log.infof("  + %s  %s  (dry-run)", identifier, title);
                return IssueResult.succeeded(identifier, "[WOULD-CREATE]", SyncAction.CREATE,
                        "Would create new Jira " + issueType);
            }

            var input = issueDataTransfer.mapToJiraIssueInput(linearIssue, parentJiraKey);
            var created = jira.createIssue(input);
            // recorded before the attachments, which are tracked per synced issue
            state.recordSync(linearIssue.id(), created.key(), created.id(), linearIssue.updatedAt());
            syncDetails(created.key(), input, linearIssue, state);

            Log.infof("  + %s → %s  %s", identifier, created.key(), title);
            return IssueResult.succeeded(identifier, created.key(), SyncAction.CREATE,
                    "Created Jira issue: " + created.key());
        } catch (RuntimeException e) {
            Log.errorf("  ✗ %s  %s - %s", identifier, title, e.getMessage());
            return IssueResult.failed(identifier, null, SyncAction.CREATE,
                    "Failed to create Jira issue for %s: %s".formatted(identifier, e.getMessage()));
        }
    }

    private IssueResult update(LinearIssue linearIssue, SyncState.SyncedIssue synced, String parentJiraKey,
            SyncState state, boolean dryRun) {
        var identifier = linearIssue.identifier();
        var jiraKey = synced.jiraIssueKey();
        var title = truncate(linearIssue.title());

        if (!synced.isOutdatedBy(linearIssue.updatedAt())) {
            Log.infof("  · %s → %s  %s  (skipped: unchanged)", identifier, jiraKey, title);
            return IssueResult.succeeded(identifier, jiraKey, SyncAction.SKIP, "No updates needed");
        }

        if (dryRun) {
            Log.infof("  ~ %s → %s  %s  (dry-run)", identifier, jiraKey, title);
            return IssueResult.succeeded(identifier, jiraKey, SyncAction.UPDATE, "Would update Jira issue");
        }

        try {
            var input = issueDataTransfer.mapToJiraIssueInput(linearIssue, parentJiraKey);
            jira.updateIssue(jiraKey, input);
            syncDetails(jiraKey, input, linearIssue, state);
            state.recordSync(linearIssue.id(), jiraKey, synced.jiraIssueId(), linearIssue.updatedAt());

            Log.infof("  ~ %s → %s  %s", identifier, jiraKey, title);
            return IssueResult.succeeded(identifier, jiraKey, SyncAction.UPDATE, "Updated Jira issue: " + jiraKey);
        } catch (RuntimeException e) {
            Log.errorf("  ✗ %s → %s  %s - %s", identifier, jiraKey, title, e.getMessage());
            return IssueResult.failed(identifier, jiraKey, SyncAction.UPDATE,
                    "Failed to update Jira issue %s: %s".formatted(jiraKey, e.getMessage()));
        }
    }

    private void syncDetails(String jiraKey, JiraIssueInput input, LinearIssue linearIssue, SyncState state) {
        try {
            jira.syncComments(jiraKey, input);
            attachmentSyncCoordinator.syncAttachments(jiraKey, input, linearIssue, state);
        } catch (RuntimeException e) {
            Log.warnf(e, "Failed to sync comments/attachments for issue %s, but issue operation was successful",
                    jiraKey);
        }
        try {
            jira.transitionIssueStatus(jiraKey, input.status());
        } catch (RuntimeException e) {
            Log.warnf(e, "Failed to sync status for issue %s, but issue operation was successful", jiraKey);
        }
    }

    private static String truncate(String title) {
        if (title == null) {
            return "";
        }
        return title.length() <= TITLE_LOG_LENGTH ? title : title.substring(0, TITLE_LOG_LENGTH - 3) + "...";
    }
}
