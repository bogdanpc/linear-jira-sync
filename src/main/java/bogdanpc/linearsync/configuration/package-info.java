/**
 * Checks, before any API call, that the configuration needed to reach Linear and Jira is complete, so a missing
 * credential is reported by name instead of surfacing as an HTTP error mid-sync.
 * <p>
 * Values are resolved by MicroProfile Config, highest precedence first:
 * <ol>
 *   <li>system properties, including those set from command-line options such as {@code --state-dir}</li>
 *   <li>environment variables such as {@code LINEAR_API_TOKEN}</li>
 *   <li>the Jira URL derived from {@code JIRA_API_CLOUDID}</li>
 *   <li>the external properties file given with {@code --config}, by default
 *       {@code ~/.linear-jira-sync/config.properties}</li>
 *   <li>the bundled {@code application.properties}, which holds the defaults</li>
 * </ol>
 * Keeping credentials in environment variables or the external file means they never end up in the application
 * archive.
 */
package bogdanpc.linearsync.configuration;
