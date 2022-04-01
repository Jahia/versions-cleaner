# Versions cleaner
Jahia module to clean versions by: 
 - keeping the last N versions
 - removing the orphaned versions
 
* [How to use it](#how-to-use)
    * [utils:graph-missing-dependencies](#utils-graph-missing-dependencies)

## <a name="how-to-use"></a>How to use?

### Basic usage
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

