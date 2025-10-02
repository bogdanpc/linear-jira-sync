package bogdanpc.linearsync.jira.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssue(
        @JsonProperty("id") String id,
        @JsonProperty("key") String key,
        @JsonProperty("self") String self,
        @JsonProperty("fields") JiraFields fields
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraFields(
            @JsonProperty("summary") String summary,
            @JsonProperty("description") Object description,
            @JsonProperty("issuetype") JiraIssueType issuetype,
            @JsonProperty("priority") JiraPriority priority,
            @JsonProperty("status") JiraStatus status,
            @JsonProperty("assignee") JiraUser assignee,
            @JsonProperty("reporter") JiraUser reporter,
            @JsonProperty("project") JiraProject project,
            @JsonProperty("labels") List<String> labels,
            @JsonProperty("created") String created,
            @JsonProperty("updated") String updated,
            @JsonProperty("customfield_10000") String linearIssueId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraIssueType(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("iconUrl") String iconUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraPriority(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("iconUrl") String iconUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraStatus(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("statusCategory") JiraStatusCategory statusCategory
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraStatusCategory(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("colorName") String colorName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraUser(
            @JsonProperty("accountId") String accountId,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("emailAddress") String emailAddress,
            @JsonProperty("active") Boolean active
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraProject(
            @JsonProperty("id") String id,
            @JsonProperty("key") String key,
            @JsonProperty("name") String name
    ) {}
}