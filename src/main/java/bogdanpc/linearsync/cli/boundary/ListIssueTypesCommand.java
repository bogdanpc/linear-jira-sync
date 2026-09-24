package bogdanpc.linearsync.cli.boundary;

import bogdanpc.linearsync.configuration.control.SyncConfiguration;
import bogdanpc.linearsync.jira.boundary.Jira;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Mixin;

@Dependent
@CommandDefinition(name = "list-issue-types", description = "List the issue types available in the target Jira project", generateHelp = true)
public class ListIssueTypesCommand implements Command<CommandInvocation> {

    @Inject
    SyncConfiguration config;

    @Inject
    Jira jiraService;

    @Mixin
    OutputOptions output;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        if (output.applyLogLevel() || !ConfigurationCheck.isValid(config)) {
            return CommandResult.FAILURE;
        }

        Log.info("Fetching available issue types from Jira project...");

        try {
            var issueTypes = jiraService.getProjectIssueTypes();

            if (issueTypes.isEmpty()) {
                Log.info("No issue types found for the configured project.");
                return CommandResult.SUCCESS;
            }

            Log.info("Available issue types:");
            for (var issueType : issueTypes) {
                Log.infof("  - %s%s", issueType.name(), issueType.subtask() ? " [subtask]" : "");
                if (issueType.description() != null && !issueType.description().isBlank()) {
                    Log.infof("      %s", issueType.description());
                }
            }

            Log.info("To configure, set environment variables:");
            Log.info("  JIRA_ISSUE_TYPE=<name>      (for regular issues)");
            Log.info("  JIRA_SUBTASK_TYPE=<name>    (for subtasks, use a [subtask] type)");

            return CommandResult.SUCCESS;
        } catch (RuntimeException e) {
            Log.error("Failed to fetch issue types: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }
}
