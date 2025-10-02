package bogdanpc.linearsync.jira.control;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Base64;

public class JiraAuthHeaderProvider {

    private JiraAuthHeaderProvider() {

    }
    public static String getAuthHeader() {
        var jiraUsername = ConfigProvider.getConfig().getValue("jira.api.username", String.class);
        var jiraApiToken = ConfigProvider.getConfig().getValue("jira.api.token", String.class);
        var credentials = jiraUsername + ":" + jiraApiToken;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}