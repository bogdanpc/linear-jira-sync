package bogdanpc.linearsync.linear.control;

import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.linear.entity.LinearStateType;
import bogdanpc.linearsync.linear.entity.LinearUser;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class IssueOperations {

    private static final int PAGE_SIZE = 50;

    /**
     * Shared selection set, so a single issue and a page of issues always carry the same fields.
     */
    private static final String ISSUE_FIELDS = """
            id
            identifier
            title
            description
            priority
            state {
              id
              name
              type
            }
            assignee {
              id
              name
              email
              displayName
            }
            creator {
              id
              name
              email
              displayName
            }
            team {
              id
              name
              key
            }
            labels {
              nodes {
                id
                name
                color
              }
            }
            comments(first: 100) {
              nodes {
                id
                body
                user {
                  id
                  name
                  email
                  displayName
                }
                createdAt
                updatedAt
                url
              }
              pageInfo {
                hasNextPage
                endCursor
              }
            }
            attachments(first: 50) {
              nodes {
                id
                title
                url
                sourceType
                creator {
                  id
                  name
                  email
                  displayName
                }
                metadata
                createdAt
              }
              pageInfo {
                hasNextPage
                endCursor
              }
            }
            parent {
              id
              identifier
              title
            }
            children {
              nodes {
                id
                identifier
                title
              }
            }
            createdAt
            updatedAt
            url
            """;

    private static final String ISSUES_QUERY = """
            query GetIssues($first: Int, $after: String, $filter: IssueFilter) {
              issues(first: $first, after: $after, filter: $filter) {
                nodes {
            %s
                }
                pageInfo {
                  hasNextPage
                  endCursor
                }
              }
            }
            """.formatted(ISSUE_FIELDS.indent(6));

    private static final String ISSUE_QUERY = """
            query GetIssue($id: String!) {
              issue(id: $id) {
            %s
              }
            }
            """.formatted(ISSUE_FIELDS.indent(4));

    private static final String VIEWER_QUERY = """
            query {
              viewer {
                id
                name
                email
              }
            }
            """;

    private final LinearClient linearClient;

    public IssueOperations(@RestClient LinearClient linearClient) {
        this.linearClient = linearClient;
    }

    public List<LinearIssue> getIssues(String teamKey, LinearStateType stateType, Instant updatedAfter) {
        return fetchIssues(teamKey, stateType, updatedAfter, null);
    }

    public List<LinearIssue> getMyIssues(String teamKey, LinearStateType stateType, Instant updatedAfter) {
        return fetchIssues(teamKey, stateType, updatedAfter, getCurrentUserEmail());
    }

    /**
     * The Linear API resolves {@code issue(id:)} against both the UUID and the human-readable
     * identifier, so one lookup serves callers holding either.
     */
    public Optional<LinearIssue> getIssue(String idOrIdentifier) {
        Log.debugf("Fetching issue: %s", idOrIdentifier);

        var response = linearClient.getIssue(new GraphQLQuery(ISSUE_QUERY, Map.of("id", idOrIdentifier)));

        return response.data() == null ? Optional.empty() : Optional.ofNullable(response.data().issue());
    }

    public boolean testConnection() {
        return getCurrentUser() != null;
    }

    public String getCurrentUserEmail() {
        var user = getCurrentUser();
        return user != null ? user.email() : null;
    }

    private List<LinearIssue> fetchIssues(String teamKey, LinearStateType stateType, Instant updatedAfter, String assigneeEmail) {
        Log.debugf("Fetching issues - team: %s, state: %s, assignee: %s", teamKey, stateType, assigneeEmail);

        var allIssues = new ArrayList<LinearIssue>();
        String cursor = null;
        var hasNextPage = true;

        while (hasNextPage) {
            var variables = new HashMap<String, Object>();
            variables.put("first", PAGE_SIZE);
            if (cursor != null) {
                variables.put("after", cursor);
            }
            variables.put("filter", buildFilter(teamKey, stateType, updatedAfter, assigneeEmail));

            var response = linearClient.getIssues(new GraphQLQuery(ISSUES_QUERY, variables));

            if (response.data() == null || response.data().issues() == null) {
                break;
            }
            allIssues.addAll(response.data().issues().nodes());
            hasNextPage = response.data().issues().pageInfo().hasNextPage();
            cursor = response.data().issues().pageInfo().endCursor();
        }

        Log.debugf("Fetched %d issues from Linear", allIssues.size());
        return allIssues;
    }

    private LinearUser getCurrentUser() {
        var response = linearClient.getCurrentUser(new GraphQLQuery(VIEWER_QUERY, null));

        if (response.data() == null || response.data().viewer() == null) {
            Log.error("Linear API connection failed");
            return null;
        }

        var user = response.data().viewer();
        Log.debugf("Linear user: %s (%s)", user.name(), user.email());
        return user;
    }

    private Map<String, Object> buildFilter(String teamKey, LinearStateType stateType, Instant updatedAfter, String assigneeEmail) {
        var filter = new HashMap<String, Object>();

        if (teamKey != null && !teamKey.isEmpty()) {
            filter.put("team", Map.of("key", Map.of("eq", teamKey)));
        }

        if (stateType != null) {
            filter.put("state", Map.of("type", Map.of("eq", stateType.value())));
        }

        if (updatedAfter != null) {
            filter.put("updatedAt", Map.of("gte", DateTimeFormatter.ISO_INSTANT.format(updatedAfter)));
        }

        if (assigneeEmail != null && !assigneeEmail.isEmpty()) {
            filter.put("assignee", Map.of("email", Map.of("eq", assigneeEmail)));
        }

        return filter;
    }
}
