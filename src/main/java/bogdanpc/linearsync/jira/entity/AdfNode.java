package bogdanpc.linearsync.jira.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Jira's rich-text model (Atlassian Document Format).
 *
 * @see <a href="https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/">ADF structure</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdfNode(String type, Integer version, String text, Map<String, Object> attrs, List<AdfNode> content,
        List<AdfNode> marks) {

    public static AdfNode doc(List<AdfNode> content) {
        return new AdfNode("doc", 1, null, null, List.copyOf(content), null);
    }

    public static AdfNode paragraph(List<AdfNode> content) {
        return block("paragraph", content);
    }

    public static AdfNode bulletList(List<AdfNode> items) {
        return block("bulletList", items);
    }

    public static AdfNode listItem(AdfNode paragraph) {
        return block("listItem", List.of(paragraph));
    }

    public static AdfNode rule() {
        return new AdfNode("rule", null, null, null, null, null);
    }

    public static AdfNode externalImage(String url) {
        var media = new AdfNode("media", null, null, Map.of("type", "external", "url", url), null, null);
        return new AdfNode("mediaSingle", null, null, Map.of("layout", "center"), List.of(media), null);
    }

    public static AdfNode text(String value, AdfNode... marks) {
        return new AdfNode("text", null, value, null, null, marks.length == 0 ? null : List.of(marks));
    }

    public static AdfNode mark(String type) {
        return new AdfNode(type, null, null, null, null, null);
    }

    public static AdfNode link(String href) {
        return new AdfNode("link", null, null, Map.of("href", href), null, null);
    }

    private static AdfNode block(String type, List<AdfNode> content) {
        return new AdfNode(type, null, null, null, List.copyOf(content), null);
    }

    public String plainText() {
        if (text != null) {
            return text;
        }
        if (content == null) {
            return "";
        }
        var separator = "paragraph".equals(type) ? "" : " ";
        return content.stream()
                .map(AdfNode::plainText)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(separator));
    }
}
