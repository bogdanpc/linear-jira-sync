package bogdanpc.linearsync.jira.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JiraCreateRequest(Fields fields) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Fields(Project project, String summary, AdfNode description, IssueType issuetype, Priority priority,
            List<String> labels, Parent parent, @JsonIgnore Map<String, Object> customFields) {

        @JsonAnyGetter
        Map<String, Object> customFieldValues() {
            return customFields;
        }
    }

    public record Project(String key) {}

    public record IssueType(String name) {}

    public record Priority(String name) {}

    public record Parent(String key) {}
}
