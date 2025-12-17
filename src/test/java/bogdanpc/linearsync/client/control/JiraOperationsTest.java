package bogdanpc.linearsync.client.control;

import bogdanpc.linearsync.jira.control.JiraOperations;
import bogdanpc.linearsync.jira.entity.JiraIssue;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.synchronization.control.IssueDataTransfer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@ConnectWireMock
class JiraOperationsTest {

    @Inject
    JiraOperations jiraService;

    @Inject
    IssueDataTransfer issueDataTransfer;

    WireMock wiremock;

    @Test
    void testCreateIssue_Success() {
        var linearIssue = createTestLinearIssue();
        var jiraIssueInput = issueDataTransfer.mapToJiraIssueInput(linearIssue);

        JiraIssue createdIssue = jiraService.createIssue(jiraIssueInput);

        assertNotNull(createdIssue);
        assertEquals("12345", createdIssue.id());
        assertEquals("TEST-123", createdIssue.key());
    }

    @Test
    void testUpdateIssue_Success() {
        var linearIssue = createTestLinearIssue();
        var jiraIssueInput = issueDataTransfer.mapToJiraIssueInput(linearIssue);

        assertDoesNotThrow(() -> jiraService.updateIssue("TEST-123", jiraIssueInput));
    }

    @Test
    void testFindIssueByLinearId_Found() {
        var result = jiraService.findIssueBySourceId("linear-issue-id");
        var issue = result.orElseThrow(() -> new AssertionError("Expected issue to be present"));

        assertEquals("TEST-123", issue.key());
        assertEquals("12345", issue.id());
    }

    @Test
    void testFindIssueByLinearId_NotFound() {
        Optional<JiraIssue> result = jiraService.findIssueBySourceId("nonexistent-id");

        assertFalse(result.isPresent());
    }

    @Test
    void testGetAllIssuesInProject() {
        var issues = jiraService.getAllIssuesInProject();

        assertNotNull(issues);
        assertEquals(2, issues.size());
        assertEquals("TEST-123", issues.get(0).key());
        assertEquals("TEST-124", issues.get(1).key());
    }

    @Test
    void testGetAllIssuesInProject_WithPagination() {
        var page1Stub = wiremock.register(get(urlPathEqualTo("/jira/rest/api/3/search/jql"))
                .withQueryParam("maxResults", equalTo("50"))
                .withQueryParam("nextPageToken", absent())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("jira-pagination-page1.json")));

        var page2Stub = wiremock.register(get(urlPathEqualTo("/jira/rest/api/3/search/jql"))
                .withQueryParam("nextPageToken", equalTo("token-for-page-2"))
                .withQueryParam("maxResults", equalTo("50"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("jira-pagination-page2.json")));

        try {
            List<JiraIssue> issues = jiraService.getAllIssuesInProject();

            assertNotNull(issues);
            assertEquals(51, issues.size()); // 50 from first page + 1 from second page
        } finally {
            wiremock.removeStubMapping(page1Stub);
            wiremock.removeStubMapping(page2Stub);
        }
    }

    @Test
    void testCreateIssue_AuthenticationError() {
        var stub = wiremock.register(post(urlEqualTo("/jira/rest/api/3/issue"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("Authentication failed")));

        try {
            LinearIssue linearIssue = createTestLinearIssue();
            JiraIssueInput jiraIssueInput = issueDataTransfer.mapToJiraIssueInput(linearIssue);

            RuntimeException exception = assertThrows(RuntimeException.class, () -> jiraService.createIssue(jiraIssueInput));

            assertTrue(exception.getMessage().contains("Failed to create Jira issue"));
        } finally {
            wiremock.removeStubMapping(stub);
        }
    }

    @Test
    void testUpdateIssue_NotFound() {
        var stub = wiremock.register(put(urlMatching("/jira/rest/api/3/issue/NONEXISTENT-123"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Issue not found")));

        try {
            LinearIssue linearIssue = createTestLinearIssue();
            JiraIssueInput jiraIssueInput = issueDataTransfer.mapToJiraIssueInput(linearIssue);

            RuntimeException exception = assertThrows(RuntimeException.class, () -> jiraService.updateIssue("NONEXISTENT-123", jiraIssueInput));

            assertTrue(exception.getMessage().contains("Failed to update Jira issue"));
        } finally {
            wiremock.removeStubMapping(stub);
        }
    }

    private LinearIssue createTestLinearIssue() {
        // State
        var state = new LinearIssue.LinearState(
                "state-1",
                "In Progress",
                "started"
        );

        // Team
        var team = new LinearIssue.LinearTeam(
                "team-1",
                "Engineering",
                "ENG"
        );

        // Creator
        var creator = new LinearIssue.LinearUser(
                "user-1",
                "Test User",
                "test@example.com",
                "Test User"
        );

        // Labels
        var label = new LinearIssue.LinearLabel(
                "label-1",
                "bug",
                "#ff0000"
        );
        var labels = new LinearIssue.LinearLabels(
                List.of(label)
        );

        return new LinearIssue(
                "linear-issue-id",
                "ENG-123",
                "Test Issue",
                "Test description",
                2,
                state,
                null,
                creator,
                team,
                labels,
                null,
                null,
                null,        // parent
                null,        // children
                Instant.parse("2024-01-01T10:00:00Z"),
                Instant.parse("2024-01-02T10:00:00Z"),
                "https://linear.app/test/issue/ENG-123"
        );
    }
}