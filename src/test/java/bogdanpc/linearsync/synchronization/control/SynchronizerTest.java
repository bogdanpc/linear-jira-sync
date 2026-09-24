package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.synchronization.entity.SyncAction;
import bogdanpc.linearsync.synchronization.entity.SyncState;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@ConnectWireMock
class SynchronizerTest {

    @Inject
    Synchronizer synchronizer;

    @Inject
    SyncStateRepository stateRepository;

    WireMock wiremock;

    @BeforeEach
    void setUp() {
        if (stateRepository.stateFileExists()) {
            stateRepository.deleteState();
        }
        WireMock.resetAllRequests();
    }

    @Test
    void testSynchronize_Success() {
        var result = synchronizer.synchronize("ENG", null, null, false, false);

        assertTrue(result.success(), "Result success should be true");
        assertEquals(1, result.count(SyncAction.CREATE));
        assertEquals(0, result.count(SyncAction.UPDATE));
        assertEquals(0, result.count(SyncAction.SKIP));

        verify(postRequestedFor(urlEqualTo("/linear/")));
        verify(postRequestedFor(urlEqualTo("/jira/rest/api/3/issue")));
    }

    @Test
    void testSynchronizeSingleIssue_Success() {
        var result = synchronizer.synchronizeSingleIssue("ENG-123", false);

        assertTrue(result.success());
        assertEquals(1, result.count(SyncAction.CREATE));

        verify(postRequestedFor(urlEqualTo("/linear/"))
                .withRequestBody(containing("GetIssue"))
                .withRequestBody(matchingJsonPath("$.variables[?(@.id == 'ENG-123')]")));

        verify(postRequestedFor(urlEqualTo("/jira/rest/api/3/issue"))
                .withRequestBody(matchingJsonPath("$.fields[?(@.summary == '[ENG-123] Test Issue')]")));
    }

    @Test
    void testSynchronizeSingleIssue_NotFound() {
        var result = synchronizer.synchronizeSingleIssue("ENG-999", false);

        assertFalse(result.success());
        assertEquals(0, result.count(SyncAction.CREATE));
        assertEquals(1, result.allErrors().size());

        verify(postRequestedFor(urlEqualTo("/linear/"))
                .withRequestBody(containing("GetIssue")));
        verify(0, postRequestedFor(urlEqualTo("/jira/rest/api/3/issue")));
    }

    @Test
    void testSynchronize_DryRun() {
        var result = synchronizer.synchronize("ENG", null, null, false, true);

        assertTrue(result.success());
        assertEquals(1, result.count(SyncAction.CREATE), "Dry-run counts what would be created");
        assertEquals(0, result.count(SyncAction.UPDATE));

        verify(postRequestedFor(urlEqualTo("/linear/")));
        verify(0, postRequestedFor(urlEqualTo("/jira/rest/api/3/issue")));
    }

    @Test
    void testSynchronize_UpdatedChildOfSyncedParent() {
        var state = new SyncState();
        state.recordSync("parent-1", "TEST-100", "100", Instant.parse("2024-01-01T00:00:00Z"));
        stateRepository.saveState(state);

        var stub = wiremock.register(post(urlEqualTo("/linear/"))
                .atPriority(1)
                .withRequestBody(containing("GetIssues"))
                .withRequestBody(containing("\"key\":{\"eq\":\"CHILD\"}"))
                .willReturn(okJson("""
                        {"data": {"issues": {
                          "nodes": [{
                            "id": "issue-child",
                            "identifier": "ENG-123",
                            "title": "Test Issue",
                            "state": {"id": "state-1", "name": "Todo", "type": "unstarted"},
                            "parent": {"id": "parent-1", "identifier": "ENG-100", "title": "Parent"},
                            "createdAt": "2024-01-01T10:00:00Z",
                            "updatedAt": "2024-01-02T10:00:00Z"
                          }],
                          "pageInfo": {"hasNextPage": false, "endCursor": null}
                        }}}
                        """)));

        var projectStub = wiremock.register(get(urlEqualTo("/jira/rest/api/3/project/TEST"))
                .willReturn(okJson("""
                        {"key": "TEST", "issueTypes": [{"id": "1", "name": "Sub-task", "subtask": true}]}
                        """)));

        try {
            var result = synchronizer.synchronize("CHILD", null, null, true, false);

            assertTrue(result.success(), () -> String.join("\n", result.allErrors()));
            assertEquals(1, result.count(SyncAction.CREATE));
            verify(postRequestedFor(urlEqualTo("/jira/rest/api/3/issue"))
                    .withRequestBody(matchingJsonPath("$.fields.parent[?(@.key == 'TEST-100')]"))
                    .withRequestBody(matchingJsonPath("$.fields.issuetype[?(@.name == 'Sub-task')]"))
                    .withRequestBody(matchingJsonPath("$.fields[?(@.customfield_10000 == 'issue-child')]")));
            verify(0, postRequestedFor(urlEqualTo("/linear/")).withRequestBody(containing("GetIssue(")));
        } finally {
            wiremock.removeStubMapping(stub);
            wiremock.removeStubMapping(projectStub);
        }
    }
}
