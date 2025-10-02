package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.jira.entity.JiraIssue;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class IssueTransformerTest {

    @Inject
    IssueTransformer issueTransformer;

    @Test
    void testMapLinearToJiraCreateRequest() {
        // Arrange
        var linearIssue = createTestLinearIssue();

        // Act
        var request = issueTransformer.mapLinearToJiraCreateRequest(
                linearIssue, "TEST", "Task"
        );

        // Assert
        assertNotNull(request);
        assertNotNull(request.fields);

        // Project
        assertEquals("TEST", request.fields.project.key());

        // Summary
        assertEquals("[ENG-123] Test Issue", request.fields.summary);

        // Issue type
        assertEquals("Task", request.fields.issuetype.name());

        // Priority mapping (Linear 2 -> Jira High)
        assertEquals("High", request.fields.priority.name());

        // Labels
        assertNotNull(request.fields.labels);
        assertEquals(2, request.fields.labels.size());
        assertTrue(request.fields.labels.contains("bug"));
        assertTrue(request.fields.labels.contains("feature"));

        // Linear issue ID for tracking
        assertEquals("linear-123", request.fields.getCustomFields().get("customfield_10000"));

        // Description should contain original description and metadata
        assertNotNull(request.fields.description);
    }

    @Test
    void testMapLinearToJiraCreateRequest_MinimalIssue() {
        // Arrange - minimal Linear issue
        var linearIssue = new LinearIssue(
                "linear-minimal",
                "MIN-1",
                "Minimal Issue",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        // Act
        var request = issueTransformer.mapLinearToJiraCreateRequest(
                linearIssue, "TEST", "Bug"
        );

        // Assert
        assertNotNull(request);
        assertEquals("TEST", request.fields.project.key());
        assertEquals("[MIN-1] Minimal Issue", request.fields.summary);
        assertEquals("Bug", request.fields.issuetype.name());
        assertNull(request.fields.priority); // No priority mapping for null
        assertNull(request.fields.labels); // No labels for null
        // Linear issue ID should be set in custom field
        assertEquals("linear-minimal", request.fields.getCustomFields().get("customfield_10000"));
    }

    @Test
    void testShouldUpdateJiraIssue_SameSummary() {
        // Arrange
        var linearIssue = createTestLinearIssue();

        var jiraIssue = createTestJiraIssue("[ENG-123] Test Issue");

        // Act
        var shouldUpdate = issueTransformer.shouldUpdateJiraIssue(linearIssue, jiraIssue);

        // Assert
        assertFalse(shouldUpdate);
    }

    @Test
    void testShouldUpdateJiraIssue_DifferentSummary() {
        // Arrange
        var linearIssue = createTestLinearIssue();

        var jiraIssue = createTestJiraIssue("[ENG-123] Different Title");

        // Act
        var shouldUpdate = issueTransformer.shouldUpdateJiraIssue(linearIssue, jiraIssue);

        // Assert
        assertTrue(shouldUpdate);
    }

    @Test
    void testShouldUpdateJiraIssue_NoUpdatedAt() {
        // Arrange
        var linearIssue = new LinearIssue(
                "linear-123",
                "ENG-123",
                "Test Issue",
                "Description",
                2,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null, // updatedAt is null
                "https://linear.app/test/issue/ENG-123"
        );

        var jiraIssue = createTestJiraIssue("[ENG-123] Different Title");

        // Act
        var shouldUpdate = issueTransformer.shouldUpdateJiraIssue(linearIssue, jiraIssue);

        // Assert
        assertFalse(shouldUpdate); // Should not update if no updatedAt
    }

    @Test
    void testMapLinearStatusToJira() {
        assertEquals("To Do", issueTransformer.mapLinearStatusToJira("triage"));
        assertEquals("To Do", issueTransformer.mapLinearStatusToJira("backlog"));
        assertEquals("To Do", issueTransformer.mapLinearStatusToJira("todo"));
        assertEquals("In Progress", issueTransformer.mapLinearStatusToJira("in_progress"));
        assertEquals("In Review", issueTransformer.mapLinearStatusToJira("in_review"));
        assertEquals("Done", issueTransformer.mapLinearStatusToJira("done"));
        assertEquals("Cancelled", issueTransformer.mapLinearStatusToJira("canceled"));
        assertEquals("To Do", issueTransformer.mapLinearStatusToJira("unknown_status"));
    }

    @Test
    void testMapLinearPriorityToJira() {
        assertEquals("Medium", issueTransformer.mapLinearPriorityToJira(0)); // No priority
        assertEquals("Highest", issueTransformer.mapLinearPriorityToJira(1)); // Urgent
        assertEquals("High", issueTransformer.mapLinearPriorityToJira(2)); // High
        assertEquals("Medium", issueTransformer.mapLinearPriorityToJira(3)); // Normal
        assertEquals("Low", issueTransformer.mapLinearPriorityToJira(4)); // Low
        assertEquals("Medium", issueTransformer.mapLinearPriorityToJira(999)); // Unknown
    }

    @Test
    void testLabelSanitization() {
        // Arrange
        var state = new LinearIssue.LinearState("state-1", "In Progress", "started");
        var team = new LinearIssue.LinearTeam("team-1", "Engineering", "ENG");

        var label1 = new LinearIssue.LinearLabel("label-1", "needs review", "#ff0000");
        var label2 = new LinearIssue.LinearLabel("label-2", "high-priority!", "#00ff00");
        var label3 = new LinearIssue.LinearLabel("label-3", "feature/request", "#0000ff");
        var labels = new LinearIssue.LinearLabels(List.of(label1, label2, label3));

        var linearIssue = new LinearIssue(
                "linear-123", "ENG-123", "Test Issue", "Description", 2,
                state, null, null, team, labels,
                null, null,
                Instant.now(), Instant.now(), "https://linear.app/test/issue/ENG-123"
        );

        // Act
        var request = issueTransformer.mapLinearToJiraCreateRequest(linearIssue, "TEST", "Task");

        // Assert
        assertNotNull(request.fields.labels);
        assertEquals(3, request.fields.labels.size());

        // Labels should be sanitized for Jira
        assertTrue(request.fields.labels.contains("needs_review"));
        assertTrue(request.fields.labels.contains("high-priority_"));
        assertTrue(request.fields.labels.contains("feature_request"));
    }

    @Test
    void testMapLinearToJiraCreateRequest_EmptyLabels() {
        // Arrange
        var state = new LinearIssue.LinearState("state-1", "In Progress", "started");
        var team = new LinearIssue.LinearTeam("team-1", "Engineering", "ENG");
        var labels = new LinearIssue.LinearLabels(List.of()); // Empty list

        var linearIssue = new LinearIssue(
                "linear-123", "ENG-123", "Test Issue", "Description", 2,
                state, null, null, team, labels,
                null, null,
                Instant.now(), Instant.now(), "https://linear.app/test/issue/ENG-123"
        );

        // Act
        var request = issueTransformer.mapLinearToJiraCreateRequest(linearIssue, "TEST", "Task");

        // Assert
        assertNotNull(request.fields.labels);
        assertTrue(request.fields.labels.isEmpty());
    }

    @Test
    void testMapLinearToJiraCreateRequest_WithNullLabels() {
        // Arrange
        var state = new LinearIssue.LinearState("state-1", "In Progress", "started");
        var team = new LinearIssue.LinearTeam("team-1", "Engineering", "ENG");

        var validLabel = new LinearIssue.LinearLabel("label-1", "valid", "#ff0000");
        var nullLabel = new LinearIssue.LinearLabel("label-2", null, "#00ff00");
        var labels = new LinearIssue.LinearLabels(List.of(validLabel, nullLabel));

        var linearIssue = new LinearIssue(
                "linear-123", "ENG-123", "Test Issue", "Description", 2,
                state, null, null, team, labels,
                null, null,
                Instant.now(), Instant.now(), "https://linear.app/test/issue/ENG-123"
        );

        // Act
        var request = issueTransformer.mapLinearToJiraCreateRequest(linearIssue, "TEST", "Task");

        // Assert
        assertNotNull(request.fields.labels);
        assertEquals(1, request.fields.labels.size());
        assertEquals("valid", request.fields.labels.getFirst());
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

        // Assignee
        var assignee = new LinearIssue.LinearUser(
                "user-2",
                "Assigned User",
                "assigned@example.com",
                "Assigned User"
        );

        // Labels
        var bugLabel = new LinearIssue.LinearLabel(
                "label-1",
                "bug",
                "#ff0000"
        );

        var featureLabel = new LinearIssue.LinearLabel(
                "label-2",
                "feature",
                "#00ff00"
        );

        var labels = new LinearIssue.LinearLabels(
                List.of(bugLabel, featureLabel)
        );

        return new LinearIssue(
                "linear-123",
                "ENG-123",
                "Test Issue",
                "This is a test description\nwith multiple lines.",
                2, // High priority
                state,
                assignee,
                creator,
                team,
                labels,
                null,
                null,
                Instant.parse("2024-01-01T10:00:00Z"),
                Instant.parse("2024-01-02T10:00:00Z"),
                "https://linear.app/test/issue/ENG-123"
        );
    }

    private JiraIssue createTestJiraIssue(String summary) {
        var fields = new JiraIssue.JiraFields(
                summary, // summary
                null, // description
                null, // issuetype
                null, // priority
                null, // status
                null, // assignee
                null, // reporter
                null, // project
                null, // labels
                null, // created
                null, // updated
                null  // linearIssueId
        );

        return new JiraIssue("12345", "JIRA-123", "https://test.atlassian.net/rest/api/3/issue/12345", fields);
    }
}