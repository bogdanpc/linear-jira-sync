package bogdanpc.linearsync.jira.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JiraComment(String id, AdfNode body, String created) {

    public static JiraComment withBody(AdfNode body) {
        return new JiraComment(null, body, null);
    }

    public String plainText() {
        return body == null ? "" : body.plainText();
    }
}
