# Linear-Jira Synchronize

CLI tool to synchronize Linear issues to Jira projects.

## Features

- **One-way sync**: Synchronizes Linear issues to Jira
- **Incremental sync**: Only syncs new/updated issues since last run
- **State management**: Tracks synced issues to avoid duplicates
- **Filtering**: Sync specific teams, states or time ranges
- **Dry-run mode**: Preview changes without making actual updates

## Documentation

- **[Configuration Guide](CONFIGURATION.md)** - Detailed configuration options and security best practices
- **[Native Build Guide](docs/native-build.md)** - GraalVM setup and native executable build instructions
- **[Troubleshooting Guide](docs/troubleshooting.md)** - Common issues, debugging, and solutions

## Quick Start

1. **Clone and build the project:**
   ```bash
   git clone <repo-url>
   cd linear-jira-sync
   ./mvnw clean package
   ```

2. **Set your API credentials:**
   ```bash
   export LINEAR_API_TOKEN="lin_api_..."
   export JIRA_API_URL="https://yourcompany.atlassian.net"
   export JIRA_USERNAME="your@email.com"
   export JIRA_API_TOKEN="ATATT3..."
   export JIRA_PROJECT_KEY="PROJ"
   ```

3. **Test the connection:**
   ```bash
   java -jar target/quarkus-app/quarkus-run.jar test-connection
   ```

4. **Run your first sync (dry-run to preview):**
   ```bash
   java -jar target/quarkus-app/quarkus-run.jar sync --dry-run
   ```

5. **If everything looks good, run the actual sync:**
   ```bash
   java -jar target/quarkus-app/quarkus-run.jar sync
   ```

> **Alternative: Run without building** - Install [JBang](https://www.jbang.dev) and run directly: `jbang sync.java sync --dry-run`

## Prerequisites

- Java 21+
- Linear API token
- Jira API credentials (username + API token)

## Jira Setup Requirements

Before running the sync, ensure your Jira project is properly configured:

### 1. Custom Field for Linear ID (Required)
The tool needs a custom field in Jira to track Linear issue IDs and prevent duplicates:

1. Navigate to **Jira Settings → Issues → Custom Fields**
2. Click **Create custom field**
3. Select **Text Field (single line)**
4. Name it "Linear Issue ID"
5. Add it to the appropriate screens for your project
6. Note the custom field ID (e.g., `customfield_10000`)
   - Find this in the field configuration URL or via Jira API
7. Set the environment variable:
   ```bash
   export JIRA_LINEAR_ID_FIELD="customfield_10000"
   ```

### 2. Issue Type Configuration
- Ensure the "Task" issue type exists in your project (default)
- Or specify a different type via environment variable:
  ```bash
  export JIRA_ISSUE_TYPE="Story"  # or "Bug", "Epic", etc.
  ```

### 3. Required Jira Permissions
Your Jira API user needs these permissions in the target project:
- **Browse projects** - View project and issues
- **Create issues** - Create new issues from Linear
- **Edit issues** - Update existing synced issues
- **Add comments** - Add sync metadata as comments
- **Manage attachments** - Upload attachments from Linear (if enabled)

### 4. Optional: Priority Field
If you want to sync Linear priorities to Jira:
1. Ensure priority field is enabled in your project
2. Set the environment variable:
   ```bash
   export JIRA_ENABLE_PRIORITY="true"
   ```

## Configuration

The tool supports multiple configuration methods with the following priority (highest to lowest):

1. **Environment variables** (e.g., `LINEAR_API_TOKEN`)
2. **System properties** (e.g., `-Dlinear.api.token=...`)
3. **~/.linear-jira-sync/config.properties** (user-specific configuration)
4. **application-local.properties** (project-local configuration, not in git)
5. **application.properties** (default configuration)
6. **Default values**

### Environment Variables

```bash
export LINEAR_API_TOKEN="your_linear_api_token"
export JIRA_API_URL="https://your-domain.atlassian.net"
export JIRA_USERNAME="your-email@domain.com"
export JIRA_API_TOKEN="your_jira_api_token"
export JIRA_PROJECT_KEY="YOUR_PROJECT_KEY"
export JIRA_ISSUE_TYPE="Task"  # Optional, defaults to "Task"
export JIRA_LINEAR_ID_FIELD="customfield_10000"  # Required - see Jira Setup Requirements
```

### User Configuration Directory

1. Create and configure user settings:
   ```bash
   mkdir -p ~/.linear-jira-sync
   cp config.properties.example ~/.linear-jira-sync/config.properties
   # Edit ~/.linear-jira-sync/config.properties with your credentials
   chmod 600 ~/.linear-jira-sync/config.properties
   ```

2. Run the sync - configuration will be loaded automatically from all projects

**Benefits:** Works across multiple projects, secure, not in git

### Project-Local Configuration File

1. Copy the example configuration:
   ```bash
   cp application-local.properties.example application-local.properties
   ```

2. Edit `application-local.properties` with project-specific settings (this file is in `.gitignore`)

3. Run the sync - configuration will be loaded automatically

**Note:** Environment variables will override file-based configuration if both are present.

### Getting API Tokens

**Linear API Token:**
1. Go to Linear → Settings → API → Personal API Keys → Create Key
2. Copy the generated token

**Jira API Token:**
1. Go to https://id.atlassian.com/manage-profile/security/api-tokens
2. Create API token
3. Copy the generated token

For complete configuration documentation, see **[CONFIGURATION.md](CONFIGURATION.md)**

## Building

```bash
./mvnw clean package
```

## Usage

```bash
# Sync all issues
java -jar target/quarkus-app/quarkus-run.jar sync

# Dry run to see what would be synced
java -jar target/quarkus-app/quarkus-run.jar sync --dry-run

# Sync specific team
java -jar target/quarkus-app/quarkus-run.jar sync --team ENG

# Sync specific state
java -jar target/quarkus-app/quarkus-run.jar sync --state started

# Sync issues updated after specific date
java -jar target/quarkus-app/quarkus-run.jar sync --updated-after 2024-01-01T00:00:00Z

# Force full sync (ignore last sync time)
java -jar target/quarkus-app/quarkus-run.jar sync --force-full-sync
```

> **Note:** All commands can also be run with JBang: `jbang sync.java <command>`

### Development Mode
```bash
./mvnw quarkus:dev -Dquarkus.args='sync --dry-run --verbose'
```

## Command Line Options

```
Usage: linear-jira-sync [sync|status|reset] [OPTIONS]

Actions:
  sync     Synchronize Linear issues to Jira (default)
  status   Show current sync status
  reset    Reset sync state

Options:
  -t, --team TEAM                Linear team key to sync (e.g., 'ENG')
  -s, --state STATE              Filter by Linear issue state type
  -u, --updated-after DATETIME   Only sync issues updated after this ISO datetime
  -f, --force-full-sync          Force full synchronization, ignoring last sync time
  -d, --dry-run                  Show what would be done without making changes
  -v, --verbose                  Enable verbose output
  -q, --quiet                    Suppress non-error output
  -h, --help                     Show help message
  -V, --version                  Print version information
```

## State Management

The tool maintains a `.syncstate.json` file to track:
- Previously synced issues
- Last sync timestamp
- Issue mapping between Linear and Jira

This prevents duplicate issues and enables incremental syncing.

### State File Location

By default, the state file is stored in `~/.linear-jira-sync/.syncstate.json`.

**Why this location?**
- Persists across different project directories
- Works consistently when running from cron/systemd
- Follows Unix convention for user-specific application data

**To use a custom location**, provide an absolute path:

```bash
# Via environment variable
export SYNC_STORAGE_LOCATION="/var/lib/linear-jira-sync"

# Via command-line option
java -jar target/quarkus-app/quarkus-run.jar sync --state-dir /var/lib/linear-jira-sync
```

**For development/testing**, you can use the current directory:
```bash
java -jar target/quarkus-app/quarkus-run.jar sync --state-dir .
```

## Issue Mapping

### Linear → Jira Field Mapping
- **Title**: `[LINEAR-123] Issue Title`
- **Description**: Original description + Linear metadata
- **Priority**: Linear priority (0-4) → Jira priority (Highest/High/Medium/Low)
- **Labels**: Linear labels → Jira labels (sanitized)
- **Assignee**: Not mapped (manual assignment in Jira)
- **Status**: Not automatically mapped (manual workflow in Jira)

### Custom Fields
- Linear Issue ID is stored in Jira custom field `customfield_10000` for tracking

## Examples

### Daily Sync Workflow
```bash
# Incremental sync since last run
java -jar target/quarkus-app/quarkus-run.jar sync --verbose

# Sync specific team's issues
java -jar target/quarkus-app/quarkus-run.jar sync --team BACKEND

# Force resync of all issues
java -jar target/quarkus-app/quarkus-run.jar sync --force-full-sync
```

## Development

### Building and Running

```bash
# Development mode with live reload
./mvnw quarkus:dev -Dquarkus.args='sync --dry-run'

# Package application
./mvnw package

# Run packaged application
java -jar target/quarkus-app/quarkus-run.jar --help

# Build native executable
./mvnw package -Dnative
```

### Native Executable

For faster startup and lower memory usage:

```bash
./mvnw package -Dnative
./target/linear-jira-sync-1.0.0-SNAPSHOT-runner sync
```

See **[docs/native-build.md](docs/native-build.md)** for GraalVM installation and native build requirements.

### Configuration Files
The application uses `src/main/resources/application.properties` for configuration. All settings can be overridden with environment variables.

### Running Tests
```bash
# Run all tests
./mvnw test

# Run only unit tests
./mvnw test -Dtest="*Test"

# Run only integration tests
./mvnw test -Dtest="*IT"
```

#### Test Coverage
The project includes comprehensive tests covering:

- **LinearServiceTest**: Tests Linear GraphQL API client with WireMock
  - Issue fetching with pagination
  - Error handling and network failures
  - Query filtering (team, state, date)

- **JiraServiceTest**: Tests Jira REST API client with WireMock
  - Issue creation and updates
  - Search functionality
  - Authentication and error scenarios

- **SyncEngineTest**: Integration tests for sync logic with mocked dependencies
  - New issue synchronization
  - Update detection and handling
  - Dry-run mode verification
  - Error recovery and rollback

- **StateManagerTest**: Tests state persistence and file operations
  - JSON serialization/deserialization
  - File backup and recovery
  - State validation and corruption handling

- **IssueTransformerTest**: Tests Linear-to-Jira field mapping
  - Priority and status mapping
  - Label sanitization
  - Description formatting

## Troubleshooting

For common issues and solutions, see **[docs/troubleshooting.md](docs/troubleshooting.md)**.

Quick diagnostics:
```bash
# Test API connections
java -jar target/quarkus-app/quarkus-run.jar test-connection

# Run with verbose logging
java -jar target/quarkus-app/quarkus-run.jar sync --dry-run --verbose

# Check sync state
jq '.' ~/.linear-jira-sync/.syncstate.json
```

## Production Deployment

For production deployment guides including cron, systemd, Docker, and Kubernetes, see **[docs/production-deployment.md](docs/production-deployment.md)**.

Quick start for scheduled sync:
```bash
# Every 30 minutes via cron
*/30 * * * * cd /path/to/linear-jira-sync && java -jar target/quarkus-app/quarkus-run.jar sync --quiet >> lsync.log 2>&1
```

## Limitations

### Sync Limitations
- **One-way sync only**: Changes flow from Linear to Jira, not vice versa
- **No status mapping**: Linear and Jira workflows must be managed independently
- **No assignee sync**: Assignees must be manually set in Jira
- **No bidirectional updates**: Updates in Jira won't reflect back to Linear

### Technical Limitations
- **Attachment size**: Maximum 10MB per file by default (configurable via `ATTACHMENT_MAX_SIZE`)
- **Batch size**: Syncs all matching issues in a single run (no built-in pagination)
- **Field mapping**: Limited to supported fields (title, description, priority, labels)

### API Rate Limits
- **Linear API**:
  - Rate limit: 1,500 requests per hour
  - Source: [Linear API Documentation](https://developers.linear.app/docs/graphql/working-with-the-graphql-api#rate-limiting)

- **Jira Cloud API**:
  - Rate limit varies by Jira plan (Free: 5,000 requests per hour)
  - Source: [Atlassian Rate Limiting Documentation](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/)
  - Additional concurrent request limits may apply

### Workarounds for Rate Limits
- Use team/state filtering to sync in smaller batches
- Implement scheduled syncs with delays between runs
- Monitor rate limit headers in API responses when debugging
