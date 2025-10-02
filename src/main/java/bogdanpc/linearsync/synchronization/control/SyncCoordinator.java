package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.synchronization.entity.SyncState;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Instant;

@ApplicationScoped
public class SyncCoordinator {

    private final SyncStateRepository stateRepository;

    @ConfigProperty(name = "sync.dry-run", defaultValue = "false")
    boolean configDryRun;

    private boolean dryRun;

    public SyncCoordinator(SyncStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isDryRun() {
        return dryRun || configDryRun;
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
            Log.info("Force full sync requested - ignoring update time filters");
            return null;
        }

        if (requestedUpdatedAfter != null) {
            Log.infof("Using requested updated after time: %s", requestedUpdatedAfter);
            return requestedUpdatedAfter;
        }

        if (state.lastSyncTime != null) {
            Log.infof("Using last sync time as updated after: %s", state.lastSyncTime);
            return state.lastSyncTime;
        }

        Log.info("No previous sync time found - performing full sync");
        return null;
    }
}