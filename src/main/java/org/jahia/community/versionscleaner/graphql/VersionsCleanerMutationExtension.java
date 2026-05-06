package org.jahia.community.versionscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.community.versionscleaner.CleanCommand;
import org.jahia.community.versionscleaner.CleanerContext;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLName("VersionsCleanerMutations")
@GraphQLDescription("Versions Cleaner mutations")
public class VersionsCleanerMutationExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionsCleanerMutationExtension.class);

    private VersionsCleanerMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("versionsCleanerRun")
    @GraphQLDescription("Triggers a versions clean operation asynchronously. Returns false if a clean is already running.")
    @GraphQLRequiresPermission("admin")
    public static Boolean run(
            @GraphQLName("nbVersionsToKeep")
            @GraphQLDescription("Number of versions to keep per history. Negative = skip non-orphan histories.")
            Long nbVersionsToKeep,

            @GraphQLName("deleteOrphanedVersions")
            @GraphQLDescription("Delete orphaned version histories")
            Boolean deleteOrphanedVersions,

            @GraphQLName("checkIntegrity")
            @GraphQLDescription("Check and fix integrity of version references")
            Boolean checkIntegrity,

            @GraphQLName("reindexDefaultWorkspace")
            @GraphQLDescription("Reindex default workspace before cleaning")
            Boolean reindexDefaultWorkspace,

            @GraphQLName("maxExecutionTimeInMs")
            @GraphQLDescription("Maximum execution time in milliseconds (0 = unlimited)")
            Long maxExecutionTimeInMs,

            @GraphQLName("pauseDuration")
            @GraphQLDescription("Pause duration in milliseconds between version deletions (0 = no pause)")
            Long pauseDuration,

            @GraphQLName("subtreePath")
            @GraphQLDescription("Restrict scan to a subtree of the version storage (relative path)")
            String subtreePath,

            @GraphQLName("forceRestartFromBeginning")
            @GraphQLDescription("Ignore the saved position and restart from the beginning of the tree")
            Boolean forceRestartFromBeginning) {

        if (CleanCommand.isRunning()) {
            LOGGER.info("Versions cleaner run requested but already running");
            return Boolean.FALSE;
        }

        final CleanerContext context = new CleanerContext()
                .setRunAsynchronously(Boolean.TRUE)
                .setNbVersionsToKeep(nbVersionsToKeep != null ? nbVersionsToKeep : -1L)
                .setDeleteOrphanedVersions(deleteOrphanedVersions != null ? deleteOrphanedVersions : Boolean.FALSE)
                .setCheckIntegrity(checkIntegrity != null ? checkIntegrity : Boolean.FALSE)
                .setReindexDefaultWorkspace(reindexDefaultWorkspace != null ? reindexDefaultWorkspace : Boolean.FALSE)
                .setMaxExecutionTimeInMs(maxExecutionTimeInMs != null ? maxExecutionTimeInMs : 0L)
                .setPauseDuration(pauseDuration != null ? pauseDuration : 0L)
                .setSubtreePath(subtreePath)
                .setRestartFromLastPosition(forceRestartFromBeginning == null || !forceRestartFromBeginning);

        try {
            CleanCommand.execute(context);
            return Boolean.TRUE;
        } catch (RepositoryException e) {
            LOGGER.error("Failed to start versions cleaner", e);
            return Boolean.FALSE;
        }
    }
}
