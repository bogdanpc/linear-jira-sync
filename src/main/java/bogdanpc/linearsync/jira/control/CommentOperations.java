package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.AdfNode;
import bogdanpc.linearsync.jira.entity.JiraComment;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
public class CommentOperations {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[[^]]*]\\([^)]+\\)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final JiraClient jiraClient;
    private final SearchOperations searchOperations;
    private final MarkupFormatter markupFormatter;

    CommentOperations(@RestClient JiraClient jiraClient, SearchOperations searchOperations, MarkupFormatter markupFormatter) {
        this.jiraClient = jiraClient;
        this.searchOperations = searchOperations;
        this.markupFormatter = markupFormatter;
    }

    void addComment(String jiraIssueKey, AdfNode body) {
        Log.debugf("Adding comment to Jira issue: %s", jiraIssueKey);
        jiraClient.addComment(jiraIssueKey, JiraComment.withBody(body));
    }

    public void syncComments(String jiraIssueKey, JiraIssueInput issueInput) {
        if (issueInput.comments() == null || issueInput.comments().isEmpty()) {
            Log.debugf("No comments to sync for source issue: %s", issueInput.sourceIdentifier());
            return;
        }

        Log.infof("Syncing %d comments from source issue %s to Jira issue %s", issueInput.comments().size(),
                issueInput.sourceIdentifier(), jiraIssueKey);

        var existingTexts = searchOperations.getComments(jiraIssueKey).stream()
                .map(comment -> normalize(comment.plainText()))
                .filter(text -> !text.isEmpty())
                .toList();

        issueInput.comments().stream()
                .map(comment -> new RenderedComment(comment, markupFormatter.formatComment(comment)))
                .filter(rendered -> !rendered.isAlreadyIn(existingTexts))
                .forEach(rendered -> addComment(jiraIssueKey, rendered.body()));
    }

    private static String normalize(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    /**
     * A comment counts as synced when Jira holds its rendered text. The raw-body and author+ISO-timestamp checks
     * recognise comments posted by earlier versions, which stored the body as plain text.
     */
    private record RenderedComment(JiraIssueInput.CommentInput source, AdfNode body) {

        boolean isAlreadyIn(List<String> existingTexts) {
            var rendered = normalize(body.plainText());
            var rawBody = source.body() == null ? "" : normalize(IMAGE_PATTERN.matcher(source.body()).replaceAll(""));
            var author = source.authorDisplayName() != null ? source.authorDisplayName() : source.authorName();
            var isoTimestamp = source.createdAt() == null ? null : source.createdAt().toString();

            return existingTexts.stream().anyMatch(existing -> existing.equals(rendered)
                    || (!rawBody.isEmpty() && existing.contains(rawBody))
                    || (author != null && isoTimestamp != null && existing.contains(author)
                            && existing.contains(isoTimestamp)));
        }
    }
}
