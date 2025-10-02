# Troubleshooting Guide

This guide covers common issues and solutions when using the Linear-Jira Sync tool.

## Common Issues and Solutions

### 1. Authentication Errors

**Error:** `401 Unauthorized` or `403 Forbidden`

**Solutions:**
- Verify API tokens are correct and not expired
- Check Jira URL format (must include `https://`)
- For Linear: Regenerate token in Linear Settings → API
- For Jira: Create new token at https://id.atlassian.com/manage-profile/security/api-tokens
- Test connection with: `java -jar target/quarkus-app/quarkus-run.jar test-connection`

**Verify your credentials:**
```bash
# Check environment variables are set
env | grep -E "LINEAR|JIRA"

# Test Linear API
curl -H "Authorization: $LINEAR_API_TOKEN" https://api.linear.app/graphql \
  -d '{"query":"{ viewer { id name } }"}'

# Test Jira API
curl -u $JIRA_USERNAME:$JIRA_API_TOKEN \
  $JIRA_API_URL/rest/api/3/myself
```

### 2. Permission Errors

**Error:** `You do not have permission to create issues in project`

**Solutions:**
- Ensure Jira user has "Create Issues" permission in target project
- Verify Linear token has read access to the workspace
- Check project key is correct in `JIRA_PROJECT_KEY`

**Required Jira Permissions:**
- Browse projects
- Create issues
- Edit issues
- Add comments
- Manage attachments (if attachment sync enabled)

**Verify Jira permissions:**
```bash
# Check your permissions for a project
curl -u $JIRA_USERNAME:$JIRA_API_TOKEN \
  "$JIRA_API_URL/rest/api/3/mypermissions?projectKey=$JIRA_PROJECT_KEY"
```

### 3. Custom Field Errors

**Error:** `Field 'customfield_10000' cannot be set`

**Solutions:**
- Verify the custom field ID matches your Jira instance
- Update `JIRA_LINEAR_ID_FIELD` environment variable with correct ID
- Ensure field is added to the Create Issue screen in your project

**Find correct custom field ID:**
```bash
# List all custom fields
curl -u $JIRA_USERNAME:$JIRA_API_TOKEN \
  $JIRA_API_URL/rest/api/3/field | grep -i linear

# Or search for text fields
curl -u $JIRA_USERNAME:$JIRA_API_TOKEN \
  $JIRA_API_URL/rest/api/3/field | jq '.[] | select(.schema.type=="string") | {id, name}'
```

### 4. State File Issues

**Error:** `Failed to read sync state` or corrupted `.syncstate.json`

**Solutions:**
- Backup current state: `cp .syncstate.json .syncstate.backup.json`
- Reset state to start fresh: `java -jar target/quarkus-app/quarkus-run.jar reset`
- Manually fix corrupted JSON or delete file to restart sync

**State File Management:**
```bash
# View current state
cat .syncstate.json | jq '.'

# Backup before major operations
cp .syncstate.json .syncstate.$(date +%Y%m%d).backup.json

# Remove specific issue from state (if stuck)
jq 'del(.syncedIssues["LINEAR-123"])' .syncstate.json > temp.json && mv temp.json .syncstate.json

# Reset to empty state
echo '{"lastSyncTime":null,"syncedIssues":{},"issueMapping":{}}' > .syncstate.json
```

### 5. Network and Timeout Issues

**Error:** `Connection timeout` or `Read timeout`

**Solutions:**
- Check network connectivity to Linear and Jira APIs
- Increase timeout settings
- For attachment issues, adjust download timeout

**Increase timeouts:**
```bash
# General REST client timeouts (in milliseconds)
export QUARKUS_REST_CLIENT_READ_TIMEOUT=60000
export QUARKUS_REST_CLIENT_CONNECT_TIMEOUT=30000

# Attachment-specific timeouts
export ATTACHMENT_DOWNLOAD_TIMEOUT=60  # seconds
export ATTACHMENT_MAX_SIZE=20971520    # 20MB in bytes
```

**Test connectivity:**
```bash
# Test Linear API
curl -w "\nTime: %{time_total}s\n" https://api.linear.app/graphql

# Test Jira API
curl -w "\nTime: %{time_total}s\n" $JIRA_API_URL/rest/api/3/myself
```

### 6. Rate Limiting

**Error:** `429 Too Many Requests`

**Solutions:**
- Add delays between sync runs
- Use filtering to sync smaller batches
- Wait for rate limit reset

**Batch sync by team:**
```bash
# Sync teams separately
java -jar target/quarkus-app/quarkus-run.jar sync --team TEAM1
sleep 60
java -jar target/quarkus-app/quarkus-run.jar sync --team TEAM2
```

**Rate Limits:**
- **Linear API**: 1,500 requests per hour ([documentation](https://developers.linear.app/docs/graphql/working-with-the-graphql-api#rate-limiting))
- **Jira Cloud API**: Varies by plan, typically 5,000 requests/hour for free tier ([documentation](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/))

### 7. Attachment Sync Failures

**Error:** `Failed to download attachment` or `Attachment too large`

**Solutions:**
- Disable attachment sync: `export ATTACHMENT_SYNC_ENABLED=false`
- Increase size limit: `export ATTACHMENT_MAX_SIZE=20971520` (20MB)
- Increase timeout: `export ATTACHMENT_DOWNLOAD_TIMEOUT=60`

**Debug attachment issues:**
```bash
# Run with verbose logging
java -jar target/quarkus-app/quarkus-run.jar sync --verbose

# Check attachment configuration
env | grep ATTACHMENT
```

### 8. Issue Type Not Found

**Error:** `Issue type 'Task' does not exist in project`

**Solutions:**
- Verify issue type exists in your Jira project
- Change issue type: `export JIRA_ISSUE_TYPE="Story"`

**List available issue types:**
```bash
curl -u $JIRA_USERNAME:$JIRA_API_TOKEN \
  "$JIRA_API_URL/rest/api/3/project/$JIRA_PROJECT_KEY" | \
  jq '.issueTypes[] | {id, name}'
```

### 9. Priority Mapping Errors

**Error:** `Priority field cannot be set`

**Solutions:**
- Disable priority sync: `export JIRA_ENABLE_PRIORITY=false`
- Verify priority field is enabled in your project
- Check priority values match Jira configuration

**List available priorities:**
```bash
curl -u $JIRA_USERNAME:$JIRA_API_TOKEN \
  "$JIRA_API_URL/rest/api/3/priority" | jq '.[] | {id, name}'
```

### 10. Duplicate Issues Created

**Issue:** Same Linear issue creates multiple Jira issues

**Solutions:**
- Check `JIRA_LINEAR_ID_FIELD` is set correctly
- Verify custom field exists and is searchable
- Reset state and re-sync: `java -jar target/quarkus-app/quarkus-run.jar reset`

**Verify custom field is working:**
```bash
# Search for issues with Linear ID
curl -u $JIRA_USERNAME:$JIRA_API_TOKEN \
  "$JIRA_API_URL/rest/api/3/search?jql=$JIRA_LINEAR_ID_FIELD~'LIN-*'" | \
  jq '.issues[] | {key, fields.'$JIRA_LINEAR_ID_FIELD'}'
```

## Debug Logging

### Enable Verbose Output

```bash
# Application-level verbose mode
java -jar target/quarkus-app/quarkus-run.jar sync --verbose

# Maximum debug logging
export QUARKUS_LOG_LEVEL=DEBUG
export QUARKUS_LOG_CATEGORY_"com.github.bogdanpc"_LEVEL=DEBUG
java -jar target/quarkus-app/quarkus-run.jar sync
```

### HTTP Request/Response Logging

```bash
# Log all HTTP traffic
export QUARKUS_REST_CLIENT_LOGGING_SCOPE=request-response
export QUARKUS_REST_CLIENT_LOGGING_BODY_LIMIT=1024
export QUARKUS_LOG_CATEGORY_"org.jboss.resteasy.reactive.client"_LEVEL=DEBUG
```

### Log to File

```bash
# Redirect output to file
java -jar target/quarkus-app/quarkus-run.jar sync --verbose > sync.log 2>&1

# View logs in real-time
tail -f sync.log

# Search for errors
grep -i error sync.log
```

## Performance Issues

### Slow Sync Performance

**Symptoms:** Sync takes very long or hangs

**Solutions:**
1. **Use filtering to reduce scope:**
   ```bash
   # Sync specific team
   java -jar target/quarkus-app/quarkus-run.jar sync --team ENG

   # Sync recent issues only
   java -jar target/quarkus-app/quarkus-run.jar sync --updated-after 2024-01-01T00:00:00Z
   ```

2. **Check network latency:**
   ```bash
   # Ping Linear API
   time curl https://api.linear.app/graphql -I

   # Ping Jira API
   time curl $JIRA_API_URL -I
   ```

3. **Disable attachment sync:**
   ```bash
   export ATTACHMENT_SYNC_ENABLED=false
   ```

### Memory Issues

**Error:** `OutOfMemoryError` or JVM crashes

**Solutions:**
```bash
# Increase heap size
java -Xmx512m -jar target/quarkus-app/quarkus-run.jar sync

# Use incremental sync instead of force-full-sync
java -jar target/quarkus-app/quarkus-run.jar sync
```

## Getting Help

### Diagnostic Checklist

Before seeking help, gather this information:

```bash
# 1. Version information
java -jar target/quarkus-app/quarkus-run.jar --version

# 2. Configuration check
env | grep -E "LINEAR|JIRA" | sed 's/=.*/=***/'  # Hides tokens

# 3. Test connectivity
java -jar target/quarkus-app/quarkus-run.jar test-connection

# 4. Check state file
cat .syncstate.json | jq '{lastSyncTime, issueCount: (.syncedIssues | length)}'

# 5. Run with verbose logging
java -jar target/quarkus-app/quarkus-run.jar sync --dry-run --verbose 2>&1 | tee debug.log
```

### Useful Commands

```bash
# View command help
java -jar target/quarkus-app/quarkus-run.jar --help

# Check sync status
java -jar target/quarkus-app/quarkus-run.jar status

# Dry run to preview changes
java -jar target/quarkus-app/quarkus-run.jar sync --dry-run --verbose

# Reset and start fresh
java -jar target/quarkus-app/quarkus-run.jar reset
```

### Report Issues

When reporting issues, include:
- Error message and stack trace
- Command used
- Environment (OS, Java version)
- Relevant configuration (with tokens redacted)
- Output from diagnostic checklist above
