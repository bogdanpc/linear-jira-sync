package bogdanpc.linearsync.jira.control;

import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Loads the main application.properties on its own: the test classpath shadows it and defines jira.api.url.
 */
class JiraApiUrlTest {

    @Test
    void cloudIdDerivesJiraRestClientUrl() throws IOException {
        assertEquals("https://api.atlassian.com/ex/jira/abc-123", restClientUrl(Map.of("JIRA_API_CLOUDID", "abc-123")));
    }

    @Test
    void explicitUrlWinsOverCloudId() throws IOException {
        assertEquals("https://example.atlassian.net", restClientUrl(
                Map.of("JIRA_API_CLOUDID", "abc-123", "JIRA_API_URL", "https://example.atlassian.net")));
    }

    private static String restClientUrl(Map<String, String> environment) throws IOException {
        var config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new PropertiesConfigSource(Path.of("src/main/resources/application.properties").toUri().toURL()))
                .withSources(new EnvConfigSource(environment, 300))
                .build();
        return config.getValue("quarkus.rest-client.jira-api.url", String.class);
    }
}
