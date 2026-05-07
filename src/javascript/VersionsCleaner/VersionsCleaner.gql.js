import {gql} from '@apollo/client';

export const IS_RUNNING = gql`
    query VersionsCleanerIsRunning {
        versionsCleanerIsRunning
    }
`;

export const RUN_CLEANER = gql`
    mutation VersionsCleanerRun(
        $nbVersionsToKeep: Long
        $deleteOrphanedVersions: Boolean
        $checkIntegrity: Boolean
        $reindexDefaultWorkspace: Boolean
        $maxExecutionTimeInMs: Long
        $pauseDuration: Long
        $subtreePath: String
        $forceRestartFromBeginning: Boolean
    ) {
        versionsCleanerRun(
            nbVersionsToKeep: $nbVersionsToKeep
            deleteOrphanedVersions: $deleteOrphanedVersions
            checkIntegrity: $checkIntegrity
            reindexDefaultWorkspace: $reindexDefaultWorkspace
            maxExecutionTimeInMs: $maxExecutionTimeInMs
            pauseDuration: $pauseDuration
            subtreePath: $subtreePath
            forceRestartFromBeginning: $forceRestartFromBeginning
        )
    }
`;

export const GET_CONFIG = gql`
    query VersionsCleanerConfig {
        versionsCleanerConfig {
            disabled
            cronExpression
            nbVersionsToKeep
            deleteOrphanedVersions
            checkIntegrity
            reindexDefaultWorkspace
            maxExecutionTimeInMs
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
        $maxExecutionTimeInMs: Long
    ) {
        versionsCleanerSaveConfig(
            disabled: $disabled
            cronExpression: $cronExpression
            nbVersionsToKeep: $nbVersionsToKeep
            deleteOrphanedVersions: $deleteOrphanedVersions
            checkIntegrity: $checkIntegrity
            reindexDefaultWorkspace: $reindexDefaultWorkspace
            maxExecutionTimeInMs: $maxExecutionTimeInMs
        )
    }
`;
