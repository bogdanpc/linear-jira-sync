package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.jira.control.JiraConfig;
import bogdanpc.linearsync.jira.entity.JiraCreateRequest;
import bogdanpc.linearsync.jira.entity.JiraIssue;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class IssueTransformer {

    private final JiraConfig jiraConfig;

    IssueTransformer(JiraConfig jiraConfig) {
        this.jiraConfig = jiraConfig;
    }

    public static final String TO_DO = "To Do";
    private static final String IN_PROGRESS = "In Progress";
    private static final String DONE = "Done";

    /**
     * Maps Linear workflow state types to Jira status names.
     * Linear state types: triage, backlog, unstarted, started, completed, canceled
     */
    private static final Map<String, String> STATUS_MAPPING = Map.of(
            "triage", TO_DO,
            "backlog", TO_DO,
            "unstarted", TO_DO,
            "started", IN_PROGRESS,
            "completed", DONE,
            "canceled", DONE);

    // Priority mapping from Linear (0-4) to Jira
    private static final Map<Integer, String> PRIORITY_MAPPING = Map.of(
            0, "Medium", // No priority -> Medium
            1, "Highest", // Urgent -> Highest
            2, "High", // High -> High
            3, "Medium", // Normal -> Medium
            4, "Low" // Low -> Low
    );
    
    public JiraCreateRequest mapLinearToJiraCreateRequest(LinearIssue linearIssue, String jiraProjectKey, String issueType) {
        Log.debugf("Mapping Linear issue %s to Jira create request", linearIssue.identifier());
        
        var request = new JiraCreateRequest();
        request.fields = new JiraCreateRequest.Fields();
        
        // Basic fields
        request.fields.project = new JiraCreateRequest.Project(jiraProjectKey);
        request.fields.summary = formatSummary(linearIssue);
        request.fields.description = new JiraCreateRequest.Description(formatDescription(linearIssue));
        request.fields.issuetype = new JiraCreateRequest.IssueType(issueType);
        
        // Priority mapping (only if enabled)
        if (jiraConfig.priorityEnabled() && linearIssue.priority() != null) {
            var jiraPriority = PRIORITY_MAPPING.getOrDefault(linearIssue.priority(), "Medium");
            request.fields.priority = new JiraCreateRequest.Priority(jiraPriority);
        }
        
        // Labels mapping
        if (linearIssue.labels() != null && linearIssue.labels().nodes() != null) {
            request.fields.labels = linearIssue.labels().nodes().stream()
                    .map(label -> sanitizeLabel(label.name()))
                    .filter(label -> !label.isEmpty())
                    .toList();
        }

        // Store Linear issue ID in custom field if configured
        if (jiraConfig.hasLinearIdField()) {
            request.fields.setCustomField(jiraConfig.linearIdFieldName(), linearIssue.id());
        }
        
        return request;
    }
    
    public boolean shouldUpdateJiraIssue(LinearIssue linearIssue, JiraIssue jiraIssue) {
        // Check if Linear issue has been updated since last sync
        if (linearIssue.updatedAt() == null) {
            return false;
        }
        
        // Extract creation/update times from Jira (simplified check)
        // In a real implementation, you'd want to compare with the last sync time
        var currentSummary = formatSummary(linearIssue);
        var jiraSummary = jiraIssue.fields() != null ? jiraIssue.fields().summary() : null;
        
        return !currentSummary.equals(jiraSummary);
    }
    
    private String formatSummary(LinearIssue linearIssue) {
        return String.format("[%s] %s", linearIssue.identifier(), linearIssue.title());
    }
    
    private String formatDescription(LinearIssue linearIssue) {
        var description = new StringBuilder();
        
        if (linearIssue.description() != null && !linearIssue.description().isEmpty()) {
            description.append(linearIssue.description());
        }
        
        description.append("\n\n---\n");
        description.append("**Linear Details:**\n");
        description.append("- Issue ID: ").append(linearIssue.identifier()).append("\n");
        description.append("- Linear URL: ").append(linearIssue.url()).append("\n");
        
        if (linearIssue.team() != null) {
            description.append("- Team: ").append(linearIssue.team().name()).append("\n");
        }
        
        if (linearIssue.state() != null) {
            description.append("- Status: ").append(linearIssue.state().name()).append("\n");
        }
        
        if (linearIssue.creator() != null) {
            description.append("- Created by: ").append(linearIssue.creator().displayName()).append("\n");
        }
        
        if (linearIssue.assignee() != null) {
            description.append("- Assigned to: ").append(linearIssue.assignee().displayName()).append("\n");
        }
        
        return description.toString();
    }
    
    private String sanitizeLabel(String label) {
        if (label == null) {
            return "";
        }
        
        // Jira labels cannot contain spaces, special characters
        return label.replaceAll("[^a-zA-Z0-9_-]", "_")
                   .replaceAll("_{2,}", "_")
                   .toLowerCase();
    }
    
    public String mapLinearStatusToJira(String linearStateType) {
        return STATUS_MAPPING.getOrDefault(linearStateType.toLowerCase(), TO_DO);
    }
    
    public String mapLinearPriorityToJira(Integer linearPriority) {
        return PRIORITY_MAPPING.getOrDefault(linearPriority, "Medium");
    }
}