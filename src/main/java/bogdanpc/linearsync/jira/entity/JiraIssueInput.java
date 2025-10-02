package bogdanpc.linearsync.jira.entity;

import java.time.Instant;
import java.util.List;

/**
 * Represents issue data for Jira operations within the Jira business component.
 * This entity encapsulates all necessary information to create or update Jira issues
 * without depending on entities from other business components.
 */
public record JiraIssueInput(
    String sourceId,
    String sourceIdentifier,
    String title,
    String description,
    Integer priority,
    String state,
    String assigneeEmail,
    String assigneeDisplayName,
    String creatorEmail,
    String creatorDisplayName,
    String teamName,
    String teamKey,
    List<LabelInput> labels,
    List<CommentInput> comments,
    List<AttachmentInput> attachments,
    Instant createdAt,
    Instant updatedAt,
    String sourceUrl
) {

    public record LabelInput(
        String name,
        String color
    ) {}

    public record CommentInput(
        String id,
        String body,
        String authorName,
        String authorDisplayName,
        String authorEmail,
        Instant createdAt,
        Instant updatedAt,
        String url
    ) {}

    public record AttachmentInput(
        String id,
        String title,
        String url,
        String sourceType,
        String creatorName,
        String creatorDisplayName,
        String creatorEmail,
        Instant createdAt
    ) {}
}