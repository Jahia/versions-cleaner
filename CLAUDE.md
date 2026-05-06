# versions-cleaner

Jahia OSGi module that scans the JCR version storage tree to delete old versions (keeping last N) and orphaned version histories. Admin UI at `/jahia/administration/versionsCleaner`.

## Key Facts

- **artifactId**: `versions-cleaner` | **version**: `2.3.1-SNAPSHOT`
- **Java package**: `org.jahia.community.versionscleaner`
- **jahia-depends**: `default,graphql-dxm-provider`
- No Blueprint/Spring — pure OSGi DS
- Karaf command: `versions-cleaner:run`

## Architecture

| Class | Role |
|-------|------|
| `CleanCommand` | Core scan + deletion logic (Karaf `@Command`); static `isRunning()` for GraphQL status |
| `CleanerContext` | Holds all configuration and session state for a single execution |
| `CleanBackgroundJob` | Quartz `BackgroundJob` subclass; reads params from `JobDataMap`, delegates to `CleanCommand.execute()` |
| `VersionsCleanerConfig` | `ManagedService` reading `org.jahia.community.versionscleaner.cfg`; holds scheduler + job defaults |
| `VersionsCleanerScheduler` | `@Component(immediate=true)`; schedules `CleanBackgroundJob` with `CronTrigger` on `@Activate` |
| `graphql/VersionsCleanerGraphQLExtensionsProvider` | Registers GraphQL extensions |
| `graphql/VersionsCleanerQueryExtension` | `versionsCleanerIsRunning` query |
| `graphql/VersionsCleanerMutationExtension` | `versionsCleanerRun(...)` mutation — async, returns false if already running |

## OSGi Config

PID: `org.jahia.community.versionscleaner`  
File: `digital-factory-config/jahia/org.jahia.community.versionscleaner.cfg`

Key properties: `disabled` (true), `cronExpression` (0 30 1 * * ?), `nbVersionsToKeep` (2), `deleteOrphanedVersions` (false), `maxExecutionTimeInMs` (60000).

Config changes require module restart to reschedule the cron job.

## GraphQL API

| Operation | Name | Notes |
|-----------|------|-------|
| Query | `versionsCleanerIsRunning` → Boolean | Reads static `AtomicBoolean` in `CleanCommand` |
| Mutation | `versionsCleanerRun(...)` → Boolean | Params: `nbVersionsToKeep`, `deleteOrphanedVersions`, `checkIntegrity`, `reindexDefaultWorkspace`, `maxExecutionTimeInMs`, `pauseDuration`, `subtreePath`, `forceRestartFromBeginning` |

All require `admin` permission. The mutation runs `CleanCommand.execute()` with `runAsynchronously=true`.

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
