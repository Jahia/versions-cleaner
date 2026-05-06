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
