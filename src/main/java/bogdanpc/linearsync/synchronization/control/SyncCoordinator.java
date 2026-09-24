package bogdanpc.linearsync.synchronization.control;

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
