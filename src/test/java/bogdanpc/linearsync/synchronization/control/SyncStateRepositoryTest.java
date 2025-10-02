package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.synchronization.entity.SyncState;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SyncStateRepositoryTest {

    @Inject
    SyncStateRepository stateManager;

    private Path tempDir;
    private Path originalStateFile;
    private Path testStateFile;

    @BeforeEach
    void setUp() throws IOException {
        // Create temp directory
        tempDir = Files.createTempDirectory("state-test");

        // Save original state file path
        originalStateFile = stateManager.getStateFilePath();

        // Create test state file in temp directory
        testStateFile = tempDir.resolve(".syncstate.json");

        // Use reflection to set the state file path to test location
        // This is a simplified approach - in real tests you might use a test profile
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        // Restore original working directory first
        if (originalStateFile != null && originalStateFile.getParent() != null) {
            System.setProperty("user.dir", originalStateFile.getParent().toString());
        }

        // Clean up test files
        if (Files.exists(testStateFile)) {
            Files.delete(testStateFile);
        }

        // Clean up backup files in temp directory
        if (Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException _) {
                                // ignore
                            }
                        });
                Files.delete(tempDir);
            }
        }
    }

    @Test
    void testLoadState_NewFile() {
        // Create new StateManager with temp directory
        var testStateManager = new SyncStateRepository("current", 5);

        var state = testStateManager.loadState();

        assertNotNull(state);
        assertNotNull(state.syncedIssues);
        assertTrue(state.syncedIssues.isEmpty());
        assertNotNull(state.lastSyncTime);
        assertEquals("1.0", state.version);
    }

    @Test
    void testSaveAndLoadState() {
        var testStateManager = new SyncStateRepository("current", 5);

        var originalState = new SyncState();
        originalState.addSyncedIssue("linear-123", "JIRA-456", "jira-id-456");
        originalState.lastSyncTime = Instant.parse("2024-01-01T10:00:00Z");

        testStateManager.saveState(originalState);

        assertTrue(Files.exists(testStateManager.getStateFilePath()), "File should exists");

        var loadedState = testStateManager.loadState();

        assertNotNull(loadedState);
        assertEquals(1, loadedState.syncedIssues.size());
        assertTrue(loadedState.isIssueAlreadySynced("linear-123"));

        var syncedIssue = loadedState.getSyncedIssue("linear-123");
        assertNotNull(syncedIssue);
        assertEquals("linear-123", syncedIssue.linearIssueId);
        assertEquals("JIRA-456", syncedIssue.jiraIssueKey);
        assertEquals("jira-id-456", syncedIssue.jiraIssueId);
        assertEquals(SyncState.SyncStatus.SYNCED, syncedIssue.status);
    }

    @Test
    void testSaveState_UpdatesLastSyncTime() throws InterruptedException {
        var testStateManager = new SyncStateRepository("current", 5);

        var state = new SyncState();
        Instant beforeSave = Instant.now();

        Thread.sleep(1); // Ensure time difference

        testStateManager.saveState(state);

        assertTrue(state.lastSyncTime.isAfter(beforeSave));
    }

    @Test
    void testBackupState() throws IOException {
        var testStateManager = new SyncStateRepository("current", 5);

        // Create and save initial state
        var state = new SyncState();
        state.addSyncedIssue("test-123", "JIRA-123", "jira-123");
        testStateManager.saveState(state);

        testStateManager.backupState();

        var stateFile = testStateManager.getStateFilePath();
        var parentDir = stateFile.getParent();

        try (var fileList = Files.list(parentDir)) {
            var backupExists = fileList.anyMatch(path -> path.getFileName().toString().startsWith(".syncstate.json.backup."));

            assertTrue(backupExists, "Backup file should exist");
        }
    }

    @Test
    void testBackupState_NoExistingFile() {
        var testStateManager = new SyncStateRepository("current", 5);

        // Should not throw exception when no state file exists
        assertDoesNotThrow(testStateManager::backupState);
    }

    @Test
    void testDeleteState() {
        var testStateManager = new SyncStateRepository("current", 5);

        var state = new SyncState();
        testStateManager.saveState(state);

        assertTrue(Files.exists(testStateManager.getStateFilePath()), "State file exists");

        testStateManager.deleteState();

        assertFalse(Files.exists(testStateManager.getStateFilePath()), "State file was deleted");
    }

    @Test
    void testDeleteState_NoFile() {
        var testStateManager = new SyncStateRepository("current", 5);

        assertDoesNotThrow(testStateManager::deleteState, "Should not throw exception when no file exists");
    }

    @Test
    void testStateFileExists() {
        var testStateManager = new SyncStateRepository("current", 5);

        // Ensure clean state - delete any existing file from previous test runs
        testStateManager.deleteState();

        assertFalse(testStateManager.stateFileExists());

        var state = new SyncState();
        testStateManager.saveState(state);

        assertTrue(testStateManager.stateFileExists(), "State file exists");
    }

    @Test
    void testValidateState_ValidState() {
        var testStateManager = new SyncStateRepository("current", 5);

        var state = new SyncState();
        state.addSyncedIssue("linear-123", "JIRA-456", "jira-456");

        assertDoesNotThrow(() -> testStateManager.validateState(state), "Should not throw exception");
    }

    @Test
    void testValidateState_NullState() {
        var testStateManager = new SyncStateRepository("current", 5);

        var exception = assertThrows(IllegalArgumentException.class, () -> testStateManager.validateState(null));

        assertTrue(exception.getMessage().contains("Sync state cannot be null"));
    }

    @Test
    void testValidateState_InitializesNullFields() {
        var testStateManager = new SyncStateRepository("current", 5);

        var state = new SyncState();
        state.syncedIssues = null;
        state.version = null;

        testStateManager.validateState(state);

        assertNotNull(state.syncedIssues);
        assertNotNull(state.version);
        assertEquals("1.0", state.version);
    }

    @Test
    void testValidateState_RemovesInvalidEntries() {
        var testStateManager = new SyncStateRepository("current", 5);

        var state = new SyncState();

        // Add valid entry
        state.addSyncedIssue("linear-123", "JIRA-456", "jira-456");

        // Add invalid entry manually
        var invalidIssue = new SyncState.SyncedIssue();
        invalidIssue.linearIssueId = null; // Invalid - null ID
        invalidIssue.jiraIssueKey = "JIRA-789";
        state.syncedIssues.put("invalid-key", invalidIssue);

        assertEquals(2, state.syncedIssues.size());

        testStateManager.validateState(state);

        // Invalid entry should be removed
        assertEquals(1, state.syncedIssues.size());
        assertTrue(state.syncedIssues.containsKey("linear-123"));
        assertFalse(state.syncedIssues.containsKey("invalid-key"));
    }

    @Test
    void testLoadState_CorruptedFile() throws IOException {
        var testStateManager = new SyncStateRepository("current", 5);

        // Write corrupted JSON to state file
        var stateFile = testStateManager.getStateFilePath();
        Files.createDirectories(stateFile.getParent());
        Files.writeString(stateFile, "{ invalid json }");

        // Should create new state when file is corrupted
        var state = testStateManager.loadState();

        assertNotNull(state);
        assertTrue(state.syncedIssues.isEmpty());
        assertNotNull(state.lastSyncTime);
    }
}