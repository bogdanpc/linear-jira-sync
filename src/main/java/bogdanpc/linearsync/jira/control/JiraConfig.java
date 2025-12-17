package bogdanpc.linearsync.jira.control;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Centralized configuration for Jira integration.
 * Properties can be set via application.properties, system properties,
 * or environment variables (which take precedence).
 */
@ConfigMapping(prefix = "jira")
public interface JiraConfig {

    @WithName("api.url")
    Optional<String> apiUrl();

    @WithName("api.username")
    Optional<String> username();

    @WithName("api.token")
    Optional<String> apiToken();

    @WithName("project.key")
    Optional<String> projectKey();

    @WithName("issue.type")
    @WithDefault("Task")
    String issueType();

    @WithName("custom-field.linear-id")
    Optional<String> linearIdField();

    @WithName("enable-priority")
    @WithDefault("false")
    boolean priorityEnabled();

    @WithName("enable-status-sync")
    @WithDefault("true")
    boolean statusSyncEnabled();

    /**
     * Checks if the Linear ID custom field is configured.
     */
    default boolean hasLinearIdField() {
        return linearIdField().filter(s -> !s.isBlank()).isPresent();
    }

    /**
     * Gets the Linear ID field name, normalized to customfield_XXXXX format.
     * @throws IllegalStateException if not configured
     */
    default String linearIdFieldName() {
        var field = linearIdField()
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException("Linear ID custom field not configured"));
        return field.startsWith("customfield_") ? field : "customfield_" + field;
    }

    /**
     * Gets the numeric ID portion of the Linear ID field for JQL queries.
     * @throws IllegalStateException if not configured
     */
    default String linearIdFieldNumericId() {
        var fieldName = linearIdFieldName();
        return fieldName.replace("customfield_", "");
    }

    /**
     * Builds a JQL clause to search by Linear issue ID.
     */
    default String jqlByLinearId(String linearId) {
        return String.format("cf[%s] = \"%s\"", linearIdFieldNumericId(), linearId);
    }
}
