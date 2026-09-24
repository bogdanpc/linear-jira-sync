package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraCreateRequest;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@ApplicationScoped
class IssueFieldMapper {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final JiraConfig config;

    IssueFieldMapper(JiraConfig config) {
        this.config = config;
    }

    List<String> labels(JiraIssueInput issueInput) {
        if (issueInput.labels() == null) {
            return List.of();
        }
        return issueInput.labels().stream()
                .map(JiraIssueInput.LabelInput::name)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> WHITESPACE.matcher(name.strip()).replaceAll("_"))
                .distinct()
                .toList();
    }

    JiraCreateRequest.Priority priority(JiraIssueInput issueInput) {
        if (!config.priorityEnabled() || issueInput.priority() == null) {
            return null;
        }
        return new JiraCreateRequest.Priority(mapPriority(issueInput.priority()));
    }

    Map<String, Object> customFields(JiraIssueInput issueInput) {
        if (!config.hasLinearIdField() || issueInput.sourceId() == null) {
            return Map.of();
        }
        return Map.of(config.linearIdFieldName(), issueInput.sourceId());
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
