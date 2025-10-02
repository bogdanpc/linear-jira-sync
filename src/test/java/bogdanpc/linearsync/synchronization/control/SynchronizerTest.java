package bogdanpc.linearsync.synchronization.control;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@ConnectWireMock
class SynchronizerTest {

    @Inject
    Synchronizer synchronizer;

    @Inject
    SyncStateRepository stateRepository;

    @BeforeEach
    void setUp() {

        if (stateRepository.stateFileExists()) {
            stateRepository.deleteState();
        }
        WireMock.resetAllRequests();

        synchronizer.setDryRun(false);
    }

    @Test
    void testSynchronize_Success() {

        var result = synchronizer.synchronize("ENG", null, null, false);

        assertTrue(result.success, "Result success should be true");
        assertEquals(1, result.createdCount);
        assertEquals(0, result.updatedCount);
        assertEquals(0, result.skippedCount);

        verify(postRequestedFor(urlEqualTo("/linear/")));

        verify(postRequestedFor(urlEqualTo("/jira/rest/api/3/issue")));
    }

    @Test
    void testSynchronizeSingleIssue_Success() {

        var result = synchronizer.synchronizeSingleIssue("ENG-123");

        assertTrue(result.success);
        assertEquals(1, result.createdCount);

        verify(postRequestedFor(urlEqualTo("/linear/"))
                .withRequestBody(containing("GetIssue"))
                .withRequestBody(matchingJsonPath("$.variables[?(@.id == 'ENG-123')]")));

        verify(postRequestedFor(urlEqualTo("/jira/rest/api/3/issue"))
                .withRequestBody(matchingJsonPath("$.fields[?(@.summary == '[ENG-123] Test Issue')]")));
    }

    @Test
    void testSynchronizeSingleIssue_NotFound() {
        var result = synchronizer.synchronizeSingleIssue("ENG-999");

        assertFalse(result.success);
        assertEquals(0, result.createdCount);
        assertEquals(1, result.errors.size());

        verify(postRequestedFor(urlEqualTo("/linear/"))
                .withRequestBody(containing("GetIssue")));
        verify(0, postRequestedFor(urlEqualTo("/jira/rest/api/3/issue")));
    }

    @Test
    void testSynchronize_DryRun() {
        synchronizer.setDryRun(true);

        var result = synchronizer.synchronize("ENG", null, null, false);

        assertTrue(result.success);
        // In dry run, counts reflect what would be created/updated
        assertEquals(1, result.createdCount);
        assertEquals(0, result.updatedCount);

        // Verify Linear API was called
        verify(postRequestedFor(urlEqualTo("/linear/")));

        // Verify Jira API was NOT called in dry run (no actual creation)
        verify(0, postRequestedFor(urlEqualTo("/jira/rest/api/3/issue")));
    }

    @Test
    void testSetDryRun() {
        synchronizer.setDryRun(true);

        // No API calls needed for this test
        // Just verify the method works without error
        assertDoesNotThrow(() -> synchronizer.setDryRun(false));
    }
}
