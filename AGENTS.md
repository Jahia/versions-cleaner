# versions-cleaner

Jahia OSGi module that scans the JCR version storage tree to delete old versions (keeping last N) and orphaned version histories. Admin UI at `/jahia/administration/versionsCleaner`.

## Key Facts

- **artifactId**: `versions-cleaner` | **version**: `2.3.4-SNAPSHOT` (authoritative source is `pom.xml`; keep `package.json` in sync via the `sync-pom` npm script — they have drifted before)
- **Java package**: `org.jahia.community.versionscleaner`
- **jahia-depends**: `default,graphql-dxm-provider`
- No Blueprint/Spring — pure OSGi DS
- Karaf command: `versions-cleaner:run`
- SonarQube quality gate: PASSED (0 open issues)

## Architecture

| Class | Role |
|-------|------|
| `CleanCommand` | Core scan + deletion logic (Karaf `@Command`); static `isRunning()` for GraphQL status |
| `CleanerContext` | Holds all configuration and session state for a single execution |
| `CleanBackgroundJob` | Quartz `BackgroundJob` subclass; reads params from `JobDataMap`, delegates to `CleanCommand.execute()` |
| `VersionsCleanerConfig` | `ManagedService` reading `org.jahia.community.versionscleaner.cfg`; holds scheduler + job defaults |
| `VersionsCleanerScheduler` | `@Component(immediate=true)`; schedules `CleanBackgroundJob` with `CronTrigger` on `@Activate` |
| `graphql/VersionsCleanerGraphQLExtensionsProvider` | Registers GraphQL extensions |
| `graphql/VersionsCleanerQueryExtension` | Query fields: `versionsCleanerIsRunning`, `versionsCleanerConfig`, and test helpers |
| `graphql/VersionsCleanerMutationExtension` | Mutation fields: `versionsCleanerRun(...)`, `versionsCleanerSaveConfig(...)`, and test helpers |

### Key private helpers in CleanCommand

| Method | Purpose |
|--------|---------|
| `isUuidOrphaned(uuid, context)` | Checks if a frozen node UUID is absent from both edit and live workspaces |
| `processProperty(session, node, property, ...)` | Dispatches to `processMultipleValueProperty` or `processPropertyValue` |
| `performReindex()` | Schedules and polls default-workspace reindex |
| `removeOneVersion(vh, versionName, ...)` | Removes a single version; returns whether deletion succeeded |
| `pauseBetweenDeletions(context, stopWatch)` | Applies configured pause between deletions |

## OSGi Config

PID: `org.jahia.community.versionscleaner`  
Shipped default: `src/main/resources/META-INF/configurations/org.jahia.community.versionscleaner.cfg`
(line 1 MUST be `# default configuration - won't be overridden`). Runtime file: `<karaf.home>/etc/org.jahia.community.versionscleaner.cfg`.

Key properties: `disabled` (true), `cronExpression` (0 30 1 * * ?), `nbVersionsToKeep` (2), `deleteOrphanedVersions` (false), `maxExecutionTimeInMs` (60000).

Config changes require module restart to reschedule the cron job.

## GraphQL API

All operations are gated by the fine-grained `versionsCleanerAdmin` permission (shipped via the
`versions-cleaner-administrator` role in `roles.xml`, defined in `permissions.xml`), resolved per field
on the `/` node — NOT a generic "admin" flag. The Karaf `versions-cleaner:run` command and the
`versions-cleaner.interrupt` system property deliberately bypass this permission (they assume shell/JVM
access, a higher privilege); `CleanCommandKarafGateTest` is a tripwire on that asymmetry.

**The schema is namespaced**: operations live under a single `versionsCleaner` container on `Query`/`Mutation`
(e.g. `mutation { versionsCleaner { run(...) } }`), read as `data.versionsCleaner.<op>`. They are NOT flat
root fields — the `versionsCleanerRun` / `versionsCleanerIsRunning` names used below are the GraphQLName of
the nested fields (`run`, `isRunning`, …) within that container, not top-level fields.

### Queries

| Name | Returns | Notes |
|------|---------|-------|
| `versionsCleanerIsRunning` | `Boolean` | Reads static `AtomicBoolean` in `CleanCommand` |
| `versionsCleanerConfig` | `VersionsCleanerConfig` | Returns current scheduled job configuration |
| `versionsCleanerVersionCount(nodePath)` | `Long` | Test helper: non-root version count for a node path; -1 on error |
| `versionsCleanerHistoryExists(historyId)` | `Boolean` | Test helper: whether a version history UUID still exists |

### Mutations

| Name | Returns | Notes |
|------|---------|-------|
| `versionsCleanerRun(...)` | `Boolean` | Starts async clean; returns `false` if already running |
| `versionsCleanerSaveConfig(...)` | `Boolean` | Persists OSGi config via `ConfigurationAdmin`; restart required for schedule changes |
| `versionsCleanerCreateTestVersions(name, versionCount)` | `String` | Test helper: creates versionable node and checks it in N times; returns history UUID |
| `versionsCleanerDeleteTestNode(name)` | `Boolean` | Test helper: removes the test node |
| `versionsCleanerSetStartupDelay(delayMs)` | `Boolean` | Test helper: one-shot startup delay before next async run |

`versionsCleanerRun` parameters: `nbVersionsToKeep`, `deleteOrphanedVersions`, `checkIntegrity`, `reindexDefaultWorkspace`, `maxExecutionTimeInMs`, `pauseDuration`, `subtreePath`, `forceRestartFromBeginning`.

`versionsCleanerSaveConfig` parameters mirror the OSGi config properties (all optional).

## Cypress Tests

| File | Scope |
|------|-------|
| `01-versionsCleanerAPI.cy.ts` | GraphQL API shape and auth checks |
| `02-versionsCleanerExecution.cy.ts` | Async run lifecycle (start, polling, completion) |
| `03-versionsCleanerConfiguration.cy.ts` | Save/read config via `versionsCleanerSaveConfig` / `versionsCleanerConfig` |
| `04-versionsCleanerCorrectness.cy.ts` | Semantic correctness: verifies actual version counts and orphan deletion |

Test helpers (`createTestVersions`, `deleteTestNode`, `versionsCleanerVersionCount`, `versionsCleanerHistoryExists`) are used exclusively by these tests.

## Build

```bash
mvn clean install
yarn build
yarn lint
```

- Admin route target: `administration-server-systemComponents:999`
- CSS prefix: `vc_`
- Route key: `versionsCleaner`
- i18n namespace: `versions-cleaner`

## Gotchas

- `CleanCommand.isRunning()` uses a static `AtomicBoolean RUNNING` set true at the start of `execute()`. The GraphQL mutation checks this and returns `false` immediately if a run is in progress.
- The Quartz scheduler group name is derived from the job class name by `BackgroundJob.getGroupName()`: group = `CleanBackgroundJob`. When `@Deactivate` runs, it deletes the job by the stored `jobDetail.getName()` + group.
- Config changes (`.cfg` file) do NOT automatically reschedule the cron job. Module restart is required.
- The interrupt mechanism uses a JVM system property `versions-cleaner.interrupt`. This must be set on the same JVM where the purge is running.
- CSS Modules: match in tests with `[class*="vc_..."]`
- `isUuidOrphaned` uses two nested try-catch blocks (edit then live workspace). Cannot use multi-catch `ItemNotFoundException | RepositoryException` because `ItemNotFoundException extends RepositoryException`.
