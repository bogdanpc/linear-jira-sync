package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraComment;
import bogdanpc.linearsync.jira.entity.JiraCreateRequest;
import bogdanpc.linearsync.jira.entity.JiraIssue;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import bogdanpc.linearsync.jira.entity.JiraProject;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class IssueOperations {

    private final JiraClient jiraClient;
    private final IssueFieldMapper issueFieldMapper;
    private final JiraConfig config;

    private String cachedSubtaskType;

    IssueOperations(@RestClient JiraClient jiraClient, IssueFieldMapper issueFieldMapper, JiraConfig config) {
        this.jiraClient = jiraClient;
        this.issueFieldMapper = issueFieldMapper;
        this.config = config;
    }

    public JiraIssue createIssue(JiraIssueInput issueInput) {
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

    public void updateIssue(String jiraIssueKey, JiraIssueInput issueInput) {
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

    public boolean testConnection() {
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
        var projectKey = config.projectKey().orElseThrow(() -> new IllegalStateException("Jira project key not configured"));

        var request = new JiraCreateRequest();
        request.fields = new JiraCreateRequest.Fields();
        request.fields.project = new JiraCreateRequest.Project(projectKey);
        request.fields.summary = String.format("[%s] %s", issueInput.sourceIdentifier(), issueInput.title());
        request.fields.description = new JiraCreateRequest.Description(issueInput.description());

        // Determine issue type based on whether this is a subtask
        var isSubtask = issueInput.parentJiraKey() != null && !issueInput.parentJiraKey().isEmpty();
        var issueTypeName = isSubtask ? getSubtaskTypeName() : config.issueType();
        request.fields.issuetype = new JiraCreateRequest.IssueType(issueTypeName);

        // Set parent if this is a subtask
        if (isSubtask) {
            request.fields.parent = new JiraCreateRequest.Parent(issueInput.parentJiraKey());
            Log.debugf("Creating subtask with parent: %s, type: %s", issueInput.parentJiraKey(), issueTypeName);
        }

        issueFieldMapper.mapCustomFields(issueInput, request);
        issueFieldMapper.mapPriorityIfEnabled(issueInput, request);
        issueFieldMapper.mapLabels(issueInput, request);

        return request;
    }

    private String getSubtaskTypeName() {
        if (cachedSubtaskType != null) {
            return cachedSubtaskType;
        }

        var projectKey = config.projectKey().orElseThrow(() -> new IllegalStateException("Jira project key not configured"));
        var project = jiraClient.getProject(projectKey);
        if (project.issueTypes() != null) {
            cachedSubtaskType = project.issueTypes().stream()
                    .filter(JiraProject.IssueType::subtask)
                    .map(JiraProject.IssueType::name)
                    .findFirst()
                    .orElse("Subtask");
            Log.debugf("Detected subtask type: %s", cachedSubtaskType);
        } else {
            cachedSubtaskType = "Subtask";
        }

        return cachedSubtaskType;
    }

    private JiraCreateRequest buildUpdateRequest(JiraIssueInput issueInput) {
        var request = new JiraCreateRequest();
        request.fields = new JiraCreateRequest.Fields();
        request.fields.summary = String.format("[%s] %s", issueInput.sourceIdentifier(), issueInput.title());
        request.fields.description = new JiraCreateRequest.Description(issueInput.description());

        issueFieldMapper.mapPriorityIfEnabled(issueInput, request);
        issueFieldMapper.mapLabels(issueInput, request);

        return request;
    }
}
