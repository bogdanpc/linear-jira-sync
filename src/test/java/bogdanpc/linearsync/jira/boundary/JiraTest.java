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
	void testCreateIssue_AuthenticationError() {
		var stub = wiremock.register(post(urlEqualTo("/jira/rest/api/3/issue"))
			.willReturn(aResponse()
				.withStatus(401)
				.withBody("Authentication failed")));

		try {
			var jiraIssueInput = createTestInput();

			var exception = assertThrows(JiraApiException.class,
					() -> jiraService.createIssue(jiraIssueInput));

			assertEquals(401, exception.getStatusCode());
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
			var jiraIssueInput = createTestInput();

			var exception = assertThrows(JiraApiException.class,
					() -> jiraService.updateIssue("NONEXISTENT-123", jiraIssueInput));

			assertEquals(404, exception.getStatusCode());
		} finally {
			wiremock.removeStubMapping(stub);
		}
	}

	private static JiraIssueInput createTestInput() {
		return new JiraIssueInput(
				"linear-issue-id",
				"ENG-123",
				"Test Issue",
				"Test description",
				2,
				WorkflowStatus.IN_PROGRESS,
				null,
				null,
				"test@example.com",
				"Test User",
				"Engineering",
				"ENG",
				List.of(new JiraIssueInput.LabelInput("bug", "#ff0000")),
				List.of(),
				List.of(),
				Instant.parse("2024-01-01T10:00:00Z"),
				Instant.parse("2024-01-02T10:00:00Z"),
				"https://linear.app/test/issue/ENG-123",
				null);
	}
}