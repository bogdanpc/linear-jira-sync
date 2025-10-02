package bogdanpc.linearsync.synchronization.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SyncState {

    @JsonProperty("lastSyncTime")
    public Instant lastSyncTime;

    @JsonProperty("syncedIssues")
    public Map<String, SyncedIssue> syncedIssues = new HashMap<>();

    @JsonProperty("version")
    public String version = "1.0";

    public static class SyncedIssue {
        @JsonProperty("linearIssueId")
        public String linearIssueId;

        @JsonProperty("jiraIssueKey")
        public String jiraIssueKey;

        @JsonProperty("jiraIssueId")
        public String jiraIssueId;

        @JsonProperty("lastSyncTime")
        public Instant lastSyncTime;

        @JsonProperty("linearUpdatedAt")
        public Instant linearUpdatedAt;

        @JsonProperty("jiraUpdatedAt")
        public Instant jiraUpdatedAt;

        @JsonProperty("status")
        public SyncStatus status = SyncStatus.SYNCED;

        @JsonProperty("syncedAttachments")
        public Set<String> syncedAttachments = new HashSet<>();

        public SyncedIssue() {}

        public SyncedIssue(String linearIssueId, String jiraIssueKey, String jiraIssueId) {
            this.linearIssueId = linearIssueId;
            this.jiraIssueKey = jiraIssueKey;
            this.jiraIssueId = jiraIssueId;
            this.lastSyncTime = Instant.now();
        }
    }

    public enum SyncStatus {
        SYNCED,
        ERROR
    }

    public void addSyncedIssue(String linearIssueId, String jiraIssueKey, String jiraIssueId) {
        syncedIssues.put(linearIssueId, new SyncedIssue(linearIssueId, jiraIssueKey, jiraIssueId));
    }

    public SyncedIssue getSyncedIssue(String linearIssueId) {
        return syncedIssues.get(linearIssueId);
    }

    public boolean isIssueAlreadySynced(String linearIssueId) {
        return syncedIssues.containsKey(linearIssueId);
    }

    public void updateLastSyncTime() {
        this.lastSyncTime = Instant.now();
    }

    public void markAttachmentSynced(String linearIssueId, String attachmentId) {
        var syncedIssue = getSyncedIssue(linearIssueId);
        if (syncedIssue != null) {
            syncedIssue.syncedAttachments.add(attachmentId);
        }
    }

    public boolean isAttachmentAlreadySynced(String linearIssueId, String attachmentId) {
        var syncedIssue = getSyncedIssue(linearIssueId);
        return syncedIssue != null && syncedIssue.syncedAttachments.contains(attachmentId);
    }
}