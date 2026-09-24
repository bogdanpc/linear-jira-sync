package bogdanpc.linearsync.synchronization.entity;

public enum SyncAction {
    CREATE,
    UPDATE,
    /**
     * The Jira issue already existed but was missing from the sync state, for example after the state file was lost.
     */
    RECOVER,
    SKIP
}
