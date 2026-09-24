package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.AdfNode;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkupFormatterTest {

    final MarkupFormatter formatter = new MarkupFormatter();

    @Test
    void commentKeepsListsAndImages() {
        var comment = new JiraIssueInput.CommentInput("c-1", """
                Steps
                to reproduce:

                - open **settings**
                - click save
                ![screenshot](https://uploads.linear.app/shot.png)""",
                "jane", "Jane", null, Instant.parse("2024-01-01T10:00:00Z"), null, null);

        var content = formatter.formatComment(comment).content();

        assertEquals("paragraph", content.get(0).type(), "author header");
        assertEquals("Steps to reproduce:", content.get(1).plainText());
        var list = content.get(2);
        assertEquals("bulletList", list.type());
        assertEquals(2, list.content().size());
        assertEquals("strong", list.content().getFirst().content().getFirst().content().get(1).marks().getFirst().type());
        assertEquals("mediaSingle", content.get(3).type());
        assertEquals("Jane · Jan 1, 2024 10:00 Z Steps to reproduce: open settings click save",
                formatter.formatComment(comment).plainText());
    }

    @Test
    void blankDescriptionIsAValidDocument() {
        AdfNode doc = formatter.markdownToAdf(null);

        assertEquals("doc", doc.type());
        assertEquals("paragraph", doc.content().getFirst().type());
    }
}
