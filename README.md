# Versions cleaner

Jahia module to clean versions by:

- keeping the last N versions
- removing the orphaned versions

* [How to use it](#how-to-use)
    * [versions-cleaner:run](#versions-cleanerrun)
    * [How to interrupt an execution?](#how-to-interrupt-an-execution)

## How to use?

You can run some purge on demand, using a [Karaf command](#commands) or configure some [automated purges](#background-job). 

### Background job

**Properties:**

| Name                                               | Default value | Description                               |
|----------------------------------------------------|---------------|-------------------------------------------|
| jahia.versions.cleaner.job.disabled                | true          | Disable the scheduled purge               |
| jahia.versions.cleaner.job.reindexDefaultWorkspace | false         | Reindex default workspace before cleaning |
| jahia.versions.cleaner.job.checkIntegrity          | false         | Check integrity of the versions           |
| jahia.versions.cleaner.job.nbVersionsToKeep        | 2             | Number of versions to keep                |
| jahia.versions.cleaner.job.maxExecutionTimeInMs    | 60000         | Max execution time in ms (0 = Infinite)   |
| jahia.versions.cleaner.job.deleteOrphanedVersions  | false         | Delete orphaned versions                  |
| jahia.versions.cleaner.job.cronExpression          | 0 30 1 * * ?  | Crontab expression for the job            |

### Commands

#### versions-cleaner:run

Run a scan the versions tree, and perform the configured actions

**Options:**

| Name                                   | alias                       | Multiple | Default value | Description                                                                                                                                                                                                          |
|----------------------------------------|-----------------------------|:--------:|:-------------:|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| -r                                     | --reindex-default-workspace |          |     false     | If specified, reindex the default workspace before cleaning                                                                                                                                                          |
| -c                                     | --check-integrity           |          |     false     | If specified, check the integrity of the versions                                                                                                                                                                    |
| -n                                     | --nb-versions-to-keep       |          |      -1       | Number of versions to keep on the non orphaned histories <br/>A negative value means that the non orphaned histories are ignored                                                                                     |
| -t                                     | --max-execution-time-in-ms  |          |       0       | Max execution time in ms (0 Infinite)                                                                                                                                                                                |
| -o                                     | --delete-orphaned-versions  |          |     false     | If specified, the orphaned versions are deleted. They are ignored otherwise                                                                                                                                          |
| -p                                     | --subtree-path              |          |               | Subtree of the versions tree where to run the scan. If not defined, the whole tree is processed                                                                                                                      |
| -pause                                 |                             |          |       0       | Duration of the pause between 2 version deletions. No pause if less or equal to zero                                                                                                                                 |
| -skip                                  | --skip-subtree              |    x     |               | Path to be skipped by the process <br/>Useful for example if you have identified some version histories which are particularly massive, and you want to iterate over the rest first<br/>Several paths can be defined |
| -threshold-long-history-purge-strategy |                             |          |     1000      | Number of versions over which orphaned histories are purged by deleting the versions one by one, to reduce the memory footprint                                                                                      |
| -force-restart-from-the-beginning      |                             |          |     false     | If specified, the process will restart from the beginning of the tree <br/>Otherwise, it will try to restart from where the previous execution had stopped                                                           |

**Examples:**

Reduce all the non-orphan histories, keeping maximum 2 versions per history:                                

    versions-cleaner:run -n 2

Delete all the orphan histories:

    versions-cleaner:run -o

Reduce all the non-orphan histories, keeping maximum 2 versions per history, and delete all the orphan histories, at the same time:

    versions-cleaner:run -n 2 -o

Same command, but with a time limit. If the process has not reached the end of the tree after 10mn of execution, it will stop gracefully. The next execution will restart from the reached position.

    versions-cleaner:run -n 2 -o -t 600000

### How to interrupt an execution?

The module defines a predefined script (stopVersionPurgeProcess.groovy) for the [Extended Groovy Console](https://store.jahia.com/contents/modules-repository/org/jahia/community/modules/extended-groovy-console.html)

Otherwise, run the below code in a Groovy console.  
In case of a Jahia Cluster, this has to be executed on the JVM where the purge is running. If you have triggered it with
the Karaf command, this means the same server. If you use the scheduled background execution, this means the procesing
server.

```
System.setProperty("versions-cleaner.interrupt", "true")
```

