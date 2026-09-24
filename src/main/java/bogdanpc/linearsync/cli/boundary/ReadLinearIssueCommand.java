package bogdanpc.linearsync.cli.boundary;

import bogdanpc.linearsync.linear.control.IssueOperations;
import bogdanpc.linearsync.linear.entity.LinearIssue;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

import java.util.Arrays;

@Dependent
@CommandDefinition(name = "read", description = "Read a specific Linear issue with comments and attachments", generateHelp = true)
public class ReadLinearIssueCommand implements Command<CommandInvocation> {

    @Inject
    IssueOperations linearService;

    @Argument(description = "Linear issue identifier (e.g., 'ENG-123')", required = true)
    String issueIdentifier;

    @Mixin
    OutputOptions output;

    @Option(name = "comments-only", hasValue = false, description = "Show only comments (no attachments)")
    boolean commentsOnly;

    @Option(name = "attachments-only", hasValue = false, description = "Show only attachments (no comments)")
    boolean attachmentsOnly;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        if (output.applyLogLevel()) {
            return CommandResult.FAILURE;
        }

        if (commentsOnly && attachmentsOnly) {
            Log.error("Error: Cannot use both --comments-only and --attachments-only options");
            return CommandResult.FAILURE;
        }

        Log.info("Fetching Linear issue: " + issueIdentifier);

        try {
            return linearService.getIssue(issueIdentifier).map(this::displayIssue).orElseGet(() -> {
                Log.error("Error: Issue not found: " + issueIdentifier);
                return CommandResult.FAILURE;
            });
        } catch (RuntimeException e) {
            Log.error("Error: Failed to fetch Linear issue - " + e.getMessage());
            Log.debug("Stack trace: " + Arrays.toString(e.getStackTrace()));
            return CommandResult.FAILURE;
        }
    }

    private CommandResult displayIssue(LinearIssue issue) {
        displayIssueBasicInfo(issue);
        displayComments(issue);
        displayAttachments(issue);
        return CommandResult.SUCCESS;
    }

    private static void displayIssueBasicInfo(LinearIssue issue) {
        Log.info("");
        Log.info("=== LINEAR ISSUE ===");
        Log.info("ID: %s".formatted(issue.identifier()));
        Log.info("Title: %s".formatted(issue.title()));
        Log.info("Team: %s (%s)".formatted(issue.team().name(), issue.team().key()));
        Log.info("State: %s (%s)".formatted(issue.state().name(), issue.state().type()));
        Log.info("Priority: %d".formatted(issue.priority()));
        if (issue.assignee() != null) {
            Log.info("Assignee: %s".formatted(issue.assignee().displayName()));
        }
        Log.info("Created: %s".formatted(issue.createdAt()));
        Log.info("Updated: %s".formatted(issue.updatedAt()));
        Log.info("URL: %s".formatted(issue.url()));

        if (issue.description() != null && !issue.description().isEmpty()) {
            Log.debug("");
            Log.debug("--- Description ---");
            Log.debug(issue.description());
        }
    }

    private void displayAttachments(LinearIssue issue) {
        if (!commentsOnly && issue.attachments() != null && !issue.attachments().nodes().isEmpty()) {
            Log.info("");
            Log.info("=== ATTACHMENTS (%d) ===".formatted(issue.attachments().nodes().size()));

            for (int i = 0; i < issue.attachments().nodes().size(); i++) {
                var attachment = issue.attachments().nodes().get(i);
                Log.info("");
                Log.info("--- Attachment %d ---".formatted(i + 1));
                Log.info("Title: %s".formatted(attachment.title()));
                Log.info("Type: %s".formatted(attachment.sourceType()));
                Log.info("URL: %s".formatted(attachment.url()));
                Log.info("Created by: %s".formatted(attachment.creator().displayName()));
                Log.info("Created: %s".formatted(attachment.createdAt()));

                if (attachment.metadata() != null && !attachment.metadata().isEmpty()) {
                    Log.debug("Metadata:");
                    attachment.metadata().forEach((key, value) -> Log.debug("  %s: %s".formatted(key, value)));
                }
            }

            if (issue.attachments().pageInfo().hasNextPage()) {
                Log.info("");
                Log.info("Note: This issue has more attachments that were not fetched.");
            }
        } else if (!commentsOnly) {
            Log.info("");
            Log.info("=== ATTACHMENTS ===");
            Log.info("No attachments found.");
        }
    }

    private void displayComments(LinearIssue issue) {
        if (!attachmentsOnly && issue.comments() != null && !issue.comments().nodes().isEmpty()) {
            Log.info("");
            Log.info("=== COMMENTS (%d) ===".formatted(issue.comments().nodes().size()));

            for (int i = 0; i < issue.comments().nodes().size(); i++) {
                var comment = issue.comments().nodes().get(i);
                Log.info("");
                Log.info("--- Comment %d ---".formatted(i + 1));
                Log.info("Author: %s".formatted(comment.user().displayName()));
                Log.info("Created: %s".formatted(comment.createdAt()));
                if (!comment.createdAt().equals(comment.updatedAt())) {
                    Log.info("Updated: %s".formatted(comment.updatedAt()));
                }
                Log.debug("URL: %s".formatted(comment.url()));
                Log.info("Content:");
                Log.info(comment.body());
            }

            if (issue.comments().pageInfo().hasNextPage()) {
                Log.info("");
                Log.info("Note: This issue has more comments that were not fetched.");
            }
        } else if (!attachmentsOnly) {
            Log.info("");
            Log.info("=== COMMENTS ===");
            Log.info("No comments found.");
        }
    }

}
