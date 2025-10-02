/**
 * Linear-Jira Synchronization Tool
 *
 * This application provides one-way synchronization from Linear issues to Jira projects.
 * It performs incremental updates and maintains synchronization state to avoid duplicates.
 *
 * The application follows the Boundary-Control-Entity (BCE) architectural pattern:
 * - Boundary: Handles external interfaces (CLI, API clients)
 * - Control: Contains business logic and coordination
 * - Entity: Represents domain objects and data structures
 *
 * Business Components:
 * - cli: Command-line interface for user interaction
 * - linear: Integration with Linear GraphQL API
 * - jira: Integration with Jira REST API
 * - synchronization: Core sync orchestration and state management
 *
 * Key Features:
 * - Incremental synchronization based on timestamps
 * - Persistent state management to track synced issues
 * - Comprehensive field mapping between Linear and Jira
 * - Dry-run mode for testing synchronization logic
 * - Error handling and logging for production use
 */
package bogdanpc.linearsync;