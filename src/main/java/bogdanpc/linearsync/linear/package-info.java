/**
 * Linear Integration Business Component
 *
 * This package manages all interactions with the Linear GraphQL API for retrieving
 * issue data, comments, attachments, and metadata. It provides a robust abstraction
 * over Linear's API with proper error handling and data transformation.
 *
 * Architecture follows BCE pattern:
 * - Boundary: GraphQL client interfaces and API communication
 * - Control: Business logic for Linear operations and data coordination
 * - Entity: Rich domain models representing Linear resources
 *
 * Key Capabilities:
 * - GraphQL query execution with dynamic filtering
 * - Issue retrieval with comprehensive metadata (comments, attachments, labels)
 * - Team and user information resolution
 * - Pagination support for large datasets
 * - Connection testing and health checks
 *
 * Design Decisions:
 * - Uses MicroProfile REST Client with GraphQL over HTTP
 * - Authentication via Bearer token in Authorization header
 * - Modern Java records for immutable data models
 * - Custom deserializers for complex nested structures
 * - Comprehensive filtering support (team, state, date ranges)
 * - Defensive error handling with meaningful error messages
 */
package bogdanpc.linearsync.linear;