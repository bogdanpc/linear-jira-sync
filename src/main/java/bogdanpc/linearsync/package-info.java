/**
 * One-way synchronization of Linear issues into a Jira project.
 * <p>
 * Linear stays the source of truth: the tool creates and updates Jira issues but never writes back to Linear, so there
 * is no conflict resolution. Runs are incremental — only issues changed since the last successful run are fetched —
 * and a local sync state maps each Linear issue to its Jira counterpart, so a re-run never creates duplicates.
 * <p>
 * The {@code linear} and {@code jira} components know nothing about each other; {@code synchronization} translates
 * between them, which keeps each API integration replaceable and testable on its own.
 */
package bogdanpc.linearsync;
