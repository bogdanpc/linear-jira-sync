package bogdanpc.linearsync.jira.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
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
    public record JiraContentNode(
            @JsonProperty("content") List<JiraTextNode> content,
            @JsonProperty("type") String type
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraTextNode(
            @JsonProperty("text") String text,
            @JsonProperty("type") String type
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraVisibility(
            @JsonProperty("type") String type,
            @JsonProperty("value") String value,
            @JsonProperty("identifier") String identifier
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraUser(
            @JsonProperty("accountId") String accountId,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("emailAddress") String emailAddress,
            @JsonProperty("active") Boolean active,
            @JsonProperty("self") String self
    ) {}

    public static JiraComment createFromText(String text, JiraUser author) {
        var textNode = new JiraTextNode(text, "text");
        var contentNode = new JiraContentNode(List.of(textNode), "paragraph");
        var body = new JiraContent(List.of(contentNode), "doc", 1);

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