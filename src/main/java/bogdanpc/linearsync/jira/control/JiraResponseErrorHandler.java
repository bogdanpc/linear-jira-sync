package bogdanpc.linearsync.jira.control;

import io.quarkus.logging.Log;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/**
 * Converts HTTP error responses from Jira REST API into meaningful exceptions.
 */
public class JiraResponseErrorHandler implements ResponseExceptionMapper<RuntimeException> {


    @Override
    public RuntimeException toThrowable(Response response) {
        if (response.getStatus() >= 400) {
            var statusCode = response.getStatus();
            var statusInfo = response.getStatusInfo();

            String errorBody = "";
            if (response.hasEntity()) {
                try {
                    errorBody = response.readEntity(String.class);
                } catch (ProcessingException | IllegalStateException e) {
                    Log.warnf("Failed to read error response body: %s", e.getMessage());
                }
            }

            var errorMessage = String.format("Jira API Error - Status: %d %s%nResponse Body: %s", statusCode, statusInfo.getReasonPhrase(), errorBody.isEmpty() ? "<empty>" : errorBody);

            Log.error(errorMessage);

            return new JiraApiException(errorMessage, statusCode, errorBody);
        }

        return null;
    }
}