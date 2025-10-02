package bogdanpc.linearsync.linear.control;

import bogdanpc.linearsync.linear.entity.LinearIssue;
import bogdanpc.linearsync.linear.entity.LinearResponse;
import bogdanpc.linearsync.linear.entity.LinearUserResponse;
import bogdanpc.linearsync.linear.entity.LinearIssueResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "linear-api")
@RegisterProvider(LinearResponseExceptionMapper.class)
@ApplicationScoped
@ClientHeaderParam(name = "Authorization", value = "${linear.api.token}")
public interface LinearClient {

    @POST
    @Path("/")
    LinearResponse<LinearIssue> getIssues(GraphQLQuery query);

    @POST
    @Path("/")
    LinearUserResponse getCurrentUser(GraphQLQuery query);

    @POST
    @Path("/")
    LinearIssueResponse getIssue(GraphQLQuery query);
}