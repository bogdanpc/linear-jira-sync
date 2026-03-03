package bogdanpc.linearsync.configuration.control;

import bogdanpc.linearsync.configuration.entity.ConfigurationException;
import bogdanpc.linearsync.jira.control.JiraConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Validates that all required configuration is present for Linear-Jira synchronization.
 * Properties can be set via application.properties, system properties,
 * or environment variables (which take precedence).
 */
@ApplicationScoped
public class SyncConfiguration {

    private final JiraConfig jiraConfig;

    @ConfigProperty(name = "linear.api.token")
    Optional<String> linearApiToken;

    SyncConfiguration(JiraConfig jiraConfig) {
        this.jiraConfig = jiraConfig;
    }

    public String linearApiToken() {
        return linearApiToken.orElseThrow(() ->
                new ConfigurationException("Linear API token is required (LINEAR_API_TOKEN or linear.api.token)"));
    }

    /**
     * Validates that all required configuration is present.
     *
     * @throws ConfigurationException if required configuration is missing
     */
    public void validate() {
        if (linearApiToken.isEmpty()) {
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

        if (jiraConfig.apiUrl().isEmpty()) {
            throw new ConfigurationException("Jira API URL is required (JIRA_API_URL or jira.api.url)");
        }
    }
}
