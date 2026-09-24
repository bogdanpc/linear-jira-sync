package bogdanpc.linearsync.synchronization.entity;

public record IssueResult(String linearIdentifier, String jiraIssueKey, SyncAction action, boolean success,
        String message) {

    public static IssueResult succeeded(String linearIdentifier, String jiraIssueKey, SyncAction action,
            String message) {
        return new IssueResult(linearIdentifier, jiraIssueKey, action, true, message);
    }

    public static IssueResult failed(String linearIdentifier, String jiraIssueKey, SyncAction action, String message) {
        return new IssueResult(linearIdentifier, jiraIssueKey, action, false, message);
    }
}
