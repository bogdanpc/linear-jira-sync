package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.synchronization.entity.SyncState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SyncStateRepositoryTest {

    @TempDir
    Path tempDir;

    SyncStateRepository repository;

    static SyncConfig testSyncConfig(String location, int maxBackups) {
        return new SyncConfig() {
            @Override
            public boolean dryRun() {
                return false;
            }

            @Override
            public Storage storage() {
                return new Storage() {
                    @Override
                    public Optional<String> location() {
                        return Optional.ofNullable(location);
                    }

                    @Override
                    public int maxBackups() {
                        return maxBackups;
                    }
                };
            }
        };
    }

    @BeforeEach
    void setUp() {
        repository = new SyncStateRepository(testSyncConfig(tempDir.toString(), 2));
    }

    @Test
    void newStateTriggersFullSync() {
        var state = repository.loadState();

        assertEquals(0, state.trackedIssueCount());
        assertTrue(state.lastSyncTime().isEmpty(), "New state must trigger a full sync");
        assertEquals("1.0", state.version());
    }

    @Test
    void savedStateLoadsBack() {
        var state = new SyncState();
        state.recordSync("linear-123", "JIRA-456", "jira-id-456", Instant.parse("2024-01-01T10:00:00Z"));
        state.markAttachmentSynced("linear-123", "attachment-1");

        repository.saveState(state);
        var loaded = repository.loadState();

        assertTrue(loaded.lastSyncTime().isPresent(), "Saving records the sync time");
        var issue = loaded.syncedIssue("linear-123").orElseThrow();
        assertEquals("JIRA-456", issue.jiraIssueKey());
        assertEquals("jira-id-456", issue.jiraIssueId());
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), issue.linearUpdatedAt());
        assertTrue(loaded.isAttachmentAlreadySynced("linear-123", "attachment-1"));
    }

    @Test
    void stateFromEarlierVersionLoads() throws IOException {
        Files.writeString(repository.getStateFilePath(), """
                {
                  "lastSyncTime": "2024-01-01T10:00:00Z",
                  "version": "1.0",
                  "syncedIssues": {
                    "linear-123": {
                      "linearIssueId": "linear-123",
                      "jiraIssueKey": "JIRA-456",
                      "jiraIssueId": "456",
                      "jiraUpdatedAt": null,
                      "status": "ERROR",
                      "syncedAttachments": ["attachment-1"]
                    }
                  }
                }
                """);

        var state = repository.loadState();

        assertEquals(Optional.of(Instant.parse("2024-01-01T10:00:00Z")), state.lastSyncTime());
        assertTrue(state.isAttachmentAlreadySynced("linear-123", "attachment-1"));
    }

    @Test
    void corruptedFileStartsFresh() throws IOException {
        Files.writeString(repository.getStateFilePath(), "{ invalid json }");

        var state = repository.loadState();

        assertEquals(0, state.trackedIssueCount());
        assertTrue(state.lastSyncTime().isEmpty(), "New state must trigger a full sync");
    }

    @Test
    void validationRemovesEntriesWithoutIdentifiers() throws IOException {
        Files.writeString(repository.getStateFilePath(), """
                {"syncedIssues": {
                  "linear-123": {"linearIssueId": "linear-123", "jiraIssueKey": "JIRA-456"},
                  "invalid": {"jiraIssueKey": "JIRA-789"}
                }}
                """);
        var state = repository.loadState();

        repository.validateState(state);

        assertEquals(1, state.trackedIssueCount());
        assertTrue(state.syncedIssue("linear-123").isPresent());
    }

    @Test
    void backupsAreRotated() throws IOException, InterruptedException {
        repository.saveState(new SyncState());

        for (var i = 0; i < 4; i++) {
            repository.backupState();
            Thread.sleep(2); // backups are named by millisecond
        }

        try (var files = Files.list(tempDir)) {
            var backups = files.filter(path -> path.getFileName().toString().startsWith(".syncstate.json.backup."))
                    .count();
            assertEquals(2, backups);
        }
    }
}
