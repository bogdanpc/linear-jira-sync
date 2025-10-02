package bogdanpc.linearsync.linear.control;

import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.linear.entity.LinearUser;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@ApplicationScoped
public class IssueOperations {

    private static final String ISSUES_QUERY = """
            query GetIssues($first: Int, $after: String, $filter: IssueFilter) {
              issues(first: $first, after: $after, filter: $filter) {
                nodes {
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
                  createdAt
                  updatedAt
                  url
                }
                pageInfo {
                  hasNextPage
                  endCursor
                }
              }
            }
            """;
    private final LinearClient linearClient;

    public IssueOperations(@RestClient LinearClient linearClient) {
        this.linearClient = linearClient;
    }

    public List<LinearIssue> getIssues(String teamKey, String stateType, Instant updatedAfter) {
        return fetchIssues(teamKey, stateType, updatedAfter, null);
    }

    public List<LinearIssue> getMyIssues(String teamKey, String stateType, Instant updatedAfter) {
        String userEmail = getCurrentUserEmail();
        return fetchIssues(teamKey, stateType, updatedAfter, userEmail);
    }

    private List<LinearIssue> fetchIssues(String teamKey, String stateType, Instant updatedAfter, String assigneeEmail) {
        Log.infof("Fetching Linear issues for team: %s, state: %s, assignee: %s", teamKey, stateType, assigneeEmail);

        var allIssues = new ArrayList<LinearIssue>();
        String cursor = null;
        var hasNextPage = true;

        while (hasNextPage) {
            var variables = new HashMap<String, Object>();
            variables.put("first", 50);
            if (cursor != null) {
                variables.put("after", cursor);
            }
            variables.put("filter", buildFilter(teamKey, stateType, updatedAfter, assigneeEmail));

            var query = new GraphQLQuery(ISSUES_QUERY, variables);

            var response = linearClient.getIssues(query);

            if (response.data() == null || response.data().issues() == null) {
                break;
            }
            allIssues.addAll(response.data().issues().nodes());
            hasNextPage = response.data().issues().pageInfo().hasNextPage();
            cursor = response.data().issues().pageInfo().endCursor();
        }

        Log.infof("Fetched %d Linear issues", allIssues.size());
        return allIssues;
    }

    public boolean testConnection() {
        var currentUser = getCurrentUser();
        return currentUser != null;
    }

    public String getCurrentUserEmail() {
        var user = getCurrentUser();
        return user != null ? user.email() : null;
    }

    private LinearUser getCurrentUser() {
        var testQuery = """
                query {
                  viewer {
                    id
                    name
                    email
                  }
                }
                """;

        var query = new GraphQLQuery(testQuery, null);
        var response = linearClient.getCurrentUser(query);

        if (response.data() != null && response.data().viewer() != null) {
            var user = response.data().viewer();
            Log.infof("Successfully connected to Linear. User: %s (%s)", user.name(), user.email());
            return user;
        } else {
            Log.error("Failed to get user info from Linear API");
            return null;
        }
    }

    public Optional<LinearIssue> getIssueByIdentifier(String identifier) {
        Log.infof("Fetching Linear issue by identifier: %s", identifier);

        var issueQuery = """
                query GetIssue($id: String!) {
                  issue(id: $id) {
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
                    createdAt
                    updatedAt
                    url
                  }
                }
                """;

        var variables = Map.of("id", identifier);
        var query = new GraphQLQuery(issueQuery, variables);

        var response = linearClient.getIssue(query);

        if (response.data() != null && response.data().issue() != null) {
            return Optional.of(response.data().issue());
        }
        return Optional.empty();

    }

    private Map<String, Object> buildFilter(String teamKey, String stateType, Instant updatedAfter, String assigneeEmail) {
        var filter = new HashMap<String, Object>();

        if (teamKey != null && !teamKey.isEmpty()) {
            filter.put("team", Map.of("key", Map.of("eq", teamKey)));
        }

        if (stateType != null && !stateType.isEmpty()) {
            filter.put("state", Map.of("type", Map.of("eq", stateType)));
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