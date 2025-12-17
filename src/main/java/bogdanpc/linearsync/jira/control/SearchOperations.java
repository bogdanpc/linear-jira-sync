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

    private final JiraClient jiraClient;
    private final JiraConfig config;

    SearchOperations(@RestClient JiraClient jiraClient, JiraConfig config) {
        this.jiraClient = jiraClient;
        this.config = config;
    }

    public Optional<JiraIssue> findIssueBySourceId(String sourceIssueId) {
        var jql = buildSourceIdQuery(sourceIssueId);

        try {
            var response = jiraClient.searchIssues(jql, null, 1);

            if (response.issues() != null && !response.issues().isEmpty()) {
                return Optional.of(response.issues().getFirst());
            }

            return Optional.empty();
        } catch (Exception e) {
            Log.errorf(e, "Failed to search for Jira issue with source ID: %s", sourceIssueId);
            return Optional.empty();
        }
    }

    /**
     * Search for issues with summary starting with [identifier]
     */
    public Optional<JiraIssue> findIssueByIdentifierInSummary(String sourceIdentifier) {

        var projectKey = config.projectKey().orElseThrow(() -> new IllegalStateException("Jira project key not configured"));
        var jql = String.format("project = %s AND summary ~ \"[%s]*\"", projectKey, sourceIdentifier);

        try {
            var response = jiraClient.searchIssues(jql, null, 1);

            if (response.issues() != null && !response.issues().isEmpty()) {
                var issue = response.issues().getFirst();

                if (issue.fields() != null && issue.fields().summary() != null
                    && issue.fields().summary().startsWith("[" + sourceIdentifier + "]")) {
                    Log.debugf("Found existing Jira issue %s for Linear %s", issue.key(), sourceIdentifier);
                    return Optional.of(issue);
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            Log.debugf("Failed to search for Jira issue with identifier: %s - %s", sourceIdentifier, e.getMessage());
            return Optional.empty();
        }
    }

    public List<JiraIssue> getAllIssuesInProject() {
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
        if (!config.hasLinearIdField()) {
            throw new IllegalStateException("Cannot search by Linear ID - jira.custom-field.linear-id not configured");
        }
        return config.jqlByLinearId(sourceIssueId);
    }

    private String buildProjectQuery() {
        var projectKey = config.projectKey().orElseThrow(() -> new IllegalStateException("Jira project key not configured"));
        return String.format("project = %s", projectKey);
    }

    private List<JiraIssue> executePagedIssueSearch(String jql) {
        var allIssues = new ArrayList<JiraIssue>();
        String nextPageToken = null;
        var maxResults = 50;

        do {
            try {
                var response = jiraClient.searchIssues(jql, nextPageToken, maxResults);

                if (response.issues() != null) {
                    allIssues.addAll(response.issues());
                }

                nextPageToken = response.nextPageToken();

            } catch (Exception e) {
                Log.errorf(e, "Failed to fetch Jira issues with query: %s", jql);
                break;
            }
        } while (nextPageToken != null && !nextPageToken.isEmpty());

        Log.infof("Fetched %d Jira issues with query: %s", allIssues.size(), jql);
        return allIssues;
    }

    public List<JiraProject.IssueType> getProjectIssueTypes() {
        var projectKey = config.projectKey().orElseThrow(() -> new IllegalStateException("Jira project key not configured"));
        Log.debugf("Fetching issue types for project: %s", projectKey);
        var project = jiraClient.getProject(projectKey);
        return project.issueTypes() != null ? project.issueTypes() : List.of();
    }
}