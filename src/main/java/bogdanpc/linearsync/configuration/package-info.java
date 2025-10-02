/**
 * Configuration management for Linear-Jira synchronization.
 * <p>
 * This component handles loading and validating configuration from multiple sources
 * with the following precedence (highest to lowest):
 * <ol>
 *   <li>Environment variables (e.g., LINEAR_API_TOKEN)</li>
 *   <li>System properties (-D flags)</li>
 *   <li>~/.linear-jira-sync/config.properties (user-specific configuration)</li>
 *   <li>application-local.properties (project-local configuration)</li>
 *   <li>application.properties file (default configuration)</li>
 *   <li>Default values</li>
 * </ol>
 * <p>
 * The configuration supports both file-based configuration for local development
 * and environment variables for production deployments. Environment variables
 * always take precedence, allowing secure credential management in CI/CD pipelines.
 * <p>
 * The ~/.linear-jira-sync/config.properties file is recommended for development
 * as it works across all Linear-Jira sync projects for the current user.
 */
package bogdanpc.linearsync.configuration;
