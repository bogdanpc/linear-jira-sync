package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraComment;
import bogdanpc.linearsync.jira.entity.JiraCreateRequest;
import bogdanpc.linearsync.jira.entity.JiraIssue;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
class IssueOperations {

    private final JiraClient jiraClient;

    private final FieldOperations fieldOperations;


    private final String jiraProjectKey;


    private final String defaultIssueType;

    IssueOperations(@RestClient JiraClient jiraClient, FieldOperations fieldOperations, @ConfigProperty(name = "jira.project.key") String jiraProjectKey, @ConfigProperty(name = "jira.issue.type", defaultValue = "Task") String defaultIssueType) {
        this.jiraClient = jiraClient;
        this.fieldOperations = fieldOperations;
        this.jiraProjectKey = jiraProjectKey;
        this.defaultIssueType = defaultIssueType;
    }

    JiraIssue createIssue(JiraIssueInput issueInput) {
        Log.infof("Creating Jira issue for source issue: %s", issueInput.sourceIdentifier());

        var request = buildCreateRequest(issueInput);

        try {
            var createdIssue = jiraClient.createIssue(request);
            Log.infof("Created Jira issue: %s", createdIssue.key());
            return createdIssue;
        } catch (Exception e) {
            Log.errorf(e, "Failed to create Jira issue for source issue: %s", issueInput.sourceIdentifier());
            throw new RuntimeException("Failed to create Jira issue", e);
        }
    }

    void updateIssue(String jiraIssueKey, JiraIssueInput issueInput) {
        Log.infof("Updating Jira issue: %s", jiraIssueKey);

        var request = buildUpdateRequest(issueInput);

        try {
            jiraClient.updateIssue(jiraIssueKey, request);
            Log.infof("Updated Jira issue: %s", jiraIssueKey);
        } catch (Exception e) {
            Log.errorf(e, "Failed to update Jira issue: %s", jiraIssueKey);
            throw new RuntimeException("Failed to update Jira issue", e);
        }
    }

    boolean testConnection() {
        try {
            var userInfo = jiraClient.getCurrentUser();
            Log.infof("Successfully connected to Jira. User: %s (%s)", userInfo.displayName(), userInfo.emailAddress());
            return true;
        } catch (Exception e) {
            Log.errorf(e, "Failed to connect to Jira API");
            return false;
        }
    }

    JiraComment.JiraUser getCurrentUserInfo() {
        try {
            var userInfo = jiraClient.getCurrentUser();
            return new JiraComment.JiraUser(userInfo.accountId(), userInfo.displayName(), userInfo.emailAddress(), true, null);
        } catch (Exception e) {
            Log.errorf(e, "Failed to get current user info");
            // Return a fallback user
            return new JiraComment.JiraUser("unknown", "System", "system@example.com", true, null);
        }
    }

    private JiraCreateRequest buildCreateRequest(JiraIssueInput issueInput) {
        var request = new JiraCreateRequest();
        request.fields = new JiraCreateRequest.Fields();
        request.fields.project = new JiraCreateRequest.Project(jiraProjectKey);
        request.fields.summary = String.format("[%s] %s", issueInput.sourceIdentifier(), issueInput.title());
        request.fields.description = new JiraCreateRequest.Description(issueInput.description());
        request.fields.issuetype = new JiraCreateRequest.IssueType(defaultIssueType);

        fieldOperations.mapCustomFields(issueInput, request);
        fieldOperations.mapPriorityIfEnabled(issueInput, request);
        fieldOperations.mapLabels(issueInput, request);

        return request;
    }

    private JiraCreateRequest buildUpdateRequest(JiraIssueInput issueInput) {
        var request = new JiraCreateRequest();
        request.fields = new JiraCreateRequest.Fields();
        request.fields.summary = String.format("[%s] %s", issueInput.sourceIdentifier(), issueInput.title());
        request.fields.description = new JiraCreateRequest.Description(issueInput.description());

        fieldOperations.mapPriorityIfEnabled(issueInput, request);
        fieldOperations.mapLabels(issueInput, request);

        return request;
    }
}