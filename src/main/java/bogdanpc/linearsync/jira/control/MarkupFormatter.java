package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraComment.JiraContent;
import bogdanpc.linearsync.jira.entity.JiraComment.JiraContentNode;
import bogdanpc.linearsync.jira.entity.JiraComment.JiraMark;
import bogdanpc.linearsync.jira.entity.JiraComment.JiraTextNode;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@ApplicationScoped
class MarkupFormatter {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");
    private static final Pattern BLANK_LINE_PATTERN = Pattern.compile("\n\\s*\n");
    private static final Pattern INLINE_WHITESPACE_PATTERN = Pattern.compile("\\s*\n\\s*");

    /**
     * Builds an ADF document for a Linear comment with a metadata header,
     * inline images rendered as media nodes, and text as paragraphs.
     */
    JiraContent formatCommentForJira(JiraIssueInput.CommentInput commentInput) {
        var nodes = new ArrayList<JiraContentNode>();

        nodes.add(buildCommentMetadata(commentInput));

        if (commentInput.body() != null && !commentInput.body().isBlank()) {
            nodes.addAll(parseMarkdownBody(commentInput.body()));
        }

        return new JiraContent(nodes, "doc", 1);
    }

    private JiraContentNode buildCommentMetadata(JiraIssueInput.CommentInput comment) {
        var parts = new ArrayList<JiraTextNode>();

        var author = comment.authorDisplayName() != null ? comment.authorDisplayName() : comment.authorName();
        if (author != null) {
            parts.add(new JiraTextNode(author, "text", List.of(new JiraMark("strong", null)), null));
        }

        if (comment.createdAt() != null) {
            addSeparator(parts);
            parts.add(text(formatDateTime(comment.createdAt())));
        }

        return paragraph(parts.isEmpty() ? List.of(text("Comment from Linear")) : parts);
    }

    /**
     * Parses markdown text into ADF nodes, converting {@code ![alt](url)}
     * image references to mediaSingle nodes and text to paragraph nodes.
     */
    private List<JiraContentNode> parseMarkdownBody(String body) {
        var nodes = new ArrayList<JiraContentNode>();
        var matcher = IMAGE_PATTERN.matcher(body);
        int lastEnd = 0;

        while (matcher.find()) {
            addTextParagraphs(nodes, body.substring(lastEnd, matcher.start()));
            nodes.add(mediaSingle(matcher.group(2)));
            lastEnd = matcher.end();
        }

        addTextParagraphs(nodes, body.substring(lastEnd));
        return nodes;
    }

    private void addTextParagraphs(List<JiraContentNode> nodes, String text) {
        for (var segment : BLANK_LINE_PATTERN.split(text)) {
            var trimmed = segment.strip();
            if (!trimmed.isEmpty()) {
                nodes.add(paragraph(text(INLINE_WHITESPACE_PATTERN.matcher(trimmed).replaceAll(" "))));
            }
        }
    }

    private static JiraContentNode mediaSingle(String url) {
        var media = new JiraTextNode(null, "media", null, Map.of("type", "external", "url", url));
        return new JiraContentNode(List.of(media), "mediaSingle", Map.of("layout", "center"));
    }

    /**
     * Builds an ADF document for a Linear attachment, rendering
     * the title as a clickable link with source type and date metadata.
     */
    JiraContent formatAttachmentForJira(JiraIssueInput.AttachmentInput attachment) {
        var nodes = new ArrayList<JiraContentNode>();

        nodes.add(buildTitleNode(attachment));
        buildMetadataNode(attachment).ifPresent(nodes::add);

        return new JiraContent(nodes, "doc", 1);
    }

    private JiraContentNode buildTitleNode(JiraIssueInput.AttachmentInput attachment) {
        var title = attachment.title();
        var url = attachment.url();

        if (title != null && url != null) {
            var marks = List.of(
                    new JiraMark("link", Map.of("href", url)),
                    new JiraMark("strong", null));
            return paragraph(new JiraTextNode(title, "text", marks, null));
        }
        if (title != null) {
            return paragraph(new JiraTextNode(title, "text", List.of(new JiraMark("strong", null)), null));
        }
        if (url != null) {
            return paragraph(new JiraTextNode(url, "text", List.of(new JiraMark("link", Map.of("href", url))), null));
        }
        return paragraph(text("Linked Resource"));
    }

    private Optional<JiraContentNode> buildMetadataNode(JiraIssueInput.AttachmentInput attachment) {
        var parts = new ArrayList<JiraTextNode>();

        if (attachment.sourceType() != null) {
            parts.add(new JiraTextNode(formatSourceType(attachment.sourceType()), "text",
                    List.of(new JiraMark("code", null)), null));
        }

        var creator = attachment.creatorDisplayName() != null
                ? attachment.creatorDisplayName()
                : attachment.creatorName();
        if (creator != null) {
            addSeparator(parts);
            parts.add(text(creator));
        }

        if (attachment.createdAt() != null) {
            addSeparator(parts);
            parts.add(text(formatDate(attachment.createdAt())));
        }

        return parts.isEmpty() ? Optional.empty() : Optional.of(paragraph(parts));
    }

    private void addSeparator(List<JiraTextNode> parts) {
        if (!parts.isEmpty()) {
            parts.add(text(" \u00b7 "));
        }
    }

    private String formatSourceType(String sourceType) {
        return sourceType.substring(0, 1).toUpperCase() + sourceType.substring(1);
    }

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm z")
            .withZone(ZoneOffset.UTC);

    private String formatDate(Instant instant) {
        return DATE_FORMAT.format(instant);
    }

    private String formatDateTime(Instant instant) {
        return DATETIME_FORMAT.format(instant);
    }

    private static JiraTextNode text(String value) {
        return new JiraTextNode(value, "text", null, null);
    }

    private static JiraContentNode paragraph(JiraTextNode... nodes) {
        return new JiraContentNode(List.of(nodes), "paragraph", null);
    }

    private static JiraContentNode paragraph(List<JiraTextNode> nodes) {
        return new JiraContentNode(List.copyOf(nodes), "paragraph", null);
    }

    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AdfNode(String type, Integer version, String text, List<AdfNode> content, List<AdfNode> marks) {
    }

    /**
     * Converts a markdown-like string into a Jira ADF document structure.
     * Supports bold text, horizontal rules, and bullet lists.
     */
    AdfNode markdownToAdf(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return adfDoc(List.of(adfParagraph(List.of(adfText("")))));
        }

        var content = new ArrayList<AdfNode>();
        var lines = markdown.split("\n");
        var bulletItems = new ArrayList<String>();

        for (var line : lines) {
            var stripped = line.strip();

            if (!stripped.startsWith("- ") && !bulletItems.isEmpty()) {
                content.add(adfBulletList(bulletItems));
                bulletItems.clear();
            }

            if (stripped.equals("---")) {
                content.add(new AdfNode("rule", null, null, null, null));
            } else if (stripped.startsWith("- ")) {
                bulletItems.add(stripped.substring(2));
            } else if (!stripped.isEmpty()) {
                content.add(adfParagraph(parseInlineFormatting(stripped)));
            }
        }

        if (!bulletItems.isEmpty()) {
            content.add(adfBulletList(bulletItems));
        }

        if (content.isEmpty()) {
            content.add(adfParagraph(List.of(adfText(""))));
        }

        return adfDoc(content);
    }

    private List<AdfNode> parseInlineFormatting(String text) {
        var nodes = new ArrayList<AdfNode>();
        var matcher = BOLD_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                nodes.add(adfText(text.substring(lastEnd, matcher.start())));
            }
            nodes.add(adfBoldText(matcher.group(1)));
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            nodes.add(adfText(text.substring(lastEnd)));
        }

        return nodes;
    }

    private static AdfNode adfDoc(List<AdfNode> content) {
        return new AdfNode("doc", 1, null, content, null);
    }

    private static AdfNode adfParagraph(List<AdfNode> content) {
        return new AdfNode("paragraph", null, null, content, null);
    }

    private static AdfNode adfText(String value) {
        return new AdfNode("text", null, value, null, null);
    }

    private static AdfNode adfBoldText(String value) {
        return new AdfNode("text", null, value, null, List.of(new AdfNode("strong", null, null, null, null)));
    }

    private AdfNode adfBulletList(List<String> items) {
        var listItems = items.stream()
                .map(this::adfListItem)
                .toList();
        return new AdfNode("bulletList", null, null, listItems, null);
    }

    private AdfNode adfListItem(String item) {
        return new AdfNode("listItem", null, null, List.of(adfParagraph(parseInlineFormatting(item))), null);
    }

}