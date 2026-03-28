package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.synchronization.entity.SyncState;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

@ApplicationScoped
public class SyncCoordinator {

    private final SyncStateRepository stateRepository;
    private final SyncConfig syncConfig;

    private boolean dryRun;

    public SyncCoordinator(SyncStateRepository stateRepository, SyncConfig syncConfig) {
        this.stateRepository = stateRepository;
        this.syncConfig = syncConfig;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isDryRun() {
        return dryRun || syncConfig.dryRun();
    }

    public SyncState prepareSync() {
        var state = stateRepository.loadState();
        stateRepository.validateState(state);

        if (!isDryRun()) {
            stateRepository.backupState();
        }

        return state;
    }

    public void completeSync(SyncState state, boolean hasChanges) {
        if (!isDryRun() && hasChanges) {
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

        if (state.lastSyncTime != null) {
            Log.debugf("Using last sync time: %s", state.lastSyncTime);
            return state.lastSyncTime;
        }

        Log.debug("No previous sync - full sync");
        return null;
    }
}