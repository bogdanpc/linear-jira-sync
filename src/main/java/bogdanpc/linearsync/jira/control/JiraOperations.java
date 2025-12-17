package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraIssue;
import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import bogdanpc.linearsync.jira.entity.JiraProject;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JiraOperations {

    private final IssueOperations issueOperations;
    private final SearchOperations searchOperations;
    private final CommentOperations commentOperations;
    private final AttachmentOperations attachmentOperations;
    private final TransitionOperations transitionOperations;

    public JiraOperations(IssueOperations issueOperations, SearchOperations searchOperations, CommentOperations commentOperations, AttachmentOperations attachmentOperations, TransitionOperations transitionOperations) {
        this.issueOperations = issueOperations;
        this.searchOperations = searchOperations;
        this.commentOperations = commentOperations;
        this.attachmentOperations = attachmentOperations;
        this.transitionOperations = transitionOperations;
    }


    public JiraIssue createIssue(JiraIssueInput issueInput) {
        return issueOperations.createIssue(issueInput);
    }


    public void updateIssue(String jiraIssueKey, JiraIssueInput issueInput) {
        issueOperations.updateIssue(jiraIssueKey, issueInput);
    }

    public Optional<JiraIssue> findIssueBySourceId(String sourceIssueId) {
        return searchOperations.findIssueBySourceId(sourceIssueId);
    }

    public Optional<JiraIssue> findIssueByIdentifier(String sourceIdentifier) {
        return searchOperations.findIssueByIdentifierInSummary(sourceIdentifier);
    }

    public List<JiraIssue> getAllIssuesInProject() {
        return searchOperations.getAllIssuesInProject();
    }

    public boolean testConnection() {
        return issueOperations.testConnection();
    }

    public void syncComments(String jiraIssueKey, JiraIssueInput issueInput) {
        commentOperations.syncComments(jiraIssueKey, issueInput);
    }

    public void syncAttachments(String jiraIssueKey, JiraIssueInput issueInput) {
        attachmentOperations.syncAttachments(jiraIssueKey, issueInput);
    }

    public List<JiraProject.IssueType> getProjectIssueTypes() {
        return searchOperations.getProjectIssueTypes();
    }

    public void transitionIssueStatus(String jiraIssueKey, String linearStateType) {
        transitionOperations.transitionIfNeeded(jiraIssueKey, linearStateType);
    }

}