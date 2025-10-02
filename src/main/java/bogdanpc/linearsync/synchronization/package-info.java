/**
 * Synchronization Orchestration Business Component
 *
 * This package contains the core business logic for orchestrating the synchronization
 * process between Linear and Jira systems. It coordinates data retrieval, transformation,
 * state management, and the actual sync operations.
 *
 * Architecture follows BCE pattern:
 * - Control: Sync orchestration, field mapping, and state management logic
 * - Entity: Sync state, results, and mapping configuration data
 *
 * Key Responsibilities:
 * - Coordinating the overall synchronization workflow
 * - Managing incremental sync state and persistence
 * - Field mapping and data transformation between Linear and Jira
 * - Conflict resolution and duplicate prevention
 * - Progress tracking and result reporting
 *
 * Design Decisions:
 * - Stateful synchronization with persistent tracking via JSON files
 * - Incremental updates based on modification timestamps
 * - Comprehensive field mapping with priority and label support
 * - Dry-run capability for testing without actual modifications
 * - Transactional state updates to ensure consistency
 * - Rich result reporting with detailed success/error information
 *
 * The synchronization process follows these phases:
 * 1. State loading and validation
 * 2. Linear data retrieval with filtering
 * 3. Existing Jira issue detection
 * 4. Data transformation and mapping
 * 5. Jira issue creation/updates
 * 6. State persistence and result reporting
 */
package bogdanpc.linearsync.synchronization;