package bogdanpc.linearsync.configuration.control;

import bogdanpc.linearsync.configuration.entity.ConfigurationException;
import bogdanpc.linearsync.jira.control.JiraConfig;
import bogdanpc.linearsync.linear.control.LinearConfig;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Validates that all required configuration is present for Linear-Jira synchronization.
 * Properties can be set via application.properties, system properties,
 * or environment variables (which take precedence).
 */
@ApplicationScoped
public class SyncConfiguration {

    private final JiraConfig jiraConfig;
    private final LinearConfig linearConfig;

    SyncConfiguration(JiraConfig jiraConfig, LinearConfig linearConfig) {
        this.jiraConfig = jiraConfig;
        this.linearConfig = linearConfig;
    }

    /**
     * Validates that all required configuration is present.
     *
     * @throws ConfigurationException if required configuration is missing
     */
    public void validate() {
        if (linearConfig.api().token().isEmpty()) {
            throw new ConfigurationException("Linear API token is required (LINEAR_API_TOKEN or linear.api.token)");
        }

        if (jiraConfig.username().isEmpty()) {
            throw new ConfigurationException("Jira username is required (JIRA_USERNAME or jira.api.username)");
        }

        if (jiraConfig.apiToken().isEmpty()) {
            throw new ConfigurationException("Jira API token is required (JIRA_API_TOKEN or jira.api.token)");
        }

        if (jiraConfig.projectKey().isEmpty()) {
            throw new ConfigurationException("Jira project key is required (JIRA_PROJECT_KEY or jira.project.key)");
        }

        var hasCloudId = jiraConfig.cloudId().filter(s -> !s.isBlank()).isPresent();
        var hasApiUrl = jiraConfig.apiUrl().filter(s -> !s.isBlank()).isPresent();
        if (!hasCloudId && !hasApiUrl) {
            throw new ConfigurationException(
                    "Jira connection requires either jira.api.cloudid (JIRA_API_CLOUDID) or jira.api.url (JIRA_API_URL)");
        }
    }
}
