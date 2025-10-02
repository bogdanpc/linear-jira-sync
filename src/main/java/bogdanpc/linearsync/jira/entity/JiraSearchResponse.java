package bogdanpc.linearsync.jira.entity;

import java.util.List;

public record JiraSearchResponse(List<JiraIssue> issues, int total, int startAt, int maxResults) {}