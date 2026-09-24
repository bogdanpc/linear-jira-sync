package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraTransition;
import bogdanpc.linearsync.jira.entity.WorkflowStatus;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

@ApplicationScoped
public class TransitionOperations {

    private final JiraClient jiraClient;
    private final JiraConfig config;

    TransitionOperations(@RestClient JiraClient jiraClient, JiraConfig config) {
        this.jiraClient = jiraClient;
        this.config = config;
    }

    public void transitionIfNeeded(String jiraIssueKey, WorkflowStatus target) {
        if (!config.statusSyncEnabled()) {
            Log.debugf("Status sync disabled, skipping transition for %s", jiraIssueKey);
            return;
        }

        if (target == null) {
            Log.debugf("No target status for %s, skipping transition", jiraIssueKey);
            return;
        }

        try {
            var fields = jiraClient.getIssue(jiraIssueKey).fields();
            var currentStatus = fields != null ? fields.status() : null;
            if (currentStatus != null && target.belongsTo(currentStatus.statusCategory())) {
                Log.debugf("Issue %s already in status '%s', no transition needed", jiraIssueKey, currentStatus.name());
                return;
            }

            var currentName = currentStatus != null ? currentStatus.name() : null;
            findTransition(jiraIssueKey, target).ifPresentOrElse(
                    transition -> performTransition(jiraIssueKey, transition, currentName),
                    () -> Log.warnf("No transition found to move %s from '%s' to '%s'", jiraIssueKey, currentName,
                            target.statusName()));
        } catch (RuntimeException e) {
            Log.errorf(e, "Failed to transition issue %s to status '%s'", jiraIssueKey, target.statusName());
        }
    }

    /**
     * Prefers the status named like the target so a workflow with several statuses in one category (e.g. "In Review"
     * and "In Progress") still lands on the expected one, and falls back to any status of the target's category.
     */
    private Optional<JiraTransition> findTransition(String jiraIssueKey, WorkflowStatus target) {
        var transitions = jiraClient.getTransitions(jiraIssueKey).transitions();
        if (transitions == null) {
            return Optional.empty();
        }

        var reachable = transitions.stream().filter(t -> t.to() != null).toList();
        return reachable.stream()
                .filter(t -> target.statusName().equalsIgnoreCase(t.to().name()))
                .findFirst()
                .or(() -> reachable.stream().filter(t -> target.belongsTo(t.to().statusCategory())).findFirst());
    }

    private void performTransition(String jiraIssueKey, JiraTransition transition, String fromStatus) {
        var request = new JiraTransition.TransitionRequest(
                new JiraTransition.TransitionRequest.TransitionId(transition.id())
        );
        jiraClient.doTransition(jiraIssueKey, request);
        Log.infof("Transitioned %s: '%s' → '%s'", jiraIssueKey, fromStatus, transition.to().name());
    }
}
