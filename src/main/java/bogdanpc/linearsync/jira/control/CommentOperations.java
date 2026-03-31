package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraComment;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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

    JiraComment addComment(String jiraIssueKey, JiraComment.JiraContent body) {
        Log.debugf("Adding comment to Jira issue: %s", jiraIssueKey);
        var comment = JiraComment.createFromContent(body, issueOperations.getCurrentUserInfo());
        return jiraClient.addComment(jiraIssueKey, comment);
    }

    public void syncComments(String jiraIssueKey, JiraIssueInput issueInput) {
        if (issueInput.comments() == null || issueInput.comments().isEmpty()) {
            Log.debugf("No comments to sync for source issue: %s", issueInput.sourceIdentifier());
            return;
        }

        Log.infof("Syncing %d comments from source issue %s to Jira issue %s", issueInput.comments().size(),
                issueInput.sourceIdentifier(), jiraIssueKey);

        var existingComments = searchOperations.getComments(jiraIssueKey);
        var existingCommentTexts = extractExistingCommentTexts(existingComments);

        issueInput.comments().stream()
                .filter(c -> !isCommentAlreadySynced(c, existingCommentTexts))
                .forEach(c -> addComment(jiraIssueKey, markupFormatter.formatCommentForJira(c)));
    }

    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[[^]]*]\\([^)]+\\)");

    /**
     * Detects already-synced comments by matching body text or author+timestamp
     * against existing Jira comment text. Handles both old (plain text) and
     * new (ADF) format comments.
     */
    private boolean isCommentAlreadySynced(JiraIssueInput.CommentInput comment, Set<String> existingTexts) {
        var bodyText = comment.body() != null
                ? IMAGE_PATTERN.matcher(comment.body()).replaceAll("").strip()
                : "";

        if (!bodyText.isEmpty()) {
            var normalizedBody = bodyText.replaceAll("\\s+", " ");
            for (var existing : existingTexts) {
                if (existing.contains(normalizedBody)) {
                    return true;
                }
            }
        }

        var author = comment.authorDisplayName() != null ? comment.authorDisplayName() : comment.authorName();
        if (author != null && comment.createdAt() != null) {
            var isoTimestamp = comment.createdAt().toString();
            for (var existing : existingTexts) {
                if (existing.contains(author) && existing.contains(isoTimestamp)) {
                    return true;
                }
            }
        }

        return false;
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
}