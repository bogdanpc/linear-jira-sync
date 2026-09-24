package bogdanpc.linearsync.synchronization.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncState {

    private static final String CURRENT_VERSION = "1.0";

    @JsonProperty
    private Instant lastSyncTime;

    @JsonProperty
    private final Map<String, SyncedIssue> syncedIssues;

    @JsonProperty
    private final String version;

    public SyncState() {
        this(null, null, null);
    }

    @JsonCreator
    SyncState(@JsonProperty("lastSyncTime") Instant lastSyncTime,
            @JsonProperty("syncedIssues") Map<String, SyncedIssue> syncedIssues,
            @JsonProperty("version") String version) {
        this.lastSyncTime = lastSyncTime;
        this.syncedIssues = syncedIssues == null ? new HashMap<>() : new HashMap<>(syncedIssues);
        this.version = version == null ? CURRENT_VERSION : version;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SyncedIssue(String linearIssueId, String jiraIssueKey, String jiraIssueId, Instant lastSyncTime,
            Instant linearUpdatedAt, Set<String> syncedAttachments) {

        public SyncedIssue {
            syncedAttachments = syncedAttachments == null ? Set.of() : Set.copyOf(syncedAttachments);
        }

        boolean isValid() {
            return linearIssueId != null && jiraIssueKey != null;
        }

        public boolean isOutdatedBy(Instant linearUpdatedAt) {
            if (this.linearUpdatedAt == null) {
                return true;
            }
            return linearUpdatedAt != null && linearUpdatedAt.isAfter(this.linearUpdatedAt);
        }

        SyncedIssue withAttachment(String attachmentId) {
            var attachments = new HashSet<>(syncedAttachments);
            attachments.add(attachmentId);
            return new SyncedIssue(linearIssueId, jiraIssueKey, jiraIssueId, lastSyncTime, linearUpdatedAt,
                    attachments);
        }
    }

    public Optional<Instant> lastSyncTime() {
        return Optional.ofNullable(lastSyncTime);
    }

    public void markSynced(Instant syncTime) {
        this.lastSyncTime = syncTime;
    }

    public String version() {
        return version;
    }

    public int trackedIssueCount() {
        return syncedIssues.size();
    }

    public Optional<SyncedIssue> syncedIssue(String linearIssueId) {
        return Optional.ofNullable(syncedIssues.get(linearIssueId));
    }

    public void recordSync(String linearIssueId, String jiraIssueKey, String jiraIssueId, Instant linearUpdatedAt) {
        var attachments = syncedIssue(linearIssueId).map(SyncedIssue::syncedAttachments).orElse(Set.of());
        syncedIssues.put(linearIssueId, new SyncedIssue(linearIssueId, jiraIssueKey, jiraIssueId, Instant.now(),
                linearUpdatedAt, attachments));
    }

    public void markAttachmentSynced(String linearIssueId, String attachmentId) {
        syncedIssues.computeIfPresent(linearIssueId, (_, issue) -> issue.withAttachment(attachmentId));
    }

    public boolean isAttachmentAlreadySynced(String linearIssueId, String attachmentId) {
        return syncedIssue(linearIssueId).map(issue -> issue.syncedAttachments().contains(attachmentId)).orElse(false);
    }

    public Set<String> removeInvalidEntries() {
        var invalid = new HashSet<String>();
        syncedIssues.forEach((key, issue) -> {
            if (issue == null || !issue.isValid()) {
                invalid.add(key);
            }
        });
        invalid.forEach(syncedIssues::remove);
        return invalid;
    }
}
