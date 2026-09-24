package bogdanpc.linearsync.cli.boundary;

import bogdanpc.linearsync.configuration.control.SyncConfiguration;
import bogdanpc.linearsync.jira.boundary.Jira;
import bogdanpc.linearsync.linear.control.IssueOperations;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Mixin;

import java.util.concurrent.Callable;

@Dependent
@CommandDefinition(name = "test-connection", description = "Verify that the Linear and Jira credentials work", generateHelp = true)
public class TestConnectionCommand implements Command<CommandInvocation> {

    private static final String FAILED = "✗ FAILED";

    @Inject
    SyncConfiguration config;

    @Inject
    Jira jiraService;

    @Inject
    IssueOperations linearService;

    @Mixin
    OutputOptions output;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        if (output.conflicting() || !ConfigurationCheck.isValid(config)) {
            return CommandResult.FAILURE;
        }

        Log.info("Testing API connections...");

        var linearConnected = probe("Linear", linearService::testConnection);
        var jiraConnected = probe("Jira", jiraService::testConnection);

        Log.info("");
        if (linearConnected && jiraConnected) {
            Log.info("✓ All API connections are working correctly");
            return CommandResult.SUCCESS;
        }

        Log.info("✗ One or more API connections failed");
        Log.info("");
        Log.info("Please check:");
        if (!linearConnected) {
            Log.info("- LINEAR_API_TOKEN environment variable is set and valid");
            Log.info("- Linear API URL is accessible: https://api.linear.app/graphql");
        }
        if (!jiraConnected) {
            Log.info("- JIRA_API_TOKEN and JIRA_USERNAME environment variables are set and valid");
            Log.info("- JIRA_API_URL environment variable is set to your Jira instance URL");
        }
        return CommandResult.FAILURE;
    }

    private static boolean probe(String name, Callable<Boolean> probe) {
        Log.infof("Testing %s API connection... ", name);
        try {
            var connected = Boolean.TRUE.equals(probe.call());
            Log.info(connected ? "✓ SUCCESS" : FAILED);
            return connected;
        } catch (Exception e) {
            Log.error(FAILED);
            Log.error("  Error: " + e.getMessage());
            return false;
        }
    }
}
