package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import bogdanpc.linearsync.linear.control.AttachmentConfig;
import bogdanpc.linearsync.linear.control.AttachmentDownloader;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.File;

@ApplicationScoped
public class AttachmentOperations {

    private enum SyncResult {
        SUCCESS, SKIPPED, FAILED
    }

    private final JiraClient jiraClient;
    private final MarkupFormatter markupFormatter;
    private final CommentOperations commentOperations;
    private final AttachmentDownloader attachmentDownloader;
    private final AttachmentConfig attachmentConfig;

    AttachmentOperations(@RestClient JiraClient jiraClient,
                         MarkupFormatter markupFormatter,
                         CommentOperations commentOperations,
                         AttachmentDownloader attachmentDownloader,
                         AttachmentConfig attachmentConfig) {
        this.jiraClient = jiraClient;
        this.markupFormatter = markupFormatter;
        this.commentOperations = commentOperations;
        this.attachmentDownloader = attachmentDownloader;
        this.attachmentConfig = attachmentConfig;
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

        if (!attachmentConfig.syncEnabled()) {
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

        if (!isDownloadableAttachment(attachmentInput)) {
            Log.infof("Attachment %s is an external link (%s), adding as comment instead of downloading",
                    attachmentInput.id(), attachmentInput.sourceType());
            addAttachmentAsComment(jiraIssueKey, attachmentInput);
            return SyncResult.SKIPPED;
        }

        File tempFile = null;
        try {
            var downloadResult = attachmentDownloader.downloadAttachment(
                    attachmentInput.id(),
                    attachmentInput.url(),
                    attachmentInput.title());

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

    /**
     * Linear "attachments" include both actual file uploads (hosted on uploads.linear.app)
     * and external integration links (GitHub PRs, Jam recordings, etc.).
     * Only uploads.linear.app URLs are downloadable files.
     */
    private boolean isDownloadableAttachment(JiraIssueInput.AttachmentInput attachment) {
        return AttachmentDownloader.isLinearUpload(attachment.url());
    }

    private void addAttachmentAsComment(String jiraIssueKey, JiraIssueInput.AttachmentInput attachmentInput) {
        try {
            var attachmentBody = markupFormatter.formatAttachment(attachmentInput);
            commentOperations.addComment(jiraIssueKey, attachmentBody);
            Log.debugf("Added attachment %s info as comment to Jira issue %s", attachmentInput.id(), jiraIssueKey);
        } catch (JiraApiException e) {
            Log.errorf(e, "Failed to add attachment %s as comment to Jira issue %s",
                    attachmentInput.id(), jiraIssueKey);
        }
    }
}