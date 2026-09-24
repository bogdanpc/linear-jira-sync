package bogdanpc.linearsync.jira.boundary;

import bogdanpc.linearsync.jira.control.JiraApiException;
import bogdanpc.linearsync.jira.entity.JiraIssue;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import bogdanpc.linearsync.jira.entity.WorkflowStatus;
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
class JiraTest {

    @Inject
    Jira jiraService;

    WireMock wiremock;

    @Test
    void testCreateIssue_Success() {
        var jiraIssueInput = createTestInput();

        JiraIssue createdIssue = jiraService.createIssue(jiraIssueInput);

        assertNotNull(createdIssue);
        assertEquals("12345", createdIssue.id());
        assertEquals("TEST-123", createdIssue.key());
    }

    @Test
    void testUpdateIssue_Success() {
        var jiraIssueInput = createTestInput();

        assertDoesNotThrow(() -> jiraService.updateIssue("TEST-123", jiraIssueInput));
    }

    @Test
    void testFindExistingIssue_ByLinearIdField() {
        var issue = jiraService.findExistingIssue("linear-issue-id", "ENG-123")
                .orElseThrow(() -> new AssertionError("Expected issue to be present"));

        assertEquals("TEST-123", issue.key());
        assertEquals("12345", issue.id());
    }

    @Test
    void testFindExistingIssue_FallsBackToExactSummaryPrefix() {
        var stub = wiremock.register(get(urlPathEqualTo("/jira/rest/api/3/search/jql")).atPriority(1)
                .withQueryParam("jql", containing("summary ~")).withQueryParam("fields", containing("summary"))
                .willReturn(okJson("""
                        {"issues": [
                          {"id": "1", "key": "TEST-1", "fields": {"summary": "[ENG-7] Other issue"}},
                          {"id": "2", "key": "TEST-2", "fields": {"summary": "[ENG-77] Right issue"}}
                        ]}
                        """)));

        try {
            var issue = jiraService.findExistingIssue("unknown-linear-id", "ENG-77");

            assertEquals(Optional.of("TEST-2"), issue.map(JiraIssue::key));
        } finally {
            wiremock.removeStubMapping(stub);
        }
    }

    @Test
    void testFindExistingIssue_NotFound() {
        assertTrue(jiraService.findExistingIssue("unknown-linear-id", "ENG-999").isEmpty());
    }

    @Test
    void testCreateIssue_AuthenticationError() {
        var stub = wiremock.register(post(urlEqualTo("/jira/rest/api/3/issue"))
                .willReturn(aResponse().withStatus(401).withBody("Authentication failed")));

        try {
            var jiraIssueInput = createTestInput();

            var exception = assertThrows(JiraApiException.class, () -> jiraService.createIssue(jiraIssueInput));

            assertEquals(401, exception.getStatusCode());
        } finally {
            wiremock.removeStubMapping(stub);
        }
    }

    @Test
    void testUpdateIssue_NotFound() {
        var stub = wiremock.register(put(urlMatching("/jira/rest/api/3/issue/NONEXISTENT-123"))
                .willReturn(aResponse().withStatus(404).withBody("Issue not found")));

        try {
            var jiraIssueInput = createTestInput();

            var exception = assertThrows(JiraApiException.class,
                    () -> jiraService.updateIssue("NONEXISTENT-123", jiraIssueInput));

            assertEquals(404, exception.getStatusCode());
        } finally {
            wiremock.removeStubMapping(stub);
        }
    }

    @Test
    void testUpdateIssue_SendsJiraSafeLabelsAndClearsRemovedOnes() {
        var stub = wiremock.register(put(urlEqualTo("/jira/rest/api/3/issue/TEST-LABELS")).atPriority(1)
                .willReturn(aResponse().withStatus(204)));

        try {
            jiraService.updateIssue("TEST-LABELS", createTestInput(List.of(
                    new JiraIssueInput.LabelInput("Needs Review", null),
                    new JiraIssueInput.LabelInput("Needs_Review", null))));
            jiraService.updateIssue("TEST-LABELS", createTestInput(List.of()));

            wiremock.verify(putRequestedFor(urlEqualTo("/jira/rest/api/3/issue/TEST-LABELS"))
                    .withRequestBody(matchingJsonPath("$.fields.labels", equalToJson("[\"Needs_Review\"]"))));
            wiremock.verify(putRequestedFor(urlEqualTo("/jira/rest/api/3/issue/TEST-LABELS"))
                    .withRequestBody(matchingJsonPath("$.fields.labels", equalToJson("[]"))));
        } finally {
            wiremock.removeStubMapping(stub);
        }
    }

    @Test
    void testTransitionIssueStatus_MatchesCustomWorkflowByStatusCategory() {
        var issueUrl = "/jira/rest/api/3/issue/TEST-FLOW";
        var stubs = List.of(
                wiremock.register(get(urlEqualTo(issueUrl)).atPriority(1).willReturn(okJson("""
                        {"id": "9", "key": "TEST-FLOW", "fields": {"status": {"id": "10", "name": "Backlog",
                          "statusCategory": {"id": "2", "key": "new", "name": "To Do"}}}}
                        """))),
                wiremock.register(get(urlEqualTo(issueUrl + "/transitions")).atPriority(1).willReturn(okJson("""
                        {"transitions": [
                          {"id": "51", "name": "Close", "to": {"id": "12", "name": "Closed",
                            "statusCategory": {"id": "3", "key": "done", "name": "Done"}}},
                          {"id": "41", "name": "Start", "to": {"id": "11", "name": "Doing",
                            "statusCategory": {"id": "4", "key": "indeterminate", "name": "In Progress"}}}
                        ]}
                        """))),
                wiremock.register(post(urlEqualTo(issueUrl + "/transitions")).atPriority(1)
                        .willReturn(aResponse().withStatus(204))));

        try {
            jiraService.transitionIssueStatus("TEST-FLOW", WorkflowStatus.IN_PROGRESS);

            wiremock.verify(postRequestedFor(urlEqualTo(issueUrl + "/transitions"))
                    .withRequestBody(matchingJsonPath("$.transition.id", equalTo("41"))));
        } finally {
            stubs.forEach(wiremock::removeStubMapping);
        }
    }

    private static JiraIssueInput createTestInput() {
        return createTestInput(List.of(new JiraIssueInput.LabelInput("bug", "#ff0000")));
    }

    private static JiraIssueInput createTestInput(List<JiraIssueInput.LabelInput> labels) {
        return new JiraIssueInput("linear-issue-id", "ENG-123", "Test Issue", "Test description", 2,
                WorkflowStatus.IN_PROGRESS, null, null, "test@example.com", "Test User", "Engineering", "ENG",
                labels, List.of(), List.of(),
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-02T10:00:00Z"),
                "https://linear.app/test/issue/ENG-123", null);
    }
}