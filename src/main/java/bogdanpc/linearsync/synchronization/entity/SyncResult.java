package bogdanpc.linearsync.synchronization.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SyncResult {

    public Instant startTime;
    public Instant endTime;
    public boolean success;
    public int createdCount = 0;
    public int updatedCount = 0;
    public int skippedCount = 0;
    public List<String> errors = new ArrayList<>();
    public List<IssueResult> issueResults = new ArrayList<>();

    public void addIssueResult(IssueResult result) {
        issueResults.add(result);

        switch (result.action) {
            case String s when s.equals("create") && result.success -> createdCount++;
            case String s when s.equals("update") && result.success -> updatedCount++;
            case String s when s.equals("skip") -> skippedCount++;
            default -> {}
        }

        if (!result.success && result.message != null) {
            errors.add(result.message);
        }
    }

    public void addError(String error) {
        errors.add(error);
    }

    public boolean hasChanges() {
        return createdCount > 0 || updatedCount > 0;
    }

    public long getDurationMillis() {
        if (startTime != null && endTime != null) {
            return endTime.toEpochMilli() - startTime.toEpochMilli();
        }
        return 0;
    }

    public String getSummary() {
        var summary = new StringBuilder();
        summary.append("Sync Result Summary:\n");
        summary.append("- Success: ").append(success).append("\n");
        summary.append("- Duration: ").append(getDurationMillis()).append("ms\n");
        summary.append("- Created: ").append(createdCount).append("\n");
        summary.append("- Updated: ").append(updatedCount).append("\n");
        summary.append("- Skipped: ").append(skippedCount).append("\n");
        summary.append("- Errors: ").append(errors.size()).append("\n");

        if (!errors.isEmpty()) {
            summary.append("\nErrors:\n");
            for (String error : errors) {
                summary.append("- ").append(error).append("\n");
            }
        }

        return summary.toString();
    }

    public static class IssueResult {
        public String linearIssueId;
        public String linearIdentifier;
        public String jiraIssueKey;
        public String action; // "create", "update", "skip"
        public boolean success;
        public String message;

        @Override
        public String toString() {
            return String.format("IssueResult{linear=%s, jira=%s, action=%s, success=%s, message='%s'}", linearIdentifier, jiraIssueKey, action, success, message);
        }
    }
}