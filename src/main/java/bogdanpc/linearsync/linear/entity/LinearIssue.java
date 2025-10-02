package bogdanpc.linearsync.linear.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LinearIssue(
    @JsonProperty("id") String id,
    @JsonProperty("identifier") String identifier,
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("priority") Integer priority,
    @JsonProperty("state") LinearState state,
    @JsonProperty("assignee") LinearUser assignee,
    @JsonProperty("creator") LinearUser creator,
    @JsonProperty("team") LinearTeam team,
    @JsonProperty("labels") @JsonDeserialize(using = LinearLabelsDeserializer.class) LinearLabels labels,
    @JsonProperty("comments") LinearComments comments,
    @JsonProperty("attachments") LinearAttachments attachments,
    @JsonProperty("createdAt") Instant createdAt,
    @JsonProperty("updatedAt") Instant updatedAt,
    @JsonProperty("url") String url
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearState(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("type") String type
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearUser(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("email") String email,
        @JsonProperty("displayName") String displayName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearTeam(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("key") String key
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearLabels(
        @JsonProperty("nodes") List<LinearLabel> nodes
    ) {
        public static LinearLabels empty() {
            return new LinearLabels(List.of());
        }

        public List<LinearLabel> getNodes() {
            return nodes != null ? nodes : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearLabel(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("color") String color
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearComments(
        @JsonProperty("nodes") List<LinearComment> nodes,
        @JsonProperty("pageInfo") LinearPageInfo pageInfo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearComment(
        @JsonProperty("id") String id,
        @JsonProperty("body") String body,
        @JsonProperty("user") LinearUser user,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt,
        @JsonProperty("url") String url
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearAttachments(
        @JsonProperty("nodes") List<LinearAttachment> nodes,
        @JsonProperty("pageInfo") LinearPageInfo pageInfo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearAttachment(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("url") String url,
        @JsonProperty("sourceType") String sourceType,
        @JsonProperty("creator") LinearUser creator,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("createdAt") Instant createdAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearPageInfo(
        @JsonProperty("hasNextPage") boolean hasNextPage,
        @JsonProperty("endCursor") String endCursor
    ) {}
}