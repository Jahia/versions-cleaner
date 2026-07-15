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

The scheduled job is configured via an OSGi configuration file (PID `org.jahia.community.versionscleaner`).
The module ships a default at `src/main/resources/META-INF/configurations/org.jahia.community.versionscleaner.cfg`
(whose first line is the mandatory `# default configuration - won't be overridden` marker, so Jahia's module
extender does not overwrite operator edits on redeploy). At runtime the effective file lives at
`<karaf.home>/etc/org.jahia.community.versionscleaner.cfg`; edit it there (or deploy your own copy) to override:

| Property                    | Default value | Description                               |
|-----------------------------|---------------|-------------------------------------------|
| `disabled`                  | `true`        | Disable the scheduled purge               |
| `cronExpression`            | `0 30 1 * * ?`| Quartz cron expression for the job        |
| `reindexDefaultWorkspace`   | `false`       | Reindex default workspace before cleaning |
| `checkIntegrity`            | `false`       | Report reference-integrity problems (report-only; fixing is opt-in, see below) |
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

Every operation is gated by the fine-grained `versionsCleanerAdmin` permission (shipped via the
`versions-cleaner-administrator` role), resolved per field on the `/` node — not a generic "admin" flag.

The operations are **namespaced**: they live under a single `versionsCleaner` container on `Query`/`Mutation`,
so you query them as `versionsCleaner { <op> }` and read the result at `data.versionsCleaner.<op>` (they are
NOT flat root fields such as `versionsCleanerRun`).

### Queries — `query { versionsCleaner { ... } }`

| Field | Returns | Description |
|-------|---------|-------------|
| `isRunning` | `Boolean` | True if a clean is currently running |
| `config` | `VersionsCleanerConfig` | Returns the current scheduled job configuration |

### Mutations — `mutation { versionsCleaner { ... } }`

| Field | Returns | Description |
|-------|---------|-------------|
| `run(...)` | `Boolean` | Starts a clean asynchronously; returns `false` if already running |
| `saveConfig(...)` | `Boolean` | Persists the scheduled job configuration; module restart required for schedule changes |

#### versionsCleanerRun parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `nbVersionsToKeep` | `Long` | `-1` | Versions to keep per non-orphan history. Negative = skip non-orphans. |
| `deleteOrphanedVersions` | `Boolean` | `false` | Delete orphaned version histories. A history whose versions are still referenced elsewhere in version storage is NOT force-purged. |
| `checkIntegrity` | `Boolean` | `false` | Report reference-integrity problems (report-only unless `checkIntegrityFix` is also set) |
| `checkIntegrityFix` | `Boolean` | `false` | When `checkIntegrity` is true, actually FIX problems (null dangling references / remove offending nodes). Opt-in; without it the check only reports. |
| `reindexDefaultWorkspace` | `Boolean` | `false` | Reindex default workspace first |
| `maxExecutionTimeInMs` | `Long` | `0` | Max execution time in ms (0 = unlimited) |
| `pauseDuration` | `Long` | `0` | Pause in ms between deletions |
| `subtreePath` | `String` | `null` | Restrict scan to a subtree of the version storage |
| `forceRestartFromBeginning` | `Boolean` | `false` | Ignore saved position; restart from the beginning |

**Example:**

```graphql
mutation {
  versionsCleaner {
    run(
      nbVersionsToKeep: 2
      deleteOrphanedVersions: true
      maxExecutionTimeInMs: 600000
    )
  }
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
| `checkIntegrity` | `Boolean` | Whether to report JCR reference-integrity problems (report-only; fixing is opt-in per run) |
| `reindexDefaultWorkspace` | `Boolean` | Whether to reindex the default workspace before cleaning |
| `maxExecutionTimeInMs` | `Long` | Maximum execution time in milliseconds (0 = unlimited) |

**Example:**

```graphql
mutation {
  versionsCleaner {
    saveConfig(
      disabled: false
      cronExpression: "0 30 1 * * ?"
      nbVersionsToKeep: 5
      deleteOrphanedVersions: true
      maxExecutionTimeInMs: 600000
    )
  }
}
```

## Admin UI

The admin UI registers **two** routes under *Server / System Health* (`administration-server-systemHealth`):

- `versionsCleanerExecution` — configure and launch a one-off clean immediately from the browser; the UI
  polls the server while the operation runs and shows a progress indicator.
- `versionsCleanerConfiguration` — edit and persist the scheduled-job configuration.

## Commands

### versions-cleaner:run

Run a scan of the versions tree and perform the configured actions.

**Options:**

| Name | Alias | Default | Description |
|------|-------|---------|-------------|
| `-r` | `--reindex-default-workspace` | `false` | Reindex the default workspace before cleaning |
| `-c` | `--check-integrity` | `false` | Report integrity problems of the versions (report-only unless `-fix` is also set) |
| `-fix` | `--fix-integrity` | `false` | When checking integrity, actually FIX problems (null dangling references / remove offending nodes) instead of only reporting |
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

> **Privilege note (Karaf vs GraphQL/UI):** the GraphQL and Admin-UI surfaces are gated by the
> `versionsCleanerAdmin` permission. The `versions-cleaner:run` Karaf command and the
> `versions-cleaner.interrupt` system-property interrupt are **not** gated by that permission — they rely
> on the operator already holding shell/JVM-level access to the Jahia host, which is a strictly higher
> privilege. This asymmetry is intentional; a tripwire unit test (`CleanCommandKarafGateTest`) fails if a
> future change silently adds or removes that boundary.

## How to interrupt an execution?

Use the predefined script for the [Extended Groovy Console](https://store.jahia.com/contents/modules-repository/org/jahia/community/modules/extended-groovy-console.html) at `META-INF/extendedGroovyConsole/stopVersionPurgeProcess.groovy`.

Or run in any Groovy console (on the same JVM as the running purge):

```groovy
System.setProperty("versions-cleaner.interrupt", "true")
```
