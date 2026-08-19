package bogdanpc.linearsync.jira.control;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives the Jira REST client URL from {@code jira.api.cloudid} when configured.
 * Uses ordinal 275 so it overrides file-based URLs but not environment variables.
 */
public class JiraCloudIdConfigSourceFactory implements ConfigSourceFactory {

    private static final String CLOUD_API_BASE = "https://api.atlassian.com/ex/jira/";
    private static final String REST_CLIENT_URL_KEY = "quarkus.rest-client.jira-api.url";

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        var cloudIdValue = context.getValue("jira.api.cloudid");
        if (cloudIdValue == null || cloudIdValue.getValue().isBlank()) {
            return List.of();
        }
        var url = CLOUD_API_BASE + cloudIdValue.getValue();
        return List.of(new CloudIdUrlConfigSource(url));
    }

    private static class CloudIdUrlConfigSource implements ConfigSource {

        private final String url;

        CloudIdUrlConfigSource(String url) {
            this.url = url;
        }

        @Override
        public int getOrdinal() {
            return 275;
        }

        @Override
        public Map<String, String> getProperties() {
            return Map.of(REST_CLIENT_URL_KEY, url);
        }

        @Override
        public Set<String> getPropertyNames() {
            return Set.of(REST_CLIENT_URL_KEY);
        }

        @Override
        public String getValue(String propertyName) {
            return REST_CLIENT_URL_KEY.equals(propertyName) ? url : null;
        }

        @Override
        public String getName() {
            return "jira-cloud-id";
        }
    }
}
