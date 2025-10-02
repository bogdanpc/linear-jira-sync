package bogdanpc.linearsync.jira.entity;

import java.util.List;

public record JiraCommentsResponse(
        List<JiraComment> comments,
        int startAt,
        int maxResults,
        int total
) {}