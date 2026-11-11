# Production Deployment Guide

This guide covers deploying and running the Linear-Jira Sync tool in production environments.

## Prerequisites

- Java 21+ runtime (or native executable)
- Linear and Jira API credentials
- File system access for state persistence
- Network access to Linear and Jira APIs

## Deployment Options

### Option 1: Scheduled Cron Jobs

Use cron to run periodic syncs:

```bash
# Edit crontab
crontab -e

# Add sync job - every 30 minutes
*/30 * * * * cd /path/to/linear-jira-sync && java -jar target/quarkus-app/quarkus-run.jar sync --quiet >> /var/log/linear-jira-sync/sync.log 2>&1

# With environment file
*/30 * * * * . /path/to/.env && cd /path/to/linear-jira-sync && java -jar target/quarkus-app/quarkus-run.jar sync --quiet >> /var/log/linear-jira-sync/sync.log 2>&1

# Different intervals for different teams
0 * * * * cd /path/to/linear-jira-sync && java -jar target/quarkus-app/quarkus-run.jar sync --team FRONTEND --quiet
30 * * * * cd /path/to/linear-jira-sync && java -jar target/quarkus-app/quarkus-run.jar sync --team BACKEND --quiet
```

**Environment file (`.env`):**
```bash
export LINEAR_API_TOKEN="lin_api_..."
export JIRA_API_URL="https://yourcompany.atlassian.net"
export JIRA_USERNAME="sync@yourcompany.com"
export JIRA_API_TOKEN="ATATT3..."
export JIRA_PROJECT_KEY="PROJ"
export JIRA_LINEAR_ID_FIELD="customfield_10000"
```

### Option 2: Systemd Service (Linux)

Create a systemd timer for scheduled execution:

**Service file** (`/etc/systemd/system/linear-jira-sync.service`):
```ini
[Unit]
Description=Linear-Jira Sync Service
After=network.target

[Service]
Type=oneshot
User=syncuser
Group=syncuser
WorkingDirectory=/opt/linear-jira-sync
EnvironmentFile=/opt/linear-jira-sync/.env
ExecStart=/usr/bin/java -jar /opt/linear-jira-sync/target/quarkus-app/quarkus-run.jar sync --quiet
StandardOutput=append:/var/log/linear-jira-sync/sync.log
StandardError=append:/var/log/linear-jira-sync/error.log

[Install]
WantedBy=multi-user.target
```

**Timer file** (`/etc/systemd/system/linear-jira-sync.timer`):
```ini
[Unit]
Description=Linear-Jira Sync Timer
Requires=linear-jira-sync.service

[Timer]
OnCalendar=*:0/30
Persistent=true

[Install]
WantedBy=timers.target
```

**Enable and start:**
```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable timer
sudo systemctl enable linear-jira-sync.timer

# Start timer
sudo systemctl start linear-jira-sync.timer

# Check status
sudo systemctl status linear-jira-sync.timer
sudo systemctl list-timers linear-jira-sync.timer
```

### Option 3: Docker Container

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy application
COPY target/quarkus-app/ /app/

# Create user
RUN addgroup -g 1000 syncuser && \
    adduser -D -u 1000 -G syncuser syncuser && \
    chown -R syncuser:syncuser /app

USER syncuser

# Volume for state persistence
VOLUME ["/app/.syncstate"]

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
CMD ["sync"]
```

**Build and run:**
```bash
# Build image
docker build -t linear-jira-sync:latest .

# Run sync
docker run --rm \
  -e LINEAR_API_TOKEN="$LINEAR_API_TOKEN" \
  -e JIRA_API_URL="$JIRA_API_URL" \
  -e JIRA_USERNAME="$JIRA_USERNAME" \
  -e JIRA_API_TOKEN="$JIRA_API_TOKEN" \
  -e JIRA_PROJECT_KEY="$JIRA_PROJECT_KEY" \
  -v $(pwd)/.syncstate:/app/.syncstate \
  linear-jira-sync:latest sync --verbose

# With docker-compose
cat > docker-compose.yml <<EOF
version: '3.8'
services:
  sync:
    image: linear-jira-sync:latest
    environment:
      - LINEAR_API_TOKEN=\${LINEAR_API_TOKEN}
      - JIRA_API_URL=\${JIRA_API_URL}
      - JIRA_USERNAME=\${JIRA_USERNAME}
      - JIRA_API_TOKEN=\${JIRA_API_TOKEN}
      - JIRA_PROJECT_KEY=\${JIRA_PROJECT_KEY}
      - JIRA_LINEAR_ID_FIELD=\${JIRA_LINEAR_ID_FIELD}
    volumes:
      - ./data:/app/.syncstate
    command: sync --quiet
EOF

docker-compose run --rm sync
```

### Option 4: Kubernetes CronJob

**CronJob manifest** (`k8s/cronjob.yaml`):
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: linear-jira-sync
  namespace: default
spec:
  schedule: "*/30 * * * *"  # Every 30 minutes
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure
          containers:
          - name: sync
            image: linear-jira-sync:latest
            imagePullPolicy: IfNotPresent
            args: ["sync", "--quiet"]
            env:
            - name: LINEAR_API_TOKEN
              valueFrom:
                secretKeyRef:
                  name: linear-jira-sync-secrets
                  key: linear-api-token
            - name: JIRA_API_URL
              valueFrom:
                configMapKeyRef:
                  name: linear-jira-sync-config
                  key: jira-api-url
            - name: JIRA_USERNAME
              valueFrom:
                secretKeyRef:
                  name: linear-jira-sync-secrets
                  key: jira-username
            - name: JIRA_API_TOKEN
              valueFrom:
                secretKeyRef:
                  name: linear-jira-sync-secrets
                  key: jira-api-token
            - name: JIRA_PROJECT_KEY
              valueFrom:
                configMapKeyRef:
                  name: linear-jira-sync-config
                  key: jira-project-key
            - name: JIRA_LINEAR_ID_FIELD
              valueFrom:
                configMapKeyRef:
                  name: linear-jira-sync-config
                  key: jira-linear-id-field
            volumeMounts:
            - name: syncstate
              mountPath: /app/.syncstate
            resources:
              requests:
                memory: "128Mi"
                cpu: "100m"
              limits:
                memory: "256Mi"
                cpu: "500m"
          volumes:
          - name: syncstate
            persistentVolumeClaim:
              claimName: linear-jira-sync-state
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: linear-jira-sync-state
  namespace: default
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 100Mi
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: linear-jira-sync-config
  namespace: default
data:
  jira-api-url: "https://yourcompany.atlassian.net"
  jira-project-key: "PROJ"
  jira-linear-id-field: "customfield_10000"
---
apiVersion: v1
kind: Secret
metadata:
  name: linear-jira-sync-secrets
  namespace: default
type: Opaque
data:
  linear-api-token: <base64-encoded-token>
  jira-username: <base64-encoded-username>
  jira-api-token: <base64-encoded-token>
```

**Deploy:**
```bash
# Create secrets (replace with your actual tokens)
kubectl create secret generic linear-jira-sync-secrets \
  --from-literal=linear-api-token="$LINEAR_API_TOKEN" \
  --from-literal=jira-username="$JIRA_USERNAME" \
  --from-literal=jira-api-token="$JIRA_API_TOKEN"

# Apply configuration
kubectl apply -f k8s/cronjob.yaml

# Check status
kubectl get cronjobs
kubectl get jobs
kubectl logs -l job-name=<job-name>
```

## Monitoring and Alerting

### 1. Log Monitoring

```bash
# Create log directory
sudo mkdir -p /var/log/linear-jira-sync
sudo chown syncuser:syncuser /var/log/linear-jira-sync

# Check for errors in logs
grep -i error /var/log/linear-jira-sync/sync.log | tail -20

# Monitor sync duration
grep "Sync completed" /var/log/linear-jira-sync/sync.log | tail -5

# Real-time monitoring
tail -f /var/log/linear-jira-sync/sync.log
```

### 2. State File Monitoring

```bash
# Check last sync time
jq '.lastSyncTime' .syncstate.json

# Count synced issues
jq '.syncedIssues | length' .syncstate.json

# View recent mappings
jq '.issueMapping | to_entries | sort_by(.value) | reverse | .[0:5]' .syncstate.json
```

### 3. Health Check Script

**`/opt/linear-jira-sync/healthcheck.sh`:**
```bash
#!/bin/bash
set -e

SYNC_DIR="/opt/linear-jira-sync"
STATE_FILE="$SYNC_DIR/.syncstate.json"
MAX_AGE_SECONDS=7200  # 2 hours

# Check if state file exists
if [ ! -f "$STATE_FILE" ]; then
    echo "ERROR: State file not found"
    exit 1
fi

# Get last sync time
LAST_SYNC=$(jq -r '.lastSyncTime' "$STATE_FILE")
if [ "$LAST_SYNC" = "null" ]; then
    echo "WARNING: No sync has been performed yet"
    exit 0
fi

# Check age of last sync
CURRENT_TIME=$(date -u +%s)
LAST_SYNC_EPOCH=$(date -d "$LAST_SYNC" +%s 2>/dev/null || echo 0)
DIFF=$((CURRENT_TIME - LAST_SYNC_EPOCH))

if [ $DIFF -gt $MAX_AGE_SECONDS ]; then
    echo "ERROR: No sync for $((DIFF / 60)) minutes (threshold: $((MAX_AGE_SECONDS / 60)) minutes)"
    # Send alert (email, Slack, PagerDuty, etc.)
    exit 1
fi

echo "OK: Last sync was $((DIFF / 60)) minutes ago"
exit 0
```

**Run via cron:**
```bash
# Check every hour
0 * * * * /opt/linear-jira-sync/healthcheck.sh || mail -s "Linear-Jira Sync Health Check Failed" admin@example.com
```

### 4. Metrics Collection

**Custom metrics script** (`/opt/linear-jira-sync/metrics.sh`):
```bash
#!/bin/bash

STATE_FILE=".syncstate.json"

# Extract metrics
LAST_SYNC=$(jq -r '.lastSyncTime' "$STATE_FILE")
ISSUE_COUNT=$(jq '.syncedIssues | length' "$STATE_FILE")
MAPPING_COUNT=$(jq '.issueMapping | length' "$STATE_FILE")

# Output in Prometheus format
cat <<EOF
# HELP linear_jira_sync_last_sync_timestamp Last successful sync timestamp
# TYPE linear_jira_sync_last_sync_timestamp gauge
linear_jira_sync_last_sync_timestamp $(date -d "$LAST_SYNC" +%s 2>/dev/null || echo 0)

# HELP linear_jira_sync_issues_total Total number of synced issues
# TYPE linear_jira_sync_issues_total gauge
linear_jira_sync_issues_total $ISSUE_COUNT

# HELP linear_jira_sync_mappings_total Total number of issue mappings
# TYPE linear_jira_sync_mappings_total gauge
linear_jira_sync_mappings_total $MAPPING_COUNT
EOF
```

### 5. Alerting Examples

**Slack notification** (using webhook):
```bash
#!/bin/bash
WEBHOOK_URL="https://hooks.slack.com/services/YOUR/WEBHOOK/URL"

send_slack_alert() {
    local message="$1"
    curl -X POST "$WEBHOOK_URL" \
        -H 'Content-Type: application/json' \
        -d "{\"text\":\"Linear-Jira Sync Alert: $message\"}"
}

# Use in health check or cron
if ! /opt/linear-jira-sync/healthcheck.sh; then
    send_slack_alert "Sync health check failed!"
fi
```

**Email notification:**
```bash
# After sync failure in cron
*/30 * * * * cd /opt/linear-jira-sync && java -jar target/quarkus-app/quarkus-run.jar sync --quiet || echo "Sync failed at $(date)" | mail -s "Linear-Jira Sync Failed" admin@example.com
```

## Backup and Recovery

### Automated State Backup

**Backup script** (`/opt/linear-jira-sync/backup.sh`):
```bash
#!/bin/bash
set -e

SYNC_DIR="/opt/linear-jira-sync"
BACKUP_DIR="$SYNC_DIR/backups"
STATE_FILE="$SYNC_DIR/.syncstate.json"
RETENTION_DAYS=30

# Create backup directory
mkdir -p "$BACKUP_DIR"

# Backup state file
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
cp "$STATE_FILE" "$BACKUP_DIR/.syncstate.$TIMESTAMP.json"

# Keep only recent backups
find "$BACKUP_DIR" -name ".syncstate.*.json" -mtime +$RETENTION_DAYS -delete

echo "Backup created: .syncstate.$TIMESTAMP.json"
```

**Add to cron (before sync):**
```bash
# Backup before every sync
*/30 * * * * /opt/linear-jira-sync/backup.sh && cd /opt/linear-jira-sync && java -jar target/quarkus-app/quarkus-run.jar sync --quiet
```

### Recovery Procedure

**1. Restore from backup:**
```bash
# List available backups
ls -lth /opt/linear-jira-sync/backups/

# Restore specific backup
cp /opt/linear-jira-sync/backups/.syncstate.20240101-120000.json .syncstate.json

# Verify restored state
jq '.' .syncstate.json
```

**2. Reset and full sync:**
```bash
# Reset state
java -jar target/quarkus-app/quarkus-run.jar reset

# Force full sync
java -jar target/quarkus-app/quarkus-run.jar sync --force-full-sync --verbose
```

## Performance Optimization

### 1. Team-Based Sync Strategy

Split large syncs by team to reduce load and improve reliability:

```bash
# Cron schedule for different teams
0 * * * * java -jar app.jar sync --team FRONTEND --quiet
15 * * * * java -jar app.jar sync --team BACKEND --quiet
30 * * * * java -jar app.jar sync --team DEVOPS --quiet
45 * * * * java -jar app.jar sync --team MOBILE --quiet
```

### 2. Incremental Sync

Always prefer incremental sync over full sync:

```bash
# Good: Incremental (default)
java -jar target/quarkus-app/quarkus-run.jar sync

# Avoid unless necessary: Full sync
java -jar target/quarkus-app/quarkus-run.jar sync --force-full-sync
```

### 3. Memory Settings

For large sync volumes, adjust JVM memory:

```bash
# Default (128MB)
java -jar target/quarkus-app/quarkus-run.jar sync

# High volume (512MB)
java -Xms256m -Xmx512m -jar target/quarkus-app/quarkus-run.jar sync

# Production tuning
java -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -Xms256m -Xmx512m \
  -jar target/quarkus-app/quarkus-run.jar sync
```

### 4. Attachment Optimization

Disable or limit attachments for better performance:

```bash
# Disable attachment sync
export ATTACHMENT_SYNC_ENABLED=false

# Limit attachment size
export ATTACHMENT_MAX_SIZE=5242880  # 5MB

# Reduce timeout for faster failures
export ATTACHMENT_DOWNLOAD_TIMEOUT=15  # 15 seconds
```

### 5. Native Executable

For fastest startup and lowest memory:

```bash
# Build native
./mvnw package -Dnative

# Run native (10-50x faster startup, 50-75% less memory)
./target/linear-jira-sync-1.0.0-SNAPSHOT-runner sync
```

## Security Best Practices

### 1. Credential Management

**Don't:**
- Hard-code credentials in scripts
- Store credentials in git
- Use shared accounts

**Do:**
- Use environment variables
- Use secret management (Vault, AWS Secrets Manager, etc.)
- Create dedicated service accounts
- Rotate credentials regularly

### 2. File Permissions

```bash
# Secure environment file
chmod 600 ~/.linear-jira-sync/config.properties

# Secure state file
chmod 600 .syncstate.json

# Secure backup directory
chmod 700 backups/
```

### 3. Network Security

```bash
# Verify HTTPS connections
export JIRA_API_URL="https://yourcompany.atlassian.net"  # Not HTTP

# Use firewall rules to restrict outbound connections
# Allow only to Linear and Jira APIs
```

### 4. Logging Security

```bash
# Don't log sensitive data
export QUARKUS_REST_CLIENT_LOGGING_SCOPE=request-response  # Avoid in production

# Secure log files
chmod 640 /var/log/linear-jira-sync/sync.log
```

## Production Checklist

- [ ] API credentials configured and tested
- [ ] Jira custom field created and configured
- [ ] Sync schedule configured (cron/systemd/k8s)
- [ ] Log rotation configured
- [ ] Monitoring and alerting set up
- [ ] Backup strategy implemented
- [ ] State file persistence configured
- [ ] Error handling tested
- [ ] Rate limiting considered
- [ ] Security review completed
- [ ] Documentation updated
- [ ] Runbook created for incidents

## Example Production Setup

**Complete production setup script:**

```bash
#!/bin/bash
set -e

# Configuration
INSTALL_DIR="/opt/linear-jira-sync"
SERVICE_USER="syncuser"
LOG_DIR="/var/log/linear-jira-sync"

# Create user
sudo useradd -r -s /bin/false $SERVICE_USER || true

# Create directories
sudo mkdir -p $INSTALL_DIR $LOG_DIR
sudo mkdir -p $INSTALL_DIR/backups

# Copy application
sudo cp -r target/quarkus-app/* $INSTALL_DIR/

# Set ownership
sudo chown -R $SERVICE_USER:$SERVICE_USER $INSTALL_DIR $LOG_DIR

# Create environment file
sudo -u $SERVICE_USER tee $INSTALL_DIR/.env > /dev/null <<EOF
LINEAR_API_TOKEN=$LINEAR_API_TOKEN
JIRA_API_URL=$JIRA_API_URL
JIRA_USERNAME=$JIRA_USERNAME
JIRA_API_TOKEN=$JIRA_API_TOKEN
JIRA_PROJECT_KEY=$JIRA_PROJECT_KEY
JIRA_LINEAR_ID_FIELD=$JIRA_LINEAR_ID_FIELD
EOF

sudo chmod 600 $INSTALL_DIR/.env

# Install systemd service and timer (see above)
sudo cp linear-jira-sync.service /etc/systemd/system/
sudo cp linear-jira-sync.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable linear-jira-sync.timer
sudo systemctl start linear-jira-sync.timer

# Install health check
sudo cp healthcheck.sh $INSTALL_DIR/
sudo chmod +x $INSTALL_DIR/healthcheck.sh

# Install backup script
sudo cp backup.sh $INSTALL_DIR/
sudo chmod +x $INSTALL_DIR/backup.sh

# Configure log rotation
sudo tee /etc/logrotate.d/linear-jira-sync > /dev/null <<EOF
$LOG_DIR/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0640 $SERVICE_USER $SERVICE_USER
}
EOF

echo "Production setup complete!"
echo "Check status: sudo systemctl status linear-jira-sync.timer"
```
