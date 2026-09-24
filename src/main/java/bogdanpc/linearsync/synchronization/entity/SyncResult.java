package bogdanpc.linearsync.synchronization.entity;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

public record SyncResult(List<IssueResult> issueResults, List<String> errors, Duration duration) {

    public static SyncResult aborted(String error, Duration duration) {
        return new SyncResult(List.of(), List.of(error), duration);
    }

    public long count(SyncAction action) {
        return issueResults.stream().filter(result -> result.success() && result.action() == action).count();
    }

    public List<String> allErrors() {
        var issueErrors = issueResults.stream().filter(result -> !result.success()).map(IssueResult::message);
        return Stream.concat(errors.stream(), issueErrors).toList();
    }

    public boolean success() {
        return allErrors().isEmpty();
    }

    public boolean hasChanges() {
        return count(SyncAction.CREATE) > 0 || count(SyncAction.UPDATE) > 0 || count(SyncAction.RECOVER) > 0;
    }
}
