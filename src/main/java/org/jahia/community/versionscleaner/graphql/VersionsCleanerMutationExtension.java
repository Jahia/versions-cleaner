package org.jahia.community.versionscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.community.versionscleaner.CleanCommand;
import org.jahia.community.versionscleaner.CleanerContext;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Dictionary;
import java.util.Hashtable;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLName("VersionsCleanerMutations")
@GraphQLDescription("Versions Cleaner mutations")
public class VersionsCleanerMutationExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionsCleanerMutationExtension.class);
    private static final String CONFIG_PID = "org.jahia.community.versionscleaner";

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

    @GraphQLField
    @GraphQLName("versionsCleanerSetStartupDelay")
    @GraphQLDescription("Sets a one-shot startup delay (ms) applied before the next async run. Pass 0 to clear. Intended for automated tests only.")
    @GraphQLRequiresPermission("admin")
    public static Boolean setStartupDelay(
            @GraphQLName("delayMs")
            @GraphQLDescription("Delay in milliseconds (0 = clear)")
            Long delayMs) {
        CleanCommand.setStartupDelay(delayMs != null ? delayMs : 0L);
        return Boolean.TRUE;
    }

    @GraphQLField
    @GraphQLName("versionsCleanerSaveConfig")
    @GraphQLDescription("Saves the versions cleaner scheduled job configuration. A module restart is required for schedule changes to take effect.")
    @GraphQLRequiresPermission("admin")
    public static Boolean saveConfig(
            @GraphQLName("disabled")
            @GraphQLDescription("Whether the scheduled cleanup job is disabled")
            Boolean disabled,

            @GraphQLName("cronExpression")
            @GraphQLDescription("Quartz cron expression for the scheduled cleanup")
            String cronExpression,

            @GraphQLName("nbVersionsToKeep")
            @GraphQLDescription("Number of versions to retain per non-orphaned history (-1 = skip)")
            Long nbVersionsToKeep,

            @GraphQLName("deleteOrphanedVersions")
            @GraphQLDescription("Whether to delete orphaned version histories")
            Boolean deleteOrphanedVersions,

            @GraphQLName("checkIntegrity")
            @GraphQLDescription("Whether to check and fix JCR reference integrity")
            Boolean checkIntegrity,

            @GraphQLName("reindexDefaultWorkspace")
            @GraphQLDescription("Whether to reindex the default workspace before cleaning")
            Boolean reindexDefaultWorkspace,

            @GraphQLName("maxExecutionTimeInMs")
            @GraphQLDescription("Maximum execution time in milliseconds (0 = unlimited)")
            Long maxExecutionTimeInMs) {

        try {
            final ConfigurationAdmin configAdmin = BundleUtils.getOsgiService(ConfigurationAdmin.class, null);
            if (configAdmin == null) {
                return Boolean.FALSE;
            }
            final Configuration config = configAdmin.getConfiguration(CONFIG_PID, null);
            Dictionary<String, Object> props = config.getProperties();
            if (props == null) {
                props = new Hashtable<>();
            }
            if (disabled != null) {
                props.put("disabled", String.valueOf(disabled));
            }
            if (cronExpression != null && !cronExpression.isEmpty()) {
                props.put("cronExpression", cronExpression);
            }
            if (nbVersionsToKeep != null) {
                props.put("nbVersionsToKeep", String.valueOf(nbVersionsToKeep));
            }
            if (deleteOrphanedVersions != null) {
                props.put("deleteOrphanedVersions", String.valueOf(deleteOrphanedVersions));
            }
            if (checkIntegrity != null) {
                props.put("checkIntegrity", String.valueOf(checkIntegrity));
            }
            if (reindexDefaultWorkspace != null) {
                props.put("reindexDefaultWorkspace", String.valueOf(reindexDefaultWorkspace));
            }
            if (maxExecutionTimeInMs != null) {
                props.put("maxExecutionTimeInMs", String.valueOf(maxExecutionTimeInMs));
            }
            config.update(props);
            return Boolean.TRUE;
        } catch (Exception e) {
            LOGGER.error("Failed to save versions cleaner configuration", e);
            return Boolean.FALSE;
        }
    }
}
