# Versions cleaner

Jahia module to clean versions by:

- keeping the last N versions
- removing the orphaned versions

Admin UI available at `/jahia/administration/versionsCleaner`.

* [Configuration (scheduled job)](#configuration-scheduled-job)
* [GraphQL API](#graphql-api)
* [Admin UI](#admin-ui)
* [Karaf commands](#commands)
* [How to interrupt an execution?](#how-to-interrupt-an-execution)

## Configuration (scheduled job)

The scheduled job is configured via an OSGi configuration file.  
Create or edit `digital-factory-config/jahia/org.jahia.community.versionscleaner.cfg`:

| Property                    | Default value | Description                               |
|-----------------------------|---------------|-------------------------------------------|
| `disabled`                  | `true`        | Disable the scheduled purge               |
| `cronExpression`            | `0 30 1 * * ?`| Quartz cron expression for the job        |
| `reindexDefaultWorkspace`   | `false`       | Reindex default workspace before cleaning |
| `checkIntegrity`            | `false`       | Check and fix integrity of references     |
| `nbVersionsToKeep`          | `2`           | Number of versions to keep                |
| `maxExecutionTimeInMs`      | `60000`       | Max execution time in ms (0 = Infinite)   |
| `deleteOrphanedVersions`    | `false`       | Delete orphaned versions                  |

**Example `.cfg` to enable the scheduled job:**

```properties
disabled=false
cronExpression=0 30 1 * * ?
nbVersionsToKeep=5
deleteOrphanedVersions=true
maxExecutionTimeInMs=600000
```

> **Note:** Config changes require a module restart to reschedule the job.

## GraphQL API

All operations require `admin` permission.

### Queries

| Name | Returns | Description |
|------|---------|-------------|
| `versionsCleanerIsRunning` | `Boolean` | True if a clean is currently running |
| `versionsCleanerConfig` | `VersionsCleanerConfig` | Returns the current scheduled job configuration |

### Mutations

| Name | Returns | Description |
|------|---------|-------------|
| `versionsCleanerRun(...)` | `Boolean` | Starts a clean asynchronously; returns `false` if already running |
| `versionsCleanerSaveConfig(...)` | `Boolean` | Persists the scheduled job configuration; module restart required for schedule changes |

#### versionsCleanerRun parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `nbVersionsToKeep` | `Long` | `-1` | Versions to keep per non-orphan history. Negative = skip non-orphans. |
| `deleteOrphanedVersions` | `Boolean` | `false` | Delete orphaned version histories |
| `checkIntegrity` | `Boolean` | `false` | Check and fix reference integrity |
| `reindexDefaultWorkspace` | `Boolean` | `false` | Reindex default workspace first |
| `maxExecutionTimeInMs` | `Long` | `0` | Max execution time in ms (0 = unlimited) |
| `pauseDuration` | `Long` | `0` | Pause in ms between deletions |
| `subtreePath` | `String` | `null` | Restrict scan to a subtree of the version storage |
| `forceRestartFromBeginning` | `Boolean` | `false` | Ignore saved position; restart from the beginning |

**Example:**

```graphql
mutation {
  versionsCleanerRun(
    nbVersionsToKeep: 2
    deleteOrphanedVersions: true
    maxExecutionTimeInMs: 600000
  )
}
```

#### versionsCleanerSaveConfig parameters

All parameters are optional; only the provided values are updated.

| Parameter | Type | Description |
|-----------|------|-------------|
| `disabled` | `Boolean` | Whether the scheduled cleanup job is disabled |
| `cronExpression` | `String` | Quartz cron expression for the scheduled cleanup |
| `nbVersionsToKeep` | `Long` | Number of versions to retain per non-orphaned history (-1 = skip) |
| `deleteOrphanedVersions` | `Boolean` | Whether to delete orphaned version histories |
| `checkIntegrity` | `Boolean` | Whether to check and fix JCR reference integrity |
| `reindexDefaultWorkspace` | `Boolean` | Whether to reindex the default workspace before cleaning |
| `maxExecutionTimeInMs` | `Long` | Maximum execution time in milliseconds (0 = unlimited) |

**Example:**

```graphql
mutation {
  versionsCleanerSaveConfig(
    disabled: false
    cronExpression: "0 30 1 * * ?"
    nbVersionsToKeep: 5
    deleteOrphanedVersions: true
    maxExecutionTimeInMs: 600000
  )
}
```

## Admin UI

The admin UI is accessible at `/jahia/administration/versionsCleaner` (under *Server / System Components*).

It provides a form to configure and launch a one-off clean operation immediately from the browser. The UI polls the server while the operation is running and shows a progress indicator.

## Commands

### versions-cleaner:run

Run a scan of the versions tree and perform the configured actions.

**Options:**

| Name | Alias | Default | Description |
|------|-------|---------|-------------|
| `-r` | `--reindex-default-workspace` | `false` | Reindex the default workspace before cleaning |
| `-c` | `--check-integrity` | `false` | Check the integrity of the versions |
| `-n` | `--nb-versions-to-keep` | `-1` | Number of versions to keep on non-orphaned histories |
| `-t` | `--max-execution-time-in-ms` | `0` | Max execution time in ms (0 = Infinite) |
| `-o` | `--delete-orphaned-versions` | `false` | Delete orphaned versions |
| `-p` | `--subtree-path` | | Subtree of the versions tree to scan |
| `-pause` | | `0` | Pause duration in ms between deletions |
| `-skip` | `--skip-subtree` | | Paths to skip (can be specified multiple times) |
| `-threshold-long-history-purge-strategy` | | `1000` | Version count threshold for one-by-one deletion |
| `-force-restart-from-the-beginning` | | `false` | Restart from the beginning, ignoring the saved position |

**Examples:**

```bash
# Reduce non-orphan histories, keeping max 2 versions:
versions-cleaner:run -n 2

# Delete all orphan histories:
versions-cleaner:run -o

# Combined cleanup with 10-minute time limit:
versions-cleaner:run -n 2 -o -t 600000
```

## How to interrupt an execution?

Use the predefined script for the [Extended Groovy Console](https://store.jahia.com/contents/modules-repository/org/jahia/community/modules/extended-groovy-console.html) at `META-INF/extendedGroovyConsole/stopVersionPurgeProcess.groovy`.

Or run in any Groovy console (on the same JVM as the running purge):

```groovy
System.setProperty("versions-cleaner.interrupt", "true")
```
