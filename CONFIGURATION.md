# Configuration Guide

This document explains how to configure the Linear-Jira Sync tool.

## Configuration Priority

The application follows this configuration priority (highest to lowest):

1. **Environment variables** (e.g., `LINEAR_API_TOKEN`)
2. **System properties** (e.g., `-Dlinear.api.token=...`)
3. **~/.linear-jira-sync/config.properties** (user-specific configuration)
4. **application-local.properties** (project-local configuration, not in git)
5. **application.properties** (default configuration)
6. **Default values**

This means environment variables will always override file-based configuration, allowing you to securely manage
credentials in production while using convenient file-based configuration for local development.

The `~/.linear-jira-sync/config.properties` file is useful when you work with multiple Linear-Jira sync projects and
want to share the same credentials across all of them. The `application-local.properties` file is useful for
project-specific overrides (e.g., different Jira project keys).

## Quick Start

### Option 1: Environment Variables (Recommended for Production)

```bash
export LINEAR_API_TOKEN=lin_api_xxxxx
export JIRA_API_CLOUDID=your-cloud-id              # Atlassian Cloud ID (preferred)
# export JIRA_API_URL=https://your-domain.atlassian.net  # Alternative to Cloud ID
export JIRA_USERNAME=your-email@example.com
export JIRA_API_TOKEN=your_jira_token
export JIRA_PROJECT_KEY=PROJ

# Run the sync
./ljs
```

### Option 2: User Configuration Directory (Recommended for Development)

Create a user-specific configuration that works across all Linear-Jira sync projects:

1. Create the configuration directory and file:
   ```bash
   mkdir -p ~/.linear-jira-sync
   cp config.properties.example ~/.linear-jira-sync/config.properties
   ```

2. Edit `~/.linear-jira-sync/config.properties` with your credentials

3. Secure the file:
   ```bash
   chmod 600 ~/.linear-jira-sync/config.properties
   ```

4. Run the sync from any project - configuration is automatically loaded

**Benefits:**

- Works across multiple Linear-Jira sync projects
- Stored in standard user config location
- Not tracked in git
- Can be overridden per-project with `application-local.properties`

### Option 3: Project-Local Configuration File

For project-specific configuration:

1. Copy the example configuration:
   ```bash
   cp application-local.properties.example application-local.properties
   ```

2. Edit `application-local.properties` with project-specific settings (e.g., different `jira.project.key`)

3. Run the sync - this will override settings from `~/.linear-jira-sync/config.properties`

**Important:** `application-local.properties` is in `.gitignore` and will never be committed to version control.

### Option 4: Edit application.properties Directly

You can edit `src/main/resources/application.properties` directly, but this is **not recommended** for credentials since
this file is tracked in git.

## Required Configuration

### Linear API

| Property           | Environment Variable | Description           | Required                                          |
|--------------------|----------------------|-----------------------|---------------------------------------------------|
| `linear.api.url`   | -                    | Linear API endpoint   | No (defaults to `https://api.linear.app/graphql`) |
| `linear.api.token` | `LINEAR_API_TOKEN`   | Your Linear API token | **Yes**                                           |

**Getting a Linear API Token:**

1. Go to Linear Settings → API
2. Create a new Personal API Key
3. Copy the token (starts with `lin_api_`)

### Jira API

| Property            | Environment Variable | Description                        | Required                       |
|---------------------|----------------------|------------------------------------|--------------------------------|
| `jira.api.cloudid`  | `JIRA_API_CLOUDID`   | Atlassian Cloud ID                 | **Yes** (or `jira.api.url`)    |
| `jira.api.url`      | `JIRA_API_URL`       | Your Jira instance URL (direct)    | **Yes** (or `jira.api.cloudid`)|
| `jira.api.username` | `JIRA_USERNAME`      | Your Jira email address            | **Yes**                        |
| `jira.api.token`    | `JIRA_API_TOKEN`     | Your Jira API token                | **Yes**                        |
| `jira.project.key`  | `JIRA_PROJECT_KEY`   | Target Jira project key            | **Yes**                        |

> **Cloud ID vs URL:** Atlassian now requires API token requests to use Cloud ID-based URLs (`https://api.atlassian.com/ex/jira/{cloudId}`). When `jira.api.cloudid` is set, the tool automatically constructs this URL. The direct `jira.api.url` option is still supported as an alternative.

**Finding your Atlassian Cloud ID:**

```bash
curl https://your-domain.atlassian.net/_edge/tenant_info
```

The `cloudId` field in the JSON response is the value you need.

**Getting a Jira API Token:**

1. Go to https://id.atlassian.com/manage-profile/security/api-tokens
2. Create a new API token
3. Copy the token

### Optional Configuration

#### Jira Settings

| Property                      | Environment Variable   | Default | Description                                 |
|-------------------------------|------------------------|---------|---------------------------------------------|
| `jira.issue.type`             | `JIRA_ISSUE_TYPE`      | `Task`  | Issue type for created issues               |
| `jira.custom-field.linear-id` | `JIRA_LINEAR_ID_FIELD` | -       | Custom field ID for storing Linear issue ID |
| `jira.enable-priority`        | `JIRA_ENABLE_PRIORITY` | `false` | Enable priority field mapping               |

#### Sync Settings

| Property                   | Environment Variable          | Default                     | Description                               |
|----------------------------|-------------------------------|-----------------------------|-------------------------------------------|
| `sync.dry-run`             | -                             | `false`                     | Enable dry-run mode by default            |
| `sync.storage.location`    | `LINEARSYNC_STORAGE_LOCATION` | State file storage location |
| `sync.storage.max-backups` | `LINEARSYNC_MAX_BACKUPS`      | `5`                         | Maximum number of state file backups      |

#### Attachment Settings

| Property                       | Environment Variable          | Default    | Description                             |
|--------------------------------|-------------------------------|------------|-----------------------------------------|
| `attachment.sync.enabled`      | `ATTACHMENT_SYNC_ENABLED`     | `true`     | Enable attachment synchronization       |
| `attachment.download.timeout`  | `ATTACHMENT_DOWNLOAD_TIMEOUT` | `30`       | Download timeout in seconds             |
| `attachment.download.max-size` | `ATTACHMENT_MAX_SIZE`         | `10485760` | Maximum attachment size in bytes (10MB) |

## Configuration Examples

### Example 1: Production with Environment Variables

```bash
#!/bin/bash
# production-sync.sh

export LINEAR_API_TOKEN="lin_api_xxxxx"
export JIRA_API_CLOUDID="your-cloud-id"
export JIRA_USERNAME="bot@company.com"
export JIRA_API_TOKEN="xxxxx"
export JIRA_PROJECT_KEY="ENG"
export JIRA_ENABLE_PRIORITY="true"

java -jar target/quarkus-app/quarkus-run.jar sync
```

### Example 2: Development with application-local.properties

```properties
# application-local.properties
linear.api.token=lin_api_xxxxx
jira.api.cloudid=your-cloud-id
jira.api.username=dev@example.com
jira.api.token=xxxxx
jira.project.key=DEV
# Enable dry-run by default for dev
sync.dry-run=true
```

### Example 3: Mixed Configuration

You can combine file-based and environment variable configuration. Environment variables will override file settings:

```bash
# Use file for most config, but override sensitive values via env vars
export LINEAR_API_TOKEN="lin_api_xxxxx"
export JIRA_API_TOKEN="xxxxx"

# These come from application-local.properties:
# - jira.api.cloudid (or jira.api.url)
# - jira.api.username
# - jira.project.key

./ljs
```

### Example 4: Using System Properties

```bash
# Override individual properties at runtime
java -Djira.project.key=TEST \
     -Dsync.dry-run=true \
     -jar target/quarkus-app/quarkus-run.jar sync
```

## Testing Your Configuration

Use the `test-connection` command to validate your configuration:

```bash
./ljs test-connection
```

This will:

1. Validate all required configuration is present
2. Test connectivity to Linear API
3. Test connectivity to Jira API
4. Report any configuration errors

## Security Best Practices

1. **Never commit credentials to git**
    - Use `application-local.properties` for local development
    - Use environment variables for production
    - Never edit credentials directly in `application.properties`

2. **Use API tokens, not passwords**
    - Both Linear and Jira support API tokens
    - API tokens can be revoked without changing passwords

3. **Limit token permissions**
    - Linear: Use tokens with minimal required scopes
    - Jira: Create a dedicated service account for the sync

4. **Rotate tokens regularly**
    - Update tokens on a regular schedule
    - Immediately revoke tokens if compromised

5. **Secure your environment**
    - Use secret management tools in CI/CD (e.g., GitHub Secrets, AWS Secrets Manager)
    - Set restrictive file permissions on local config files: `chmod 600 application-local.properties`

## Jira Field Configuration Issues

### Priority Field

If you get an error about the priority field not being available:

1. Set `JIRA_ENABLE_PRIORITY=false` to disable priority syncing
2. Or configure your Jira project to include the priority field on the create/edit screen

### Custom Field for Linear ID

If you get an error about a custom field:

1. Leave `JIRA_LINEAR_ID_FIELD` empty to disable Linear ID tracking
2. Or create a custom text field in Jira and set `JIRA_LINEAR_ID_FIELD` to its ID (e.g., "customfield_10001")

### Finding Custom Field IDs

To find the correct custom field ID in Jira:

1. Go to Jira Settings > Issues > Custom Fields
2. Find your custom field and note the ID in the URL or field configuration

## Troubleshooting

### Configuration not loading

If configuration isn't being loaded:

1. Check file location:
   ```bash
   ls -la application-local.properties
   ```

2. Verify Quarkus can find the file:
   ```bash
   ./mvnw quarkus:dev -Dquarkus.args='test-connection'
   ```

3. Check configuration priority - environment variables override files

### Environment variables not working

1. Verify the variable is set:
   ```bash
   echo $LINEAR_API_TOKEN
   ```

2. Ensure the variable is exported:
   ```bash
   export LINEAR_API_TOKEN=value
   ```

3. Check for typos in variable names (they're case-sensitive)

### Configuration validation errors

Run with verbose output to see detailed error messages:

```bash
java -jar target/quarkus-app/quarkus-run.jar sync -v
```

## Advanced Configuration

### Custom Configuration Profiles

Quarkus supports multiple configuration profiles. Create profile-specific files:

- `application-dev.properties` - Development profile
- `application-staging.properties` - Staging profile
- `application-prod.properties` - Production profile

Activate with:

```bash
java -Dquarkus.profile=staging -jar target/quarkus-app/quarkus-run.jar sync
```

### Configuration from External Files

Override configuration file location:

```bash
java -Dquarkus.config.locations=/path/to/config.properties \
     -jar target/quarkus-app/quarkus-run.jar sync
```

### Programmatic Configuration Access

The `SyncConfiguration` class provides programmatic access to all configuration:

```java

@Inject
SyncConfiguration config;

public void example() {
    String token = config.linearApiToken.orElseThrow();
    boolean dryRun = config.syncDryRun;
    config.validate(); // Throws ConfigurationException if invalid
}
```
