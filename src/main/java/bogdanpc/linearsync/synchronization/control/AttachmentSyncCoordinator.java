package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.jira.control.JiraOperations;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.synchronization.entity.SyncState;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AttachmentSyncCoordinator {

    private final JiraOperations jiraOperations;

    public AttachmentSyncCoordinator(JiraOperations jiraOperations) {
        this.jiraOperations = jiraOperations;
    }

    public void syncAttachments(String jiraIssueKey, JiraIssueInput issueInput, LinearIssue linearIssue, SyncState state) {
        if (issueInput.attachments() == null || issueInput.attachments().isEmpty()) {
            Log.debugf("No attachments to sync for Linear issue: %s", linearIssue.identifier());
            return;
        }

        Log.debugf("Filtering %d attachments for Linear issue %s based on sync state",
                  issueInput.attachments().size(), linearIssue.identifier());

        var unsyncedAttachments = issueInput.attachments().stream()
                .filter(attachment -> !state.isAttachmentAlreadySynced(linearIssue.id(), attachment.id()))
                .toList();

        if (unsyncedAttachments.isEmpty()) {
            Log.debugf("All attachments already synced for Linear issue: %s", linearIssue.identifier());
            return;
        }

        Log.infof("Syncing %d new attachments for Linear issue %s to Jira issue %s",
                 unsyncedAttachments.size(), linearIssue.identifier(), jiraIssueKey);

        var filteredIssueInput = new JiraIssueInput(
            issueInput.sourceId(),
            issueInput.sourceIdentifier(),
            issueInput.title(),
            issueInput.description(),
            issueInput.priority(),
            issueInput.state(),
            issueInput.assigneeEmail(),
            issueInput.assigneeDisplayName(),
            issueInput.creatorEmail(),
            issueInput.creatorDisplayName(),
            issueInput.teamName(),
            issueInput.teamKey(),
            issueInput.labels(),
            issueInput.comments(),
            unsyncedAttachments,
            issueInput.createdAt(),
            issueInput.updatedAt(),
            issueInput.sourceUrl()
        );

        try {
            jiraOperations.syncAttachments(jiraIssueKey, filteredIssueInput);

            for (var attachment : unsyncedAttachments) {
                state.markAttachmentSynced(linearIssue.id(), attachment.id());
                Log.debugf("Marked attachment %s as synced for Linear issue %s",
                          attachment.id(), linearIssue.identifier());
            }

        } catch (Exception e) {
            Log.errorf(e, "Failed to sync attachments for Linear issue %s to Jira issue %s",
                      linearIssue.identifier(), jiraIssueKey);
        }
    }
}