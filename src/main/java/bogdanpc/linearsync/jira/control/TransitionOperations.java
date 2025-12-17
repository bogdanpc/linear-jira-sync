package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraIssue;
import bogdanpc.linearsync.jira.entity.JiraTransition;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Map;
import java.util.Optional;

@ApplicationScoped
class TransitionOperations {

    private static final String TO_DO = "To Do";
    private static final String IN_PROGRESS = "In Progress";
    private static final String DONE = "Done";

    /**
     * Maps Linear workflow state types to Jira status names.
     * Linear state types: triage, backlog, unstarted, started, completed, canceled
     */
    private static final Map<String, String> STATUS_MAPPING = Map.of(
            "triage", TO_DO,
            "backlog", TO_DO,
            "unstarted", TO_DO,
            "started", IN_PROGRESS,
            "completed", DONE,
            "canceled", DONE
    );

    private final JiraClient jiraClient;
    private final boolean statusSyncEnabled;

    TransitionOperations(
            @RestClient JiraClient jiraClient,
            @ConfigProperty(name = "jira.enable-status-sync", defaultValue = "true") boolean statusSyncEnabled
    ) {
        this.jiraClient = jiraClient;
        this.statusSyncEnabled = statusSyncEnabled;
    }

    void transitionIfNeeded(String jiraIssueKey, String linearStateType) {
        if (!statusSyncEnabled) {
            Log.debugf("Status sync disabled, skipping transition for %s", jiraIssueKey);
            return;
        }

        if (linearStateType == null || linearStateType.isBlank()) {
            Log.debugf("No Linear state type provided for %s, skipping transition", jiraIssueKey);
            return;
        }

        var targetStatus = mapLinearStatusToJira(linearStateType);
        if (targetStatus == null) {
            Log.warnf("Unknown Linear state type '%s' for %s, skipping transition", linearStateType, jiraIssueKey);
            return;
        }

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

    private String mapLinearStatusToJira(String linearStateType) {
        return STATUS_MAPPING.get(linearStateType.toLowerCase());
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
