package bogdanpc.linearsync.jira.entity;

public enum WorkflowStatus {
    TO_DO("To Do", "new"),
    IN_PROGRESS("In Progress", "indeterminate"),
    DONE("Done", "done");

    private final String statusName;
    private final String categoryKey;

    WorkflowStatus(String statusName, String categoryKey) {
        this.statusName = statusName;
        this.categoryKey = categoryKey;
    }

    public String statusName() {
        return statusName;
    }

    public boolean belongsTo(JiraIssue.JiraStatusCategory category) {
        return category != null && categoryKey.equals(category.key());
    }
}
