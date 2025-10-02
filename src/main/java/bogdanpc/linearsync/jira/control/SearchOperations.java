package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraComment;
import bogdanpc.linearsync.jira.entity.JiraIssue;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
class SearchOperations {

    private final JiraClient jiraClient;
    private final String jiraProjectKey;
    private final Optional<String> linearIdCustomField;

    SearchOperations(@RestClient JiraClient jiraClient, @ConfigProperty(name = "jira.project.key") String jiraProjectKey, @ConfigProperty(name = "jira.custom-field.linear-id", defaultValue = "") Optional<String> linearIdCustomField) {
        this.jiraClient = jiraClient;
        this.jiraProjectKey = jiraProjectKey;
        this.linearIdCustomField = linearIdCustomField;
    }

    Optional<JiraIssue> findIssueBySourceId(String sourceIssueId) {
        var jql = buildSourceIdQuery(sourceIssueId);

        try {
            var response = jiraClient.searchIssues(jql, 0, 1);

            if (response.issues() != null && !response.issues().isEmpty()) {
                return Optional.of(response.issues().getFirst());
            }

            return Optional.empty();
        } catch (Exception e) {
            Log.errorf(e, "Failed to search for Jira issue with source ID: %s", sourceIssueId);
            return Optional.empty();
        }
    }

    List<JiraIssue> getAllIssuesInProject() {
        var jql = buildProjectQuery();
        return executePagedIssueSearch(jql);
    }

    List<JiraComment> getComments(String jiraIssueKey) {
        Log.debugf("Fetching comments for Jira issue: %s", jiraIssueKey);

        var allComments = new ArrayList<JiraComment>();
        var startAt = 0;
        var maxResults = 50;
        var hasMore = true;

        while (hasMore) {
            try {
                var response = jiraClient.getComments(jiraIssueKey, startAt, maxResults);

                if (response.comments() != null) {
                    allComments.addAll(response.comments());
                }

                hasMore = response.comments() != null && response.comments().size() == maxResults && startAt + maxResults < response.total();
                startAt += maxResults;

            } catch (Exception e) {
                Log.errorf(e, "Failed to fetch comments for Jira issue: %s", jiraIssueKey);
                break;
            }
        }

        Log.debugf("Fetched %d comments for Jira issue: %s", allComments.size(), jiraIssueKey);
        return allComments;
    }

    private String buildSourceIdQuery(String sourceIssueId) {
        if (linearIdCustomField.isPresent() && !linearIdCustomField.get().isEmpty()) {
            var fieldId = linearIdCustomField.get();
            // Handle both formats: "customfield_10000" and "10000"
            if (fieldId.startsWith("customfield_")) {
                fieldId = fieldId.substring("customfield_".length());
            }
            return String.format("cf[%s] = \"%s\"", fieldId, sourceIssueId);
        }
        // Fallback to hardcoded field - should be configurable
        return String.format("cf[10000] = \"%s\"", sourceIssueId);
    }

    private String buildProjectQuery() {
        return String.format("project = %s", jiraProjectKey);
    }

    private List<JiraIssue> executePagedIssueSearch(String jql) {
        var allIssues = new ArrayList<JiraIssue>();
        var startAt = 0;
        var maxResults = 50;
        var hasMore = true;

        while (hasMore) {
            try {
                var response = jiraClient.searchIssues(jql, startAt, maxResults);

                if (response.issues() != null) {
                    allIssues.addAll(response.issues());
                }

                hasMore = response.issues() != null && response.issues().size() == maxResults && startAt + maxResults < response.total();
                startAt += maxResults;

            } catch (Exception e) {
                Log.errorf(e, "Failed to fetch Jira issues with query: %s", jql);
                break;
            }
        }

        Log.infof("Fetched %d Jira issues with query: %s", allIssues.size(), jql);
        return allIssues;
    }
}