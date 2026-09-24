package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Map;

@ApplicationScoped
public class IssueOperations {

    private final JiraClient jiraClient;
    private final IssueFieldMapper issueFieldMapper;
    private final MarkupFormatter markupFormatter;
    private final JiraConfig config;

    private String cachedSubtaskType;

    IssueOperations(@RestClient JiraClient jiraClient, IssueFieldMapper issueFieldMapper,
                    MarkupFormatter markupFormatter, JiraConfig config) {
        this.jiraClient = jiraClient;
        this.issueFieldMapper = issueFieldMapper;
        this.markupFormatter = markupFormatter;
        this.config = config;
    }

    public JiraIssue createIssue(JiraIssueInput issueInput) {
        Log.infof("Creating Jira issue for source issue: %s", issueInput.sourceIdentifier());

        var request = buildCreateRequest(issueInput);

        var createdIssue = jiraClient.createIssue(request);
        Log.infof("Created Jira issue: %s", createdIssue.key());
        return createdIssue;
    }


    public void updateIssue(String jiraIssueKey, JiraIssueInput issueInput) {
        Log.infof("Updating Jira issue: %s", jiraIssueKey);

        var request = buildUpdateRequest(issueInput);

        jiraClient.updateIssue(jiraIssueKey, request);
        Log.infof("Updated Jira issue: %s", jiraIssueKey);
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

    private JiraCreateRequest buildCreateRequest(JiraIssueInput issueInput) {
        var projectKey = config.projectKey()
                .orElseThrow(() -> new IllegalStateException("Jira project key not configured"));

        var isSubtask = issueInput.parentJiraKey() != null && !issueInput.parentJiraKey().isEmpty();
        var issueTypeName = isSubtask ? getSubtaskTypeName() : config.issueType();
        var parent = isSubtask ? new JiraCreateRequest.Parent(issueInput.parentJiraKey()) : null;
        if (isSubtask) {
            Log.debugf("Creating subtask with parent: %s, type: %s", issueInput.parentJiraKey(), issueTypeName);
        }

        return new JiraCreateRequest(new JiraCreateRequest.Fields(
                new JiraCreateRequest.Project(projectKey),
                summary(issueInput),
                markupFormatter.markdownToAdf(issueInput.description()),
                new JiraCreateRequest.IssueType(issueTypeName),
                issueFieldMapper.priority(issueInput),
                issueFieldMapper.labels(issueInput),
                parent,
                issueFieldMapper.customFields(issueInput)));
    }

    private static String summary(JiraIssueInput issueInput) {
        return "[%s] %s".formatted(issueInput.sourceIdentifier(), issueInput.title());
    }

    private String getSubtaskTypeName() {
        if (cachedSubtaskType != null) {
            return cachedSubtaskType;
        }

        var projectKey = config.projectKey()
                .orElseThrow(() -> new IllegalStateException("Jira project key not configured"));
        var project = jiraClient.getProject(projectKey);
        if (project.issueTypes() != null) {
            cachedSubtaskType = project.issueTypes()
                    .stream()
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
        return new JiraCreateRequest(new JiraCreateRequest.Fields(
                null,
                summary(issueInput),
                markupFormatter.markdownToAdf(issueInput.description()),
                null,
                issueFieldMapper.priority(issueInput),
                issueFieldMapper.labels(issueInput),
                null,
                Map.of()));
    }
}
