package bogdanpc.linearsync.synchronization.control;

import bogdanpc.linearsync.linear.control.IssueOperations;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.synchronization.entity.IssueResult;
import bogdanpc.linearsync.synchronization.entity.SyncAction;
import bogdanpc.linearsync.synchronization.entity.SyncState;
import io.quarkus.logging.Log;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class SyncRun {

    private final IssueSync issueSync;
    private final IssueOperations linear;
    private final SyncState state;
    private final boolean dryRun;
    private final Map<String, LinearIssue> batch;
    private final Set<String> visited = new HashSet<>();
    private final Map<String, IssueResult> results = new LinkedHashMap<>();

    SyncRun(IssueSync issueSync, IssueOperations linear, SyncState state, boolean dryRun, List<LinearIssue> batch) {
        this.issueSync = issueSync;
        this.linear = linear;
        this.state = state;
        this.dryRun = dryRun;
        this.batch = batch.stream()
                .collect(Collectors.toMap(LinearIssue::id, Function.identity(), (first, _) -> first,
                        LinkedHashMap::new));
    }

    List<IssueResult> syncAll() {
        batch.values().forEach(this::sync);
        return List.copyOf(results.values());
    }

    /**
     * @return the Jira key of the issue, or {@code null} if it has none yet or is still being synced higher up the
     *         hierarchy
     */
    private String sync(LinearIssue issue) {
        if (!visited.add(issue.id())) {
            var earlier = results.get(issue.id());
            return earlier == null ? null : earlier.jiraIssueKey();
        }

        var parentKey = parentJiraKey(issue);
        var result = issue.parent() != null && parentKey == null ? parentNotSynced(issue)
                : issueSync.sync(issue, parentKey, state, dryRun);
        results.put(issue.id(), result);

        if (result.success()) {
            syncChildren(issue);
        }
        return result.jiraIssueKey();
    }

    private IssueResult parentNotSynced(LinearIssue issue) {
        var synced = state.syncedIssue(issue.id());
        var action = synced.isPresent() ? SyncAction.UPDATE : SyncAction.CREATE;
        var message = "Skipped %s: its parent %s is not in Jira".formatted(issue.identifier(),
                issue.parent().identifier());
        Log.errorf("  ✗ %s", message);
        return IssueResult.failed(issue.identifier(), synced.map(SyncState.SyncedIssue::jiraIssueKey).orElse(null),
                action, message);
    }

    private String parentJiraKey(LinearIssue issue) {
        if (issue.parent() == null) {
            return null;
        }
        var parentId = issue.parent().id();
        return state.syncedIssue(parentId)
                .map(SyncState.SyncedIssue::jiraIssueKey)
                .orElseGet(() -> syncRelated(parentId, issue.parent().identifier()));
    }

    private void syncChildren(LinearIssue parent) {
        if (parent.children() == null) {
            return;
        }
        parent.children().getNodes().stream()
                .filter(child -> !visited.contains(child.id()))
                .filter(child -> batch.containsKey(child.id()) || state.syncedIssue(child.id()).isEmpty())
                .forEach(this::syncChild);
    }

    /**
     * A failing child must not stop its siblings.
     */
    private void syncChild(LinearIssue.LinearChild child) {
        try {
            syncRelated(child.id(), child.identifier());
        } catch (RuntimeException e) {
            Log.errorf(e, "Failed to sync child %s", child.identifier());
        }
    }

    private String syncRelated(String linearId, String identifier) {
        var issue = batch.containsKey(linearId) ? batch.get(linearId) : linear.getIssue(linearId).orElse(null);
        if (issue == null) {
            Log.warnf("Related issue %s not found in Linear", identifier);
            return null;
        }
        return sync(issue);
    }
}
