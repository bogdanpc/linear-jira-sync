/**
 * Command-Line Interface Business Component
 *
 * This package implements the CLI boundary for the Linear-Jira synchronization tool.
 * It provides user-facing commands for interacting with the synchronization system
 * and Linear API directly.
 *
 * The CLI uses PicoCLI framework for command parsing and execution. All classes
 * in the boundary package handle user input validation, command orchestration,
 * and output formatting.
 *
 * Available Commands:
 * - sync: Main synchronization command with various filtering options
 * - list: Display Linear issues without performing synchronization
 * - read: Retrieve detailed information about a specific Linear issue
 *
 * Design Decisions:
 * - Commands are stateless and delegate business logic to control layer services
 * - Input validation is performed at the boundary before delegation
 * - User-friendly error messages and help text are provided for all commands
 * - Output formatting is consistent across all commands for usability
 */
package bogdanpc.linearsync.cli;