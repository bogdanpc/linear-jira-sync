package bogdanpc.linearsync.cli.boundary;

import bogdanpc.linearsync.linear.control.IssueOperations;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.linear.entity.LinearStateType;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list", description = "List Linear issues without syncing to Jira", mixinStandardHelpOptions = true)
public class ListLinearIssuesCommand implements Callable<Integer> {

    @Inject
    IssueOperations linearService;

    @Option(names = {"-t", "--team"}, description = "Linear team key to filter by (e.g., 'ENG')")
    String teamKey;

    @Option(names = {"-s", "--state"}, description = "Filter by Linear issue state type (e.g., 'started', 'completed')")
    LinearStateType stateType;

    @Option(names = {"-u", "--updated-after"}, description = "Only list issues updated after this ISO datetime (e.g., '2024-01-01T00:00:00Z')")
    String updatedAfter;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output with additional issue details")
    boolean verbose = false;

    @Option(names = {"-q", "--quiet"}, description = "Suppress non-error output")
    boolean quiet = false;

    @Option(names = {"-a", "--all"}, description = "Show all issues (default is to show only issues assigned to you)")
    boolean showAll = false;

    @Override
    public Integer call() {
        if (quiet && verbose) {
            Log.error("Error: Cannot use both --quiet and --verbose options");
            return 1;
        }

        LoggingConfigurer.configure(quiet, verbose);

        try {
            var updatedAfterInstant = getUpdatedAfterInstant();
            if (updatedAfterInstant == null) return 1;

            Log.info("Fetching Linear issues...");
            Log.debug("Configuration:");
            Log.debug("  Team: " + (teamKey != null ? teamKey : "all"));
            Log.debug("  State: " + (stateType != null ? stateType.getValue() : "all"));
            Log.debug("  Updated After: " + updatedAfterInstant);
            Log.debug("  Scope: " + (showAll ? "all issues" : "my issues only"));

            var issues = getLinearIssues(updatedAfterInstant);

            if (issues.isEmpty()) {
                Log.info("No issues found matching the specified criteria.");
                return 0;
            }
            Log.info("Found %d Linear issues:%n".formatted(issues.size()));
            Log.info("");

            for (var issue : issues) {
                Log.info("%-12s %s".formatted(issue.identifier(), issue.title()));
                Log.debug("             Team: %s | State: %s (%s) | Priority: %d".formatted(issue.team().key(), issue.state().name(), issue.state().type(), issue.priority()));
                if (issue.assignee() != null) {
                    Log.debug("             Assignee: %s".formatted(issue.assignee().displayName()));
                }
                if (issue.description() != null && !issue.description().isEmpty()) {
                    var description = issue.description().length() > 100 ? issue.description().substring(0, 100) + "..." : issue.description();
                    Log.debug("             Description: %s".formatted(description));
                }
                Log.debug("             URL: %s".formatted(issue.url()));
                Log.debug("");
            }

            return 0;

        } catch (Exception e) {
            Log.error("Error: Failed to fetch Linear issues - " + e.getMessage());
            Log.debug("Stack trace: " + Arrays.toString(e.getStackTrace()));
            return 1;
        }
    }

    private List<LinearIssue> getLinearIssues(Instant updatedAfterInstant) {
        return showAll ? linearService.getIssues(teamKey, stateType.getValue(), updatedAfterInstant) :
                linearService.getMyIssues(teamKey, stateType.getValue(), updatedAfterInstant);
    }

    private Instant getUpdatedAfterInstant() {
        if (updatedAfter == null || !updatedAfter.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(updatedAfter);
        } catch (DateTimeParseException _) {
            Log.error("Error: Invalid datetime format for --updated-after. Use ISO format like '2024-01-01T00:00:00Z'");
            return null;
        }
    }

}