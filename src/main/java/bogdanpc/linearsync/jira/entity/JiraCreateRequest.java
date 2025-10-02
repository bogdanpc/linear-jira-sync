package bogdanpc.linearsync.jira.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JiraCreateRequest {

    @JsonProperty("fields")
    public Fields fields;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Fields {
        @JsonProperty("project")
        public Project project;

        @JsonProperty("summary")
        public String summary;

        @JsonProperty("description")
        public Object description;

        @JsonProperty("issuetype")
        public IssueType issuetype;

        @JsonProperty("priority")
        public Priority priority;

        @JsonProperty("assignee")
        public Assignee assignee;

        @JsonProperty("labels")
        public List<String> labels;

        // Dynamic fields for custom properties
        public Map<String, Object> customFields = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getCustomFields() {
            return customFields;
        }

        public void setCustomField(String fieldName, Object value) {
            if (value != null) {
                customFields.put(fieldName, value);
            }
        }
    }

    public record Project(@JsonProperty("key") String key) {}

    public record IssueType(@JsonProperty("name") String name) {}

    public record Priority(@JsonProperty("name") String name) {}

    public record Assignee(@JsonProperty("accountId") String accountId) {}


    public record Description(
            @JsonProperty("type") String type,
            @JsonProperty("version") int version,
            @JsonProperty("content") List<Map<String, Object>> content
    ) {
        public Description(String text) {
            this("doc", 1, List.of(
                    Map.of(
                            "type", "paragraph",
                            "content", List.of(
                                    Map.of(
                                            "type", "text",
                                            "text", text != null ? text : ""
                                    )
                            )
                    )
            ));
        }
    }
}