package bogdanpc.linearsync.linear.control;

import io.quarkus.logging.Log;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

public class LinearResponseExceptionMapper implements ResponseExceptionMapper<RuntimeException> {

    @Override
    public RuntimeException toThrowable(Response response) {
        if (response.getStatus() >= 400) {
            String errorBody = "";
            var statusCode = response.getStatus();
            var statusInfo = response.getStatusInfo();
            if (response.hasEntity()) {
                try {
                    errorBody = response.readEntity(String.class);
                } catch (ProcessingException | IllegalStateException e) {
                    Log.warnf("Failed to read error response body: %s", e.getMessage());
                }
            }

            var errorMessage = String.format("Linear     API Error - Status: %d %s%nResponse Body: %s", statusCode, statusInfo.getReasonPhrase(), errorBody.isEmpty() ? "<empty>" : errorBody);

            Log.error(errorMessage);
            return new RuntimeException("Failed to fetch Linear issues: HTTP " + response.getStatus());
        }
        return null;
    }
}
