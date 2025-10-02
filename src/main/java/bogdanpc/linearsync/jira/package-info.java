/**
 * Jira Integration Business Component
 *
 * This package handles all interactions with the Jira REST API for issue management.
 * It provides a clean abstraction layer over the Jira API with proper error handling,
 * authentication, and data transformation.
 *
 * Architecture follows BCE pattern:
 * - Boundary: REST client interfaces and exception handling
 * - Control: Business logic for Jira operations and coordination
 * - Entity: Data models representing Jira resources
 *
 * Key Capabilities:
 * - Issue creation and updates with field mapping
 * - Comment and attachment synchronization
 * - Custom field handling for Linear issue tracking
 * - Comprehensive error handling with specific exception types
 * - Request/response logging for debugging and monitoring
 *
 * Design Decisions:
 * - Uses MicroProfile REST Client for declarative API calls
 * - Authentication handled via Basic Auth with API tokens
 * - Rich domain models with Jackson serialization support
 * - Pagination support for large result sets
 * - Defensive programming with input validation and error recovery
 */
package bogdanpc.linearsync.jira;