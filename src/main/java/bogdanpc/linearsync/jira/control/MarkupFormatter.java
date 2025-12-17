package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class MarkupFormatter {

    String formatCommentForJira(JiraIssueInput.CommentInput commentInput) {
        var formattedComment = new StringBuilder();
        formattedComment.append("h4. Comment from Source\n\n");

        if (commentInput.authorDisplayName() != null || commentInput.authorName() != null) {
            var userName = commentInput.authorDisplayName() != null ? commentInput.authorDisplayName() : commentInput.authorName();
            formattedComment.append("Author: ").append(userName).append("\n");
        }

        if (commentInput.createdAt() != null) {
            formattedComment.append("Created: ").append(commentInput.createdAt()).append("\n\n");
        }

        if (commentInput.body() != null && !commentInput.body().isEmpty()) {
            formattedComment.append(commentInput.body());
        }

        return formattedComment.toString();
    }

    String formatAttachmentForJira(JiraIssueInput.AttachmentInput attachmentInput) {
        var attachmentInfo = new StringBuilder();
        attachmentInfo.append("h4. Attachment from Source\n\n");

        if (attachmentInput.title() != null) {
            attachmentInfo.append("Title: ").append(attachmentInput.title()).append("\n");
        }

        if (attachmentInput.url() != null) {
            attachmentInfo.append("URL: [").append(attachmentInput.url()).append("|").append(attachmentInput.url()).append("]\n");
        }

        if (attachmentInput.sourceType() != null) {
            attachmentInfo.append("Type: ").append(attachmentInput.sourceType()).append("\n");
        }

        if (attachmentInput.creatorDisplayName() != null || attachmentInput.creatorName() != null) {
            var creatorName = attachmentInput.creatorDisplayName() != null ? attachmentInput.creatorDisplayName() : attachmentInput.creatorName();
            attachmentInfo.append("Created by: ").append(creatorName).append("\n");
        }

        if (attachmentInput.createdAt() != null) {
            attachmentInfo.append("Created: ").append(attachmentInput.createdAt()).append("\n");
        }

        return attachmentInfo.toString();
    }
}