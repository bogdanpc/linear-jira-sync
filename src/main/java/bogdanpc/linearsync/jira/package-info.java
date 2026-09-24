/**
 * Writes issues, comments, attachments and status transitions to a Jira Cloud project through the REST API v3.
 * <p>
 * Design decisions:
 * <ul>
 *   <li>Input arrives as source-neutral {@code JiraIssueInput}, so this component does not depend on Linear.</li>
 *   <li>Rich text is sent as Atlassian Document Format, converted from markdown.</li>
 *   <li>Every synced issue carries its source identifier as a {@code [IDENTIFIER]} summary prefix, which makes issues
 *       findable even without the optional custom field that stores the Linear id.</li>
 *   <li>Comments are posted by the API user, so the original author and time are rendered into the comment text;
 *       already posted comments are recognised by their text.</li>
 *   <li>Status is moved with workflow transitions to one of three target statuses; a workflow without a matching
 *       transition leaves the issue unchanged.</li>
 *   <li>The Jira base URL can be derived from an Atlassian Cloud ID, which avoids depending on the site URL.</li>
 * </ul>
 *
 * @see <a href="https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/">Jira Cloud REST API v3</a>
 */
package bogdanpc.linearsync.jira;
