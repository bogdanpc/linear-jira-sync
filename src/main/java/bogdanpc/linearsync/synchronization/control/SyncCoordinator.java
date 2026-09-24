package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.synchronization.entity.SyncResult;
import bogdanpc.linearsync.synchronization.entity.SyncState;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class SyncCoordinator {

    private final SyncStateRepository stateRepository;
    private final SyncConfig syncConfig;

    public SyncCoordinator(SyncStateRepository stateRepository, SyncConfig syncConfig) {
        this.stateRepository = stateRepository;
        this.syncConfig = syncConfig;
    }

    public boolean isDryRun(boolean requested) {
        return requested || syncConfig.dryRun();
    }

    public SyncState prepareSync(boolean dryRun) {
        var state = stateRepository.loadState();
        stateRepository.validateState(state);

        if (!dryRun) {
            stateRepository.backupState();
        }

        return state;
    }

    /**
     * The sync time is when the run started, not when it ended, so issues edited in Linear during the run are fetched
     * next time. It only advances on a clean run: issues that failed to sync must fall inside the next fetch window.
     */
    public void advanceSyncTime(SyncState state, SyncResult result, Instant startedAt) {
        if (!result.success()) {
            Log.warn("Keeping the previous sync time so the failed issues are retried on the next run");
            return;
        }
        state.markSynced(startedAt);
    }

    public void completeSync(SyncState state, boolean hasChanges, boolean dryRun) {
        if (!dryRun && hasChanges) {
            stateRepository.saveState(state);
        }
    }

    public Instant determineUpdatedAfter(SyncState state, Instant requestedUpdatedAfter, boolean forceFullSync) {
        if (forceFullSync) {
            Log.debug("Full sync requested");
            return null;
        }

        if (requestedUpdatedAfter != null) {
            Log.debugf("Using requested filter: %s", requestedUpdatedAfter);
            return requestedUpdatedAfter;
        }

        var lastSync = state.lastSyncTime().orElse(null);
        Log.debugf(lastSync == null ? "No previous sync - full sync" : "Using last sync time: %s", lastSync);
        return lastSync;
    }
}
