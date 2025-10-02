package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.synchronization.entity.SyncState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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

    public SyncStateRepository(@ConfigProperty(name = "sync.storage.location") String storageLocation, @ConfigProperty(name = "sync.storage.max-backups", defaultValue = "5") int maxBackups) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.maxBackups = maxBackups;
        this.stateFilePath = resolveStateFilePath(storageLocation);
        Log.infof("Using state file location: %s", stateFilePath);
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
        Log.debugf("Loading sync state from: %s", stateFilePath);

        if (!Files.exists(stateFilePath)) {
            Log.info("No existing sync state file found, creating new state");
            return createNewState();
        }

        try {
            var content = Files.readString(stateFilePath);
            var state = objectMapper.readValue(content, SyncState.class);
            Log.infof("Loaded sync state with %d synced issues, last sync: %s", state.syncedIssues.size(), state.lastSyncTime);
            return state;
        } catch (IOException e) {
            Log.errorf(e, "Failed to load sync state from: %s", stateFilePath);
            Log.warn("Creating new sync state due to load failure");
            return createNewState();
        }
    }

    public void saveState(SyncState state) {
        Log.debugf("Saving sync state to: %s", stateFilePath);

        try {
            // Update the last sync time before saving
            state.updateLastSyncTime();

            var json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);

            // Create parent directories if they don't exist
            var parentDir = stateFilePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(stateFilePath, json);
            Log.infof("Saved sync state with %d synced issues", state.syncedIssues.size());

        } catch (IOException e) {
            Log.errorf(e, "Failed to save sync state to: %s", stateFilePath);
            throw new RuntimeException("Failed to save sync state", e);
        }
    }

    public void backupState() {
        if (!Files.exists(stateFilePath)) {
            Log.debug("No state file to backup");
            return;
        }

        try {
            var backupPath = Paths.get(stateFilePath + ".backup." + System.currentTimeMillis());
            Files.copy(stateFilePath, backupPath);
            Log.infof("Created backup of sync state at: %s", backupPath);

            // Rotate old backups to prevent unlimited growth
            rotateBackups();
        } catch (IOException e) {
            Log.errorf(e, "Failed to create backup of sync state");
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
            for (int i = maxBackups; i < sortedBackups.size(); i++) {
                try {
                    Files.delete(sortedBackups.get(i));
                    Log.debugf("Deleted old backup: %s", sortedBackups.get(i));
                } catch (IOException e) {
                    Log.warnf(e, "Failed to delete old backup: %s", sortedBackups.get(i));
                }
            }
        } catch (IOException e) {
            Log.warnf(e, "Failed to rotate backups in directory: %s", backupDir);
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