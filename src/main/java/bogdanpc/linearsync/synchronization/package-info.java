/**
 * Decides what has to change in Jira for the Linear issues of a run, and remembers what was synced.
 * <p>
 * Design decisions:
 * <ul>
 *   <li>The sync state file is the only memory between runs. It maps Linear ids to Jira keys, stores the Linear
 *       {@code updatedAt} of the last sync to skip unchanged issues, and records uploaded attachments. The state is
 *       written only after a run that changed something, and backed up before each run.</li>
 *   <li>If the state is lost, issues are recovered by finding the {@code [IDENTIFIER]} prefix in Jira summaries
 *       instead of being created a second time.</li>
 *   <li>Linear sub-issues become Jira subtasks, so parents are synced before their children. Related issues outside
 *       the fetched batch are loaded from Linear only when they were never synced.</li>
 *   <li>A failing issue does not stop the run; comments, attachments and status transitions are best effort and are
 *       retried with the next update of the issue.</li>
 * </ul>
 */
package bogdanpc.linearsync.synchronization;
