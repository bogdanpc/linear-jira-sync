package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraComment;
import bogdanpc.linearsync.jira.entity.JiraIssue;
import bogdanpc.linearsync.jira.entity.JiraProject;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SearchOperations {

    /** /search/jql returns only issue IDs unless fields are requested. */
    private static final String SEARCH_FIELDS = "key,summary";
    private static final int SUMMARY_CANDIDATES = 20;

    private final JiraClient jiraClient;
    private final JiraConfig config;

    SearchOperations(@RestClient JiraClient jiraClient, JiraConfig config) {
        this.jiraClient = jiraClient;
        this.config = config;
    }

    /**
     * Recovers the link to a Jira issue created by an earlier run whose sync state was lost. The Linear ID custom field
     * is the reliable key; the "[ENG-123]" summary prefix covers setups without that field and issues created before
     * it was configured. Search failures propagate: treating them as "not found" would create a duplicate.
     */
    public Optional<JiraIssue> findExistingIssue(String linearId, String identifier) {
        return findByLinearIdField(linearId).or(() -> findBySummaryPrefix(identifier));
    }

    private Optional<JiraIssue> findByLinearIdField(String linearId) {
        if (!config.hasLinearIdField()) {
            return Optional.empty();
        }
        var jql = "cf[%s] = %s".formatted(config.linearIdFieldNumericId(), jqlString(linearId));
        return search(jql, 1).stream().findFirst();
    }

    /**
     * Text search is tokenized, so "ENG-12" also matches "[ENG-123]". The prefix check picks the exact match among
     * the candidates, oldest first in case duplicates already exist.
     */
    private Optional<JiraIssue> findBySummaryPrefix(String identifier) {
        var projectKey = config.projectKey().orElseThrow(() -> new IllegalStateException("Jira project key not configured"));
        // quoted twice: the inner quotes make Jira match the identifier as a phrase
        var jql = "project = %s AND summary ~ %s ORDER BY created ASC"
                .formatted(jqlString(projectKey), jqlString(jqlString(identifier)));
        var prefix = "[" + identifier + "]";

        return search(jql, SUMMARY_CANDIDATES).stream()
                .filter(issue -> issue.fields() != null && issue.fields().summary() != null)
                .filter(issue -> issue.fields().summary().startsWith(prefix))
                .findFirst();
    }

    private List<JiraIssue> search(String jql, int maxResults) {
        var issues = jiraClient.searchIssues(jql, SEARCH_FIELDS, null, maxResults).issues();
        return issues != null ? issues : List.of();
    }

    private static String jqlString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Fails instead of returning the comments fetched so far: a comment missing from the list counts as unsynced and
     * would be posted again.
     */
    List<JiraComment> getComments(String jiraIssueKey) {
        Log.debugf("Fetching comments for Jira issue: %s", jiraIssueKey);

        var allComments = new ArrayList<JiraComment>();
        var startAt = 0;
        var maxResults = 50;
        var hasMore = true;

        while (hasMore) {
            var response = jiraClient.getComments(jiraIssueKey, startAt, maxResults);

            if (response.comments() != null) {
                allComments.addAll(response.comments());
            }

            hasMore = response.comments() != null && response.comments().size() == maxResults && startAt + maxResults < response.total();
            startAt += maxResults;
        }

        Log.debugf("Fetched %d comments for Jira issue: %s", allComments.size(), jiraIssueKey);
        return allComments;
    }

    public List<JiraProject.IssueType> getProjectIssueTypes() {
        var projectKey = config.projectKey().orElseThrow(() -> new IllegalStateException("Jira project key not configured"));
        Log.debugf("Fetching issue types for project: %s", projectKey);
        var project = jiraClient.getProject(projectKey);
        return project.issueTypes() != null ? project.issueTypes() : List.of();
    }
}