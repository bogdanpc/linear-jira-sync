package bogdanpc.linearsync.jira.control;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Base64;

/**
 * Provides Basic authentication header for Jira REST API.
 *
 * @see JiraClient
 */
public class JiraAuthHeaderProvider {

    private JiraAuthHeaderProvider() {
    }

    /**
     * @see JiraClient
     */
    public static String getAuthHeader() {
        var jiraUsername = ConfigProvider.getConfig().getValue("jira.api.username", String.class);
        var jiraApiToken = ConfigProvider.getConfig().getValue("jira.api.token", String.class);
        var credentials = jiraUsername + ":" + jiraApiToken;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}