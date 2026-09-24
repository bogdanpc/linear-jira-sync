/**
 * Read-only access to Linear issues through its GraphQL API.
 * <p>
 * All issue queries share one selection set, so an issue fetched on its own and one fetched as part of a page carry
 * the same fields. Filtering (team, state type, assignee, updated since) is done by Linear, keeping incremental runs
 * cheap. Files uploaded to Linear are downloaded with the API token, because upload URLs are not public.
 *
 * @see <a href="https://developers.linear.app/docs/graphql/working-with-the-graphql-api">Linear GraphQL API</a>
 */
package bogdanpc.linearsync.linear;
