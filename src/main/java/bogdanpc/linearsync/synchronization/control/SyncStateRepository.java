package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.synchronization.entity.SyncState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;

@ApplicationScoped
public class SyncStateRepository {

    private static final String STATE_FILE_NAME = ".syncstate.json";
    private static final String APP_NAME = "linear-jira-sync";

    private final ObjectMapper objectMapper;
    private final Path stateFilePath;
    private final int maxBackups;

    public SyncStateRepository(SyncConfig syncConfig) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.maxBackups = syncConfig.storage().maxBackups();
        this.stateFilePath = resolveStateFilePath(syncConfig.storage().location().orElse(null));
        Log.debugf("State file: %s", stateFilePath);
    }

    private static boolean isBackupPath(Path p) {
        return p.getFileName().toString().startsWith(STATE_FILE_NAME + ".backup");
    }

    private Path resolveStateFilePath(String storageLocation) {
        if (storageLocation == null || storageLocation.isBlank()) {
            return Paths.get(getPlatformDataDirectory().toString(), STATE_FILE_NAME);
        }
        return Paths.get(storageLocation, STATE_FILE_NAME);
    }

    private Path getPlatformDataDirectory() {
        // Simple dotfile directory: ~/.linear-jira-sync
        // Traditional Unix approach, works on all platforms
        return Paths.get(System.getProperty("user.home"), "." + APP_NAME);
    }

    public SyncState loadState() {
        Log.debugf("Loading state from: %s", stateFilePath);

        if (!Files.exists(stateFilePath)) {
            Log.debug("No state file found, starting fresh");
            return createNewState();
        }

        try {
            var content = Files.readString(stateFilePath);
            var state = objectMapper.readValue(content, SyncState.class);
            Log.debugf("Loaded state: %d issues tracked", state.syncedIssues.size());
            return state;
        } catch (IOException e) {
            Log.errorf(e, "Failed to load state from: %s", stateFilePath);
            Log.warn("Starting with fresh state");
            return createNewState();
        }
    }

    public void saveState(SyncState state) {
        Log.debugf("Saving state to: %s", stateFilePath);

        try {
            state.updateLastSyncTime();

            var json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);

            var parentDir = stateFilePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(stateFilePath, json);
            Log.debugf("Saved state: %d issues tracked", state.syncedIssues.size());

        } catch (IOException e) {
            Log.errorf(e, "Failed to save state");
            throw new RuntimeException("Failed to save sync state", e);
        }
    }

    public void backupState() {
        if (!Files.exists(stateFilePath)) {
            return;
        }

        try {
            var backupPath = Paths.get(stateFilePath + ".backup." + System.currentTimeMillis());
            Files.copy(stateFilePath, backupPath);
            Log.debugf("Created state backup");
            rotateBackups();
        } catch (IOException e) {
            Log.warnf(e, "Failed to backup state");
        }
    }

    private void rotateBackups() {
        var backupDir = stateFilePath.getParent();
        if (backupDir == null || !Files.exists(backupDir)) {
            return;
        }

        try (var backups = Files.list(backupDir)) {
            var sortedBackups = backups.filter(SyncStateRepository::isBackupPath)
                    .sorted(Comparator.reverseOrder()).toList();

            if (sortedBackups.size() <= maxBackups) {
                return;
            }
            sortedBackups.forEach(SyncStateRepository::deleteFile);

        } catch (IOException e) {
            Log.warnf(e, "Failed to rotate backups in directory: %s", backupDir);
        }
    }

    private static void deleteFile(Path f) {
        try {
            Files.delete(f);
            Log.debugf("Deleted old backup: %s", f);
        } catch (IOException e) {
            Log.warnf(e, "Failed to delete old backup: %s", f);
        }
    }

    public boolean stateFileExists() {
        return Files.exists(stateFilePath);
    }

    public Path getStateFilePath() {
        return stateFilePath;
    }

    public void deleteState() {
        try {
            if (Files.exists(stateFilePath)) {
                Files.delete(stateFilePath);
                Log.info("Deleted sync state file");
            }
        } catch (IOException e) {
            Log.errorf(e, "Failed to delete sync state file");
            throw new RuntimeException("Failed to delete sync state", e);
        }
    }

    private SyncState createNewState() {
        var state = new SyncState();
        state.lastSyncTime = Instant.now();
        return state;
    }

    public void validateState(SyncState state) {
        if (state == null) {
            throw new IllegalArgumentException("Sync state cannot be null");
        }

        if (state.syncedIssues == null) {
            Log.warn("Sync state has null syncedIssues map, initializing empty map");
            state.syncedIssues = new HashMap<>();
        }

        if (state.version == null) {
            Log.warn("Sync state has no version, setting to 1.0");
            state.version = "1.0";
        }

        // Validate each synced issue
        state.syncedIssues.entrySet().removeIf(entry -> {
            SyncState.SyncedIssue issue = entry.getValue();
            if (issue.linearIssueId == null || issue.jiraIssueKey == null) {
                Log.warnf("Removing invalid synced issue entry: %s", entry.getKey());
                return true;
            }
            return false;
        });
    }
}