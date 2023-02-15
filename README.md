# Versions cleaner
Jahia module to clean versions by: 
 - keeping the last N versions
 - removing the orphaned versions
 
* [How to use it](#how-to-use)
    * [cleaners-version:keep-n](#cleaners-version-keep-n)
    * [How to interrupt an execution?](#how-to-interrupt)

## <a name="how-to-use"></a>How to use?

### Background job

**Properties:**
Name | Value | Description
 --- | --- | ---
jahia.versions.cleaner.job.reindexDefaultWorkspace | false | Reindex default workspace before cleaning
jahia.versions.cleaner.job.checkIntegrity | false | Check integrity of the versions
jahia.versions.cleaner.job.nbVersionsToKeep | 2 | Number of versions to keep
jahia.versions.cleaner.job.maxExecutionTimeInMs | 0 | Max execution time in ms
jahia.versions.cleaner.job.deleteOrphanedVersions | false | Delete orphaned versions
jahia.versions.cleaner.job.cronExpression | 0 30 1 * * ? | Crontab expression for the job

### Commands
#### <a name="cleaners-version:keep-n"></a>cleaners-version:keep-n
Delete all versions except the last N ones

**Options:**

Name | alias | Mandatory | Value | Description
 --- | --- | :---: | :---: | ---
 -r | --reindex-default-workspace | | false | Reindex default workspace before cleaning
 -c | --check-integrity | | false | Check integrity of the versions
 -n | --nb-versions-to-keep | | 2 | Number of versions to keep
 -t | --max-execution-time-in-ms | | 0 | Max execution time in ms
 -o | --delete-orphaned-versions | | false | Delete orphaned versions


**Example:**

    cleaners-version:keep-n 

### <a name="how-to-interrupt"></a>How to interrupt an execution?
                                                           
Run the below code in a Groovy console.
In case of a Jahia Cluster, this has to be executed on the JVM where the purge is running. If you have triggered it with the Karaf command, this means the same server. If you use the scheduled background execution, this means the procesing server.

```
System.setProperty("versions-cleaner.interrupt", "true")
```

