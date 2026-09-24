package bogdanpc.linearsync.cli.boundary;

import bogdanpc.linearsync.linear.control.IssueOperations;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.linear.entity.LinearStateType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Dependent
@CommandDefinition(name = "list", description = "List Linear issues without syncing to Jira", generateHelp = true)
public class ListLinearIssuesCommand implements Command<CommandInvocation> {

    private static final int DESCRIPTION_PREVIEW_LENGTH = 100;

    @Inject
    IssueOperations linearService;

    @Mixin
    OutputOptions output;

    @Option(name = "team", shortName = 't', description = "Linear team key to filter by (e.g., 'ENG')")
    String teamKey;

    @Option(name = "state", shortName = 's', converter = StateTypeConverter.class, description = "Filter by Linear issue state type (e.g., 'started', 'completed')")
    LinearStateType stateType;

    @Option(name = "updated-after", shortName = 'u', description = "Only list issues updated after this ISO datetime (e.g., '2024-01-01T00:00:00Z')")
    String updatedAfter;

    @Option(name = "all", shortName = 'a', hasValue = false, description = "Show all issues (default is to show only issues assigned to you)")
    boolean showAll;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        if (output.applyLogLevel()) {
            return CommandResult.FAILURE;
        }

        Instant updatedAfterInstant;
        try {
            updatedAfterInstant = parseUpdatedAfter();
        } catch (DateTimeParseException _) {
            Log.error("Error: Invalid datetime format for --updated-after. Use ISO format like '2024-01-01T00:00:00Z'");
            return CommandResult.FAILURE;
        }

        try {
            Log.info("Fetching Linear issues...");
            Log.debug("Configuration:");
            Log.debug("  Team: " + (teamKey != null ? teamKey : "all"));
            Log.debug("  State: " + (stateType != null ? stateType : "all"));
            Log.debug("  Updated After: " + (updatedAfterInstant != null ? updatedAfterInstant : "any"));
            Log.debug("  Scope: " + (showAll ? "all issues" : "my issues only"));

            var issues = getLinearIssues(updatedAfterInstant);

            if (issues.isEmpty()) {
                Log.info("No issues found matching the specified criteria.");
                return CommandResult.SUCCESS;
            }

            Log.infof("Found %d Linear issues:", issues.size());
            Log.info("");
            issues.forEach(ListLinearIssuesCommand::printIssue);

            return CommandResult.SUCCESS;

        } catch (RuntimeException e) {
            Log.error("Error: Failed to fetch Linear issues - " + e.getMessage());
            Log.debug("Stack trace: " + Arrays.toString(e.getStackTrace()));
            return CommandResult.FAILURE;
        }
    }

    private static void printIssue(LinearIssue issue) {
        Log.info("%-12s %s".formatted(issue.identifier(), issue.title()));
        Log.debug("             Team: %s | State: %s (%s) | Priority: %d".formatted(issue.team().key(),
                issue.state().name(), issue.state().type(), issue.priority()));
        if (issue.assignee() != null) {
            Log.debug("             Assignee: %s".formatted(issue.assignee().displayName()));
        }
        if (issue.description() != null && !issue.description().isEmpty()) {
            Log.debug("             Description: %s".formatted(preview(issue.description())));
        }
        Log.debug("             URL: %s".formatted(issue.url()));
        Log.debug("");
    }

    private static String preview(String description) {
        return description.length() > DESCRIPTION_PREVIEW_LENGTH
                ? description.substring(0, DESCRIPTION_PREVIEW_LENGTH) + "..."
                : description;
    }

    private List<LinearIssue> getLinearIssues(Instant updatedAfterInstant) {
        return showAll ? linearService.getIssues(teamKey, stateType, updatedAfterInstant)
                : linearService.getMyIssues(teamKey, stateType, updatedAfterInstant);
    }

    private Instant parseUpdatedAfter() {
        return updatedAfter == null || updatedAfter.isBlank() ? null : Instant.parse(updatedAfter);
    }

}
