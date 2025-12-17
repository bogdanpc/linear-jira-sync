package bogdanpc.linearsync.jira.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraProject(
    @JsonProperty("id") String id,
    @JsonProperty("key") String key,
    @JsonProperty("name") String name,
    @JsonProperty("issueTypes") List<IssueType> issueTypes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssueType(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("subtask") boolean subtask,
        @JsonProperty("description") String description
    ) {}
}
