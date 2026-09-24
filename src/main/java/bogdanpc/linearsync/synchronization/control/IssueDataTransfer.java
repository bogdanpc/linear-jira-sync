package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.jira.entity.JiraIssueInput;
import bogdanpc.linearsync.jira.entity.WorkflowStatus;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.linear.entity.LinearStateType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Transfers LinearIssue entities to JiraIssueInput entities for proper BCE compliance.
 * This service ensures that the Jira business component doesn't depend on entities
 * from the Linear business component.
 */
@ApplicationScoped
public class IssueDataTransfer {

    public JiraIssueInput mapToJiraIssueInput(LinearIssue linearIssue, String parentJiraKey) {
        if (linearIssue == null) {
            return null;
        }

        return new JiraIssueInput(
                linearIssue.id(),
                linearIssue.identifier(),
                linearIssue.title(),
                linearIssue.description(),
                linearIssue.priority(),
                linearIssue.state() != null ? workflowStatus(linearIssue.state().type()) : null,
                linearIssue.assignee() != null ? linearIssue.assignee().email() : null,
                linearIssue.assignee() != null ? linearIssue.assignee().displayName() : null,
                linearIssue.creator() != null ? linearIssue.creator().email() : null,
                linearIssue.creator() != null ? linearIssue.creator().displayName() : null,
                linearIssue.team() != null ? linearIssue.team().name() : null,
                linearIssue.team() != null ? linearIssue.team().key() : null,
                mapLabels(linearIssue),
                mapComments(linearIssue),
                mapAttachments(linearIssue),
                linearIssue.createdAt(),
                linearIssue.updatedAt(),
                linearIssue.url(),
                parentJiraKey);
    }

    private static WorkflowStatus workflowStatus(LinearStateType stateType) {
        return switch (stateType) {
            case null -> null;
            case TRIAGE, BACKLOG, UNSTARTED -> WorkflowStatus.TO_DO;
            case STARTED -> WorkflowStatus.IN_PROGRESS;
            case COMPLETED, CANCELED -> WorkflowStatus.DONE;
        };
    }

    private List<JiraIssueInput.LabelInput> mapLabels(LinearIssue linearIssue) {
        if (linearIssue.labels() == null || linearIssue.labels().nodes() == null) {
            return List.of();
        }

        return linearIssue.labels().nodes().stream()
            .map(label -> new JiraIssueInput.LabelInput(
                label.name(),
                label.color()
            ))
            .toList();
    }

    private List<JiraIssueInput.CommentInput> mapComments(LinearIssue linearIssue) {
        if (linearIssue.comments() == null || linearIssue.comments().nodes() == null) {
            return List.of();
        }

        return linearIssue.comments().nodes().stream()
            .map(comment -> new JiraIssueInput.CommentInput(
                comment.id(),
                comment.body(),
                comment.user() != null ? comment.user().name() : null,
                comment.user() != null ? comment.user().displayName() : null,
                comment.user() != null ? comment.user().email() : null,
                comment.createdAt(),
                comment.updatedAt(),
                comment.url()
            ))
            .toList();
    }

    private List<JiraIssueInput.AttachmentInput> mapAttachments(LinearIssue linearIssue) {
        if (linearIssue.attachments() == null || linearIssue.attachments().nodes() == null) {
            return List.of();
        }

        return linearIssue.attachments().nodes().stream()
            .map(attachment -> new JiraIssueInput.AttachmentInput(
                attachment.id(),
                attachment.title(),
                attachment.url(),
                attachment.sourceType(),
                attachment.creator() != null ? attachment.creator().name() : null,
                attachment.creator() != null ? attachment.creator().displayName() : null,
                attachment.creator() != null ? attachment.creator().email() : null,
                attachment.createdAt()
            ))
            .toList();
    }
}