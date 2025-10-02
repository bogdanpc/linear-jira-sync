package bogdanpc.linearsync.linear.control;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@ConnectWireMock
class IssueOperationsTest {

    @Inject
    IssueOperations linearService;

    @Test
    void testGetIssues_Success() {
        var issues = linearService.getIssues("ENG", "started", null);

        assertNotNull(issues);
        assertEquals(1, issues.size());

        var issue = issues.getFirst();
        assertEquals("issue-1", issue.id());
        assertEquals("ENG-123", issue.identifier());
        assertEquals("Test Issue", issue.title());
        assertEquals("Test description", issue.description());
        assertEquals(2, issue.priority());

        assertNotNull(issue.state());
        assertEquals("In Progress", issue.state().name());
        assertEquals("started", issue.state().type());

        assertNotNull(issue.assignee());
        assertEquals("John Doe", issue.assignee().displayName());

        assertNotNull(issue.team());
        assertEquals("ENG", issue.team().key());

        assertNotNull(issue.labels());
        assertEquals(1, issue.labels().nodes().size());
        assertEquals("bug", issue.labels().nodes().getFirst().name());

        // Verify the request was made
        verify(postRequestedFor(urlEqualTo("/linear/"))
                .withHeader("Authorization", containing("test-token")));
    }


    @Test
    void testGetIssues_WithFilters() {
        var updatedAfter = Instant.parse("2024-01-01T00:00:00Z");
        var issues = linearService.getIssues("BACKEND", "started", updatedAfter);

        assertNotNull(issues);
        assertEquals(0, issues.size());

        // Verify the request contains the filters
        verify(postRequestedFor(urlEqualTo("/linear/"))
                .withRequestBody(containing("BACKEND"))
                .withRequestBody(containing("started"))
                .withRequestBody(containing("2024-01-01T00:00:00Z")));
    }
}