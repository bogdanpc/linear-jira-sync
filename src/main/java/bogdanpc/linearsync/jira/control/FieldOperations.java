package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraCreateRequest;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@ApplicationScoped
class FieldOperations {

    @ConfigProperty(name = "jira.custom-field.linear-id", defaultValue = "")
    Optional<String> linearIdCustomField;

    @ConfigProperty(name = "jira.enable-priority", defaultValue = "false")
    boolean enablePriority;

    void mapLabels(JiraIssueInput issueInput, JiraCreateRequest request) {
        if (issueInput.labels() != null && !issueInput.labels().isEmpty()) {
            request.fields.labels = issueInput.labels().stream().map(JiraIssueInput.LabelInput::name).toList();
        }
    }

    void mapPriorityIfEnabled(JiraIssueInput issueInput, JiraCreateRequest request) {
        if (enablePriority && issueInput.priority() != null) {
            request.fields.priority = new JiraCreateRequest.Priority(mapPriority(issueInput.priority()));
        }
    }

    void mapCustomFields(JiraIssueInput issueInput, JiraCreateRequest request) {
        if (linearIdCustomField.isPresent() && !linearIdCustomField.get().isEmpty()) {
            request.fields.setCustomField(linearIdCustomField.get(), issueInput.sourceId());
        }
    }

    private String mapPriority(Integer priority) {
        // Priority mapping: 0 = No priority, 1 = Urgent, 2 = High, 3 = Normal, 4 = Low
        // Jira priority: Highest, High, Medium, Low, Lowest
        return switch (priority) {
            case 1 -> "Highest";
            case 2 -> "High";
            case 4 -> "Low";
            default -> "Medium";
        };
    }
}