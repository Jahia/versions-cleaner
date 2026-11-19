import {gql} from '@apollo/client';

export const IS_RUNNING = gql`
    query VersionsCleanerIsRunning {
        versionsCleaner {
            isRunning
        }
    }
`;

export const RUN_CLEANER = gql`
    mutation VersionsCleanerRun(
        $nbVersionsToKeep: Long
        $deleteOrphanedVersions: Boolean
        $checkIntegrity: Boolean
        $reindexDefaultWorkspace: Boolean
        $forceConsistencyCheck: Boolean
        $maxExecutionTimeInMs: Long
        $pauseDuration: Long
        $subtreePath: String
        $forceRestartFromBeginning: Boolean
    ) {
        versionsCleaner {
            run(
                nbVersionsToKeep: $nbVersionsToKeep
                deleteOrphanedVersions: $deleteOrphanedVersions
                checkIntegrity: $checkIntegrity
                reindexDefaultWorkspace: $reindexDefaultWorkspace
                forceConsistencyCheck: $forceConsistencyCheck
                maxExecutionTimeInMs: $maxExecutionTimeInMs
                pauseDuration: $pauseDuration
                subtreePath: $subtreePath
                forceRestartFromBeginning: $forceRestartFromBeginning
            )
        }
    }
`;

export const GET_CONFIG = gql`
    query VersionsCleanerConfig {
        versionsCleaner {
            config {
                disabled
                cronExpression
                nbVersionsToKeep
                deleteOrphanedVersions
                checkIntegrity
                reindexDefaultWorkspace
                forceConsistencyCheck
                maxExecutionTimeInMs
            }
        }
    }
`;

export const SAVE_CONFIG = gql`
    mutation VersionsCleanerSaveConfig(
        $disabled: Boolean
        $cronExpression: String
        $nbVersionsToKeep: Long
        $deleteOrphanedVersions: Boolean
        $checkIntegrity: Boolean
        $reindexDefaultWorkspace: Boolean
        $forceConsistencyCheck: Boolean
        $maxExecutionTimeInMs: Long
    ) {
        versionsCleaner {
            saveConfig(
                disabled: $disabled
                cronExpression: $cronExpression
                nbVersionsToKeep: $nbVersionsToKeep
                deleteOrphanedVersions: $deleteOrphanedVersions
                checkIntegrity: $checkIntegrity
                reindexDefaultWorkspace: $reindexDefaultWorkspace
                forceConsistencyCheck: $forceConsistencyCheck
                maxExecutionTimeInMs: $maxExecutionTimeInMs
            )
        }
    }
`;
