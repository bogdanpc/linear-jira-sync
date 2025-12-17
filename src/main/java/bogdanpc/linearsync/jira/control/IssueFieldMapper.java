package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraCreateRequest;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class IssueFieldMapper {

    private final JiraConfig config;

    IssueFieldMapper(JiraConfig config) {
        this.config = config;
    }

    void mapLabels(JiraIssueInput issueInput, JiraCreateRequest request) {
        if (issueInput.labels() != null && !issueInput.labels().isEmpty()) {
            request.fields.labels = issueInput.labels().stream().map(JiraIssueInput.LabelInput::name).toList();
        }
    }

    void mapPriorityIfEnabled(JiraIssueInput issueInput, JiraCreateRequest request) {
        if (config.priorityEnabled() && issueInput.priority() != null) {
            request.fields.priority = new JiraCreateRequest.Priority(mapPriority(issueInput.priority()));
        }
    }

    void mapCustomFields(JiraIssueInput issueInput, JiraCreateRequest request) {
        if (config.hasLinearIdField()) {
            request.fields.setCustomField(config.linearIdFieldName(), issueInput.sourceId());
        }
    }

    /**
     * Priority mapping: 0 = No priority, 1 = Urgent, 2 = High, 3 = Normal, 4 = Low
     * Jira priority: Highest, High, Medium, Low, Lowest
     */
    private String mapPriority(Integer priority) {

        return switch (priority) {
            case 1 -> "Highest";
            case 2 -> "High";
            case 4 -> "Low";
            default -> "Medium";
        };
    }
}