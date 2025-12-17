package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import bogdanpc.linearsync.linear.control.AttachmentDownloader;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.File;

@ApplicationScoped
public class AttachmentOperations {

    private enum SyncResult {
        SUCCESS, SKIPPED, FAILED
    }

    @ConfigProperty(name = "attachment.sync.enabled", defaultValue = "true")
    boolean attachmentSyncEnabled;

    private final JiraClient jiraClient;
    private final MarkupFormatter markupFormatter;
    private final CommentOperations commentOperations;
    private final IssueOperations issueOperations;
    private final AttachmentDownloader attachmentDownloader;

    AttachmentOperations(@RestClient JiraClient jiraClient,
                         MarkupFormatter markupFormatter,
                         CommentOperations commentOperations,
                         IssueOperations issueOperations,
                         AttachmentDownloader attachmentDownloader) {
        this.jiraClient = jiraClient;
        this.markupFormatter = markupFormatter;
        this.commentOperations = commentOperations;
        this.issueOperations = issueOperations;
        this.attachmentDownloader = attachmentDownloader;
    }

    public void syncAttachments(String jiraIssueKey, JiraIssueInput issueInput) {
        if (jiraIssueKey == null || jiraIssueKey.trim().isEmpty()) {
            Log.errorf("Invalid Jira issue key provided for attachment sync: %s", jiraIssueKey);
            return;
        }

        if (issueInput == null) {
            Log.errorf("Null issue input provided for attachment sync to Jira issue: %s", jiraIssueKey);
            return;
        }

        if (issueInput.attachments() == null || issueInput.attachments().isEmpty()) {
            Log.debugf("No attachments to sync for source issue: %s", issueInput.sourceIdentifier());
            return;
        }

        Log.infof("Processing %d attachments from source issue %s for Jira issue %s",
                issueInput.attachments().size(), issueInput.sourceIdentifier(), jiraIssueKey);

        if (!attachmentSyncEnabled) {
            Log.debugf("Attachment sync is disabled. Adding attachment info as comments for issue %s", jiraIssueKey);
            syncAttachmentsAsComments(jiraIssueKey, issueInput);
            return;
        }

        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        for (var attachmentInput : issueInput.attachments()) {
            var result = syncSingleAttachment(jiraIssueKey, attachmentInput);
            switch (result) {
                case SUCCESS -> successCount++;
                case SKIPPED -> skipCount++;
                case FAILED -> failCount++;
            }
        }

        Log.infof("Attachment sync summary for issue %s: %d successful, %d skipped, %d failed",
                jiraIssueKey, successCount, skipCount, failCount);
    }

    private SyncResult syncSingleAttachment(String jiraIssueKey, JiraIssueInput.AttachmentInput attachmentInput) {
        Log.debugf("Syncing attachment %s (%s) to Jira issue %s",
                attachmentInput.id(), attachmentInput.title(), jiraIssueKey);

        File tempFile = null;
        try {
            var downloadResult = attachmentDownloader.downloadAttachment(
                    attachmentInput.id(),
                    attachmentInput.url(),
                    attachmentInput.title()
            );

            if (downloadResult.isEmpty()) {
                Log.warnf("Failed to download attachment %s. Adding as comment instead.", attachmentInput.id());
                addAttachmentAsComment(jiraIssueKey, attachmentInput);
                return SyncResult.FAILED;
            }

            tempFile = downloadResult.get();

            var uploadedAttachments = jiraClient.addAttachment(jiraIssueKey, tempFile);

            if (uploadedAttachments != null && !uploadedAttachments.isEmpty()) {
                Log.infof("Successfully uploaded attachment %s (%s) to Jira issue %s",
                        attachmentInput.id(), attachmentInput.title(), jiraIssueKey);
                return SyncResult.SUCCESS;
            } else {
                Log.warnf("Upload returned empty result for attachment %s. Adding as comment instead.", attachmentInput.id());
                addAttachmentAsComment(jiraIssueKey, attachmentInput);
                return SyncResult.FAILED;
            }

        } catch (Exception e) {
            Log.errorf(e, "Failed to upload attachment %s to Jira issue %s. Adding as comment instead.",
                    attachmentInput.id(), jiraIssueKey);
            addAttachmentAsComment(jiraIssueKey, attachmentInput);
            return SyncResult.FAILED;
        } finally {
            if (tempFile != null) {
                attachmentDownloader.cleanupTempFile(tempFile);
            }
        }
    }

    private void syncAttachmentsAsComments(String jiraIssueKey, JiraIssueInput issueInput) {
        for (var attachmentInput : issueInput.attachments()) {
            try {
                addAttachmentAsComment(jiraIssueKey, attachmentInput);
            } catch (Exception e) {
                Log.errorf(e, "Failed to add attachment %s as comment for issue %s",
                        attachmentInput.id(), jiraIssueKey);
            }
        }
    }

    private void addAttachmentAsComment(String jiraIssueKey, JiraIssueInput.AttachmentInput attachmentInput) {
        try {
            var currentUser = issueOperations.getCurrentUserInfo();
            var attachmentInfo = markupFormatter.formatAttachmentForJira(attachmentInput);
            commentOperations.addComment(jiraIssueKey, attachmentInfo, currentUser);
            Log.debugf("Added attachment %s info as comment to Jira issue %s", attachmentInput.id(), jiraIssueKey);
        } catch (Exception e) {
            Log.errorf(e, "Failed to add attachment %s as comment to Jira issue %s",
                    attachmentInput.id(), jiraIssueKey);
        }
    }
}