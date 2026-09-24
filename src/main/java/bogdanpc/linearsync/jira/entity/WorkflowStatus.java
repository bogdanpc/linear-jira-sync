package bogdanpc.linearsync.jira.entity;

public enum WorkflowStatus {
    TO_DO("To Do"),
    IN_PROGRESS("In Progress"),
    DONE("Done");

    private final String statusName;

    WorkflowStatus(String statusName) {
        this.statusName = statusName;
    }

    public String statusName() {
        return statusName;
    }
}
