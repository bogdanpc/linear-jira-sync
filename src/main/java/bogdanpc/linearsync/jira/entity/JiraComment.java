package bogdanpc.linearsync.jira.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraComment(
        @JsonProperty("id") String id,
        @JsonProperty("author") JiraUser author,
        @JsonProperty("body") JiraContent body,
        @JsonProperty("created") String created,
        @JsonProperty("updated") String updated,
        @JsonProperty("visibility") JiraVisibility visibility,
        @JsonProperty("self") String self
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraContent(
            @JsonProperty("content") List<JiraContentNode> content,
            @JsonProperty("type") String type,
            @JsonProperty("version") Integer version
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JiraContentNode(
            @JsonProperty("content") List<JiraTextNode> content,
            @JsonProperty("type") String type,
            @JsonProperty("attrs") Map<String, Object> attrs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JiraTextNode(
            @JsonProperty("text") String text,
            @JsonProperty("type") String type,
            @JsonProperty("marks") List<JiraMark> marks,
            @JsonProperty("attrs") Map<String, String> attrs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JiraMark(
            @JsonProperty("type") String type,
            @JsonProperty("attrs") Map<String, String> attrs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraVisibility(
            @JsonProperty("type") String type,
            @JsonProperty("value") String value,
            @JsonProperty("identifier") String identifier) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraUser(
            @JsonProperty("accountId") String accountId,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("emailAddress") String emailAddress,
            @JsonProperty("active") Boolean active,
            @JsonProperty("self") String self) {
    }

    public static JiraComment createFromContent(JiraContent body, JiraUser author) {
        return new JiraComment(null, author, body, null, null, null, null);
    }

    /**
     * Extracts plain text from Jira's nested content structure by flattening
     * all text nodes into a single space-separated string.
     */
    public String extractPlainText() {
        if (body == null || body.content() == null) {
            return "";
        }

        return body.content().stream()
                .filter(contentNode -> contentNode.content() != null)
                .flatMap(contentNode -> contentNode.content().stream())
                .map(JiraTextNode::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "))
                .trim();
    }
}