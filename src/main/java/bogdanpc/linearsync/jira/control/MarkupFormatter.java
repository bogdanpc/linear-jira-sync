package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.AdfNode;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static bogdanpc.linearsync.jira.entity.AdfNode.*;

/**
 * . Only the subset Linear users commonly write is supported: paragraphs, bullet
 * lists, horizontal rules, bold text and inline images.
 */
@ApplicationScoped
class MarkupFormatter {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final String SEPARATOR = " · ";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm z")
            .withZone(ZoneOffset.UTC);

    AdfNode markdownToAdf(String markdown) {
        var blocks = markdown == null ? List.<AdfNode>of() : blocks(markdown);
        return doc(blocks.isEmpty() ? List.of(paragraph(List.of(text("")))) : blocks);
    }

    /**
     * Prefixes the body with author and time, because every synced comment is posted by the API user.
     */
    AdfNode formatComment(JiraIssueInput.CommentInput comment) {
        var nodes = new ArrayList<AdfNode>();
        nodes.add(commentHeader(comment));
        if (comment.body() != null) {
            nodes.addAll(blocksWithImages(comment.body()));
        }
        return doc(nodes);
    }

    AdfNode formatAttachment(JiraIssueInput.AttachmentInput attachment) {
        var nodes = new ArrayList<AdfNode>();
        nodes.add(attachmentTitle(attachment));
        attachmentMetadata(attachment).ifPresent(nodes::add);
        return doc(nodes);
    }

    private AdfNode commentHeader(JiraIssueInput.CommentInput comment) {
        var author = comment.authorDisplayName() != null ? comment.authorDisplayName() : comment.authorName();
        var parts = new ArrayList<AdfNode>();
        if (author != null) {
            parts.add(text(author, mark("strong")));
        }
        if (comment.createdAt() != null) {
            appendSeparated(parts, text(DATETIME_FORMAT.format(comment.createdAt())));
        }
        return paragraph(parts.isEmpty() ? List.of(text("Comment from Linear")) : parts);
    }

    private List<AdfNode> blocksWithImages(String markdown) {
        var nodes = new ArrayList<AdfNode>();
        var matcher = IMAGE_PATTERN.matcher(markdown);
        var lastEnd = 0;

        while (matcher.find()) {
            nodes.addAll(blocks(markdown.substring(lastEnd, matcher.start())));
            nodes.add(externalImage(matcher.group(2)));
            lastEnd = matcher.end();
        }

        nodes.addAll(blocks(markdown.substring(lastEnd)));
        return nodes;
    }

    /**
     * Consecutive text lines form one paragraph, as in markdown; blank lines, bullets and rules end it.
     */
    private List<AdfNode> blocks(String markdown) {
        var blocks = new ArrayList<AdfNode>();
        var paragraphLines = new ArrayList<String>();
        var bulletItems = new ArrayList<String>();

        for (var line : markdown.split("\n")) {
            var stripped = line.strip();
            if (stripped.startsWith("- ")) {
                flushParagraph(blocks, paragraphLines);
                bulletItems.add(stripped.substring(2));
                continue;
            }
            flushBullets(blocks, bulletItems);
            if (stripped.equals("---")) {
                flushParagraph(blocks, paragraphLines);
                blocks.add(rule());
            } else if (stripped.isEmpty()) {
                flushParagraph(blocks, paragraphLines);
            } else {
                paragraphLines.add(stripped);
            }
        }

        flushParagraph(blocks, paragraphLines);
        flushBullets(blocks, bulletItems);
        return blocks;
    }

    private void flushParagraph(List<AdfNode> blocks, List<String> lines) {
        if (!lines.isEmpty()) {
            blocks.add(paragraph(inline(String.join(" ", lines))));
            lines.clear();
        }
    }

    private void flushBullets(List<AdfNode> blocks, List<String> items) {
        if (!items.isEmpty()) {
            blocks.add(bulletList(items.stream().map(item -> listItem(paragraph(inline(item)))).toList()));
            items.clear();
        }
    }

    private List<AdfNode> inline(String text) {
        var nodes = new ArrayList<AdfNode>();
        var matcher = BOLD_PATTERN.matcher(text);
        var lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                nodes.add(text(text.substring(lastEnd, matcher.start())));
            }
            nodes.add(text(matcher.group(1), mark("strong")));
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            nodes.add(text(text.substring(lastEnd)));
        }
        return nodes;
    }

    private AdfNode attachmentTitle(JiraIssueInput.AttachmentInput attachment) {
        var title = attachment.title();
        var url = attachment.url();

        if (title != null && url != null) {
            return paragraph(List.of(text(title, link(url), mark("strong"))));
        }
        if (title != null) {
            return paragraph(List.of(text(title, mark("strong"))));
        }
        if (url != null) {
            return paragraph(List.of(text(url, link(url))));
        }
        return paragraph(List.of(text("Linked Resource")));
    }

    private Optional<AdfNode> attachmentMetadata(JiraIssueInput.AttachmentInput attachment) {
        var parts = new ArrayList<AdfNode>();

        if (attachment.sourceType() != null) {
            parts.add(text(capitalize(attachment.sourceType()), mark("code")));
        }

        var creator = attachment.creatorDisplayName() != null
                ? attachment.creatorDisplayName()
                : attachment.creatorName();
        if (creator != null) {
            appendSeparated(parts, text(creator));
        }

        if (attachment.createdAt() != null) {
            appendSeparated(parts, text(DATE_FORMAT.format(attachment.createdAt())));
        }

        return parts.isEmpty() ? Optional.empty() : Optional.of(paragraph(parts));
    }

    private static void appendSeparated(List<AdfNode> parts, AdfNode node) {
        if (!parts.isEmpty()) {
            parts.add(text(SEPARATOR));
        }
        parts.add(node);
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
