package bogdanpc.linearsync.jira.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraAttachment(
        @JsonProperty("id") String id,
        @JsonProperty("filename") String filename,
        @JsonProperty("size") Long size,
        @JsonProperty("mimeType") String mimeType,
        @JsonProperty("content") String content,
        @JsonProperty("thumbnail") String thumbnail,
        @JsonProperty("author") JiraUser author,
        @JsonProperty("created") String created,
        @JsonProperty("self") String self
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraUser(
            @JsonProperty("accountId") String accountId,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("emailAddress") String emailAddress,
            @JsonProperty("active") Boolean active,
            @JsonProperty("self") String self
    ) {}
}