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

        var targetStatus = target.statusName();

        try {
            var currentIssue = jiraClient.getIssue(jiraIssueKey);
            var currentStatus = currentIssue.fields() != null && currentIssue.fields().status() != null
                    ? currentIssue.fields().status().name()
                    : null;

            if (targetStatus.equalsIgnoreCase(currentStatus)) {
                Log.debugf("Issue %s already in status '%s', no transition needed", jiraIssueKey, currentStatus);
                return;
            }

            var transitionId = findTransitionToStatus(jiraIssueKey, targetStatus);
            if (transitionId.isPresent()) {
                performTransition(jiraIssueKey, transitionId.get(), currentStatus, targetStatus);
            } else {
                Log.warnf("No transition found to move %s from '%s' to '%s'", jiraIssueKey, currentStatus, targetStatus);
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to transition issue %s to status '%s'", jiraIssueKey, targetStatus);
        }
    }

    private Optional<String> findTransitionToStatus(String jiraIssueKey, String targetStatus) {
        var transitionsResponse = jiraClient.getTransitions(jiraIssueKey);
        if (transitionsResponse.transitions() == null) {
            return Optional.empty();
        }

        return transitionsResponse.transitions().stream()
                .filter(t -> t.to() != null && targetStatus.equalsIgnoreCase(t.to().name()))
                .map(JiraTransition::id)
                .findFirst();
    }

    private void performTransition(String jiraIssueKey, String transitionId, String fromStatus, String toStatus) {
        var request = new JiraTransition.TransitionRequest(
                new JiraTransition.TransitionRequest.TransitionId(transitionId)
        );
        jiraClient.doTransition(jiraIssueKey, request);
        Log.infof("Transitioned %s: '%s' → '%s'", jiraIssueKey, fromStatus, toStatus);
    }
}
