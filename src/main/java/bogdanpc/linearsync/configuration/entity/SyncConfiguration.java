package bogdanpc.linearsync.configuration.entity;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Centralized configuration for Linear-Jira synchronization.
 * Properties can be set via application.properties, system properties,
 * or environment variables (which take precedence).
 */
@ApplicationScoped
public class SyncConfiguration {

    @ConfigProperty(name = "linear.api.token")
    Optional<String> linearApiToken;

    // Jira API Configuration
    @ConfigProperty(name = "jira.api.url")
    Optional<String> jiraApiUrl;

    @ConfigProperty(name = "jira.api.username")
    Optional<String> jiraUsername;

    @ConfigProperty(name = "jira.api.token")
    Optional<String> jiraApiToken;

    @ConfigProperty(name = "jira.project.key")
    Optional<String> jiraProjectKey;

    /**
     * Validates that all required configuration is present.
     * @throws ConfigurationException if required configuration is missing
     */
    public void validate() {
        if (linearApiToken.isEmpty()) {
            throw new ConfigurationException("Linear API token is required (LINEAR_API_TOKEN or linear.api.token)");
        }

        if (jiraUsername.isEmpty()) {
            throw new ConfigurationException("Jira username is required (JIRA_USERNAME or jira.api.username)");
        }

        if (jiraApiToken.isEmpty()) {
            throw new ConfigurationException("Jira API token is required (JIRA_API_TOKEN or jira.api.token)");
        }

        if (jiraProjectKey.isEmpty()) {
            throw new ConfigurationException("Jira project key is required (JIRA_PROJECT_KEY or jira.project.key)");
        }

        if (jiraApiUrl.isEmpty()) {
            throw new ConfigurationException("Jira API URL is required (JIRA_API_URL or jira.api.url)");
        }
    }
}