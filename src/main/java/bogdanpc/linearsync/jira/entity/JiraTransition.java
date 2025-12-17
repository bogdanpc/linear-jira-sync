package bogdanpc.linearsync.jira.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraTransition(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("to") JiraTransitionStatus to
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraTransitionStatus(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("statusCategory") JiraIssue.JiraStatusCategory statusCategory
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransitionsResponse(
            @JsonProperty("transitions") List<JiraTransition> transitions
    ) {}

    public record TransitionRequest(
            @JsonProperty("transition") TransitionId transition
    ) {
        public record TransitionId(
                @JsonProperty("id") String id
        ) {}
    }
}
