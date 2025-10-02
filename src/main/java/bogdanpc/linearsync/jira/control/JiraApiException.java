package bogdanpc.linearsync.jira.control;

public class JiraApiException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public JiraApiException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}