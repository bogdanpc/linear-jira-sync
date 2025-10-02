package bogdanpc.linearsync.jira.control;

import bogdanpc.linearsync.jira.entity.JiraAttachment;
import bogdanpc.linearsync.jira.entity.JiraAttachmentsResponse;
import bogdanpc.linearsync.jira.entity.JiraComment;
import bogdanpc.linearsync.jira.entity.JiraCommentsResponse;
import bogdanpc.linearsync.jira.entity.JiraCreateRequest;
import bogdanpc.linearsync.jira.entity.JiraIssue;
import bogdanpc.linearsync.jira.entity.JiraSearchResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.io.File;
import java.util.List;

@RegisterRestClient(configKey = "jira-api")
@ApplicationScoped
@ClientHeaderParam(name = "Authorization", value = "{bogdanpc.linearsync.jira.control.JiraAuthHeaderProvider.getAuthHeader}")
@RegisterProvider(JiraResponseErrorHandler.class)
public interface JiraClient {

    @POST
    @Path("/rest/api/3/issue")
    JiraIssue createIssue(JiraCreateRequest request);

    @PUT
    @Path("/rest/api/3/issue/{issueIdOrKey}")
    void updateIssue(@PathParam("issueIdOrKey") String issueIdOrKey, JiraCreateRequest request);

    @GET
    @Path("/rest/api/3/search")
    JiraSearchResponse searchIssues(@QueryParam("jql") String jql, @QueryParam("startAt") Integer startAt, @QueryParam("maxResults") Integer maxResults);

    @GET
    @Path("/rest/api/3/myself")
    JiraUserInfo getCurrentUser();

    @GET
    @Path("/rest/api/3/issue/{issueIdOrKey}/comment")
    JiraCommentsResponse getComments(@PathParam("issueIdOrKey") String issueIdOrKey, @QueryParam("startAt") Integer startAt, @QueryParam("maxResults") Integer maxResults);

    @POST
    @Path("/rest/api/3/issue/{issueIdOrKey}/comment")
    JiraComment addComment(@PathParam("issueIdOrKey") String issueIdOrKey, JiraComment comment);

    @GET
    @Path("/rest/api/3/issue/{issueIdOrKey}/attachments")
    JiraAttachmentsResponse getAttachments(@PathParam("issueIdOrKey") String issueIdOrKey);

    @POST
    @Path("/rest/api/3/issue/{issueIdOrKey}/attachments")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    List<JiraAttachment> addAttachment(@PathParam("issueIdOrKey") String issueIdOrKey, @FormParam("file") File file);

}