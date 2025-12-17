package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraComment;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashSet;
import java.util.List;

@ApplicationScoped
public class CommentOperations {


    private final JiraClient jiraClient;
    private final SearchOperations searchOperations;
    private final MarkupFormatter markupFormatter;
    private final IssueOperations issueOperations;

    CommentOperations(@RestClient JiraClient jiraClient, SearchOperations searchOperations, MarkupFormatter markupFormatter, IssueOperations issueOperations) {
        this.jiraClient = jiraClient;
        this.searchOperations = searchOperations;
        this.markupFormatter = markupFormatter;
        this.issueOperations = issueOperations;
    }

    JiraComment addComment(String jiraIssueKey, String commentText, JiraComment.JiraUser author) {
        Log.infof("Adding comment to Jira issue: %s", jiraIssueKey);

        try {
            var comment = JiraComment.createFromText(commentText, author);
            var createdComment = jiraClient.addComment(jiraIssueKey, comment);
            Log.debugf("Successfully added comment to Jira issue: %s", jiraIssueKey);
            return createdComment;
        } catch (Exception e) {
            Log.errorf(e, "Failed to add comment to Jira issue: %s", jiraIssueKey);
            throw new RuntimeException("Failed to add comment to Jira issue", e);
        }
    }

    public void syncComments(String jiraIssueKey, JiraIssueInput issueInput) {
        if (issueInput.comments() == null || issueInput.comments().isEmpty()) {
            Log.debugf("No comments to sync for source issue: %s", issueInput.sourceIdentifier());
            return;
        }

        Log.infof("Syncing %d comments from source issue %s to Jira issue %s", issueInput.comments().size(), issueInput.sourceIdentifier(), jiraIssueKey);

        var existingComments = searchOperations.getComments(jiraIssueKey);
        var existingCommentTexts = extractExistingCommentTexts(existingComments);
        var currentUser = issueOperations.getCurrentUserInfo();

        for (var commentInput : issueInput.comments()) {
            try {
                var commentText = markupFormatter.formatCommentForJira(commentInput);

                if (isCommentAlreadyExists(commentText, existingCommentTexts)) {
                    Log.debugf("Comment already exists, skipping: %s", commentInput.id());
                    continue;
                }

                addComment(jiraIssueKey, commentText, currentUser);
                existingCommentTexts.add(commentText.trim());

            } catch (Exception e) {
                Log.errorf(e, "Failed to sync comment %s from source issue %s", commentInput.id(), issueInput.sourceIdentifier());
            }
        }
    }

    private HashSet<String> extractExistingCommentTexts(List<JiraComment> existingComments) {
        var existingCommentTexts = new HashSet<String>();

        for (var existingComment : existingComments) {
            var plainText = existingComment.extractPlainText();
            if (!plainText.isEmpty()) {
                existingCommentTexts.add(plainText.trim());
            }
        }

        return existingCommentTexts;
    }

    private boolean isCommentAlreadyExists(String commentText, HashSet<String> existingCommentTexts) {
        return existingCommentTexts.contains(commentText.trim());
    }
}