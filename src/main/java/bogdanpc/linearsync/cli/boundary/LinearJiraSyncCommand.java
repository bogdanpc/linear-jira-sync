package bogdanpc.linearsync.cli.boundary;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.GroupCommand;
import org.aesh.command.invocation.CommandInvocation;

import java.util.List;

@Dependent
@CommandDefinition(name = "linear-jira-sync", description = "Synchronize Linear issues to Jira", version = "1.0.0", generateHelp = true)
public class LinearJiraSyncCommand implements GroupCommand<CommandInvocation> {

    @Inject
    SyncCommand sync;

    @Inject
    TestConnectionCommand testConnection;

    @Inject
    ListIssueTypesCommand listIssueTypes;

    @Inject
    ListLinearIssuesCommand listIssues;

    @Inject
    ReadLinearIssueCommand readIssue;

    @Override
    public List<Command<CommandInvocation>> getCommands() {
        return List.of(sync, testConnection, listIssueTypes, listIssues, readIssue);
    }

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        invocation.println(invocation.getHelpInfo());
        return CommandResult.FAILURE;
    }
}
