package org.jahia.community.versionscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.api.Constants;
import org.jahia.community.versionscleaner.CleanCommand;
import org.jahia.community.versionscleaner.VersionsCleanerConfig;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRSessionFactory;

import javax.jcr.RepositoryException;
import javax.jcr.version.VersionHistory;

@GraphQLName("VersionsCleanerQuery")
@GraphQLDescription("Versions Cleaner queries")
public class VersionsCleanerQuery {

    @GraphQLField
    @GraphQLName("isRunning")
    @GraphQLDescription("Returns true if a versions clean operation is currently in progress")
    @GraphQLRequiresPermission("versionsCleanerAdmin")
    public Boolean isRunning() {
        return CleanCommand.isRunning();
    }

    @GraphQLField
    @GraphQLName("versionCount")
    @GraphQLDescription("Test helper: returns the number of non-root versions for the node at the given path. Returns -1 on error.")
    @GraphQLRequiresPermission("versionsCleanerAdmin")
    public Long versionCount(@GraphQLName("nodePath") String nodePath) {
        try {
            final JCRSessionFactory sf = JCRSessionFactory.getInstance();
            final VersionHistory vh = sf.getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
                    .getWorkspace().getVersionManager().getVersionHistory(nodePath);
            return vh.getAllVersions().getSize() - 1L;
        } catch (RepositoryException e) {
            return -1L;
        }
    }

    @GraphQLField
    @GraphQLName("historyExists")
    @GraphQLDescription("Test helper: returns true if a version history node with the given UUID still exists in the repository.")
    @GraphQLRequiresPermission("versionsCleanerAdmin")
    public Boolean historyExists(@GraphQLName("historyId") String historyId) {
        try {
            JCRSessionFactory.getInstance()
                    .getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
                    .getNodeByIdentifier(historyId);
            return Boolean.TRUE;
        } catch (RepositoryException e) {
            return Boolean.FALSE;
        }
    }

    @GraphQLField
    @GraphQLName("config")
    @GraphQLDescription("Returns the current versions cleaner scheduled job configuration")
    @GraphQLRequiresPermission("versionsCleanerAdmin")
    public GqlVersionsCleanerConfig config() {
        final VersionsCleanerConfig config = BundleUtils.getOsgiService(VersionsCleanerConfig.class, null);
        if (config == null) {
            return GqlVersionsCleanerConfig.defaults();
        }
        return new GqlVersionsCleanerConfig(
                config.isDisabled(),
                config.getCronExpression(),
                config.getNbVersionsToKeep(),
                config.isDeleteOrphanedVersions(),
                config.isCheckIntegrity(),
                config.isReindexDefaultWorkspace(),
                config.getMaxExecutionTimeInMs()
        );
    }

    @GraphQLName("VersionsCleanerConfig")
    @GraphQLDescription("Versions cleaner scheduled job configuration")
    public static class GqlVersionsCleanerConfig {

        private final boolean disabled;
        private final String cronExpression;
        private final long nbVersionsToKeep;
        private final boolean deleteOrphanedVersions;
        private final boolean checkIntegrity;
        private final boolean reindexDefaultWorkspace;
        private final long maxExecutionTimeInMs;

        public GqlVersionsCleanerConfig(boolean disabled, String cronExpression, long nbVersionsToKeep,
                boolean deleteOrphanedVersions, boolean checkIntegrity,
                boolean reindexDefaultWorkspace, long maxExecutionTimeInMs) {
            this.disabled = disabled;
            this.cronExpression = cronExpression;
            this.nbVersionsToKeep = nbVersionsToKeep;
            this.deleteOrphanedVersions = deleteOrphanedVersions;
            this.checkIntegrity = checkIntegrity;
            this.reindexDefaultWorkspace = reindexDefaultWorkspace;
            this.maxExecutionTimeInMs = maxExecutionTimeInMs;
        }

        public static GqlVersionsCleanerConfig defaults() {
            return new GqlVersionsCleanerConfig(
                    VersionsCleanerConfig.DEFAULT_DISABLED,
                    VersionsCleanerConfig.DEFAULT_CRON_EXPRESSION,
                    VersionsCleanerConfig.DEFAULT_NB_VERSIONS_TO_KEEP,
                    VersionsCleanerConfig.DEFAULT_DELETE_ORPHANED_VERSIONS,
                    VersionsCleanerConfig.DEFAULT_CHECK_INTEGRITY,
                    VersionsCleanerConfig.DEFAULT_REINDEX_DEFAULT_WORKSPACE,
                    VersionsCleanerConfig.DEFAULT_MAX_EXECUTION_TIME_IN_MS
            );
        }

        @GraphQLField
        @GraphQLName("disabled")
        @GraphQLDescription("Whether the scheduled cleanup job is disabled")
        public boolean isDisabled() {
            return disabled;
        }

        @GraphQLField
        @GraphQLName("cronExpression")
        @GraphQLDescription("Quartz cron expression for the scheduled cleanup")
        public String getCronExpression() {
            return cronExpression;
        }

        @GraphQLField
        @GraphQLName("nbVersionsToKeep")
        @GraphQLDescription("Number of versions to retain per non-orphaned history (-1 = skip)")
        public long getNbVersionsToKeep() {
            return nbVersionsToKeep;
        }

        @GraphQLField
        @GraphQLName("deleteOrphanedVersions")
        @GraphQLDescription("Whether to delete orphaned version histories")
        public boolean isDeleteOrphanedVersions() {
            return deleteOrphanedVersions;
        }

        @GraphQLField
        @GraphQLName("checkIntegrity")
        @GraphQLDescription("Whether to check and fix JCR reference integrity")
        public boolean isCheckIntegrity() {
            return checkIntegrity;
        }

        @GraphQLField
        @GraphQLName("reindexDefaultWorkspace")
        @GraphQLDescription("Whether to reindex the default workspace before cleaning")
        public boolean isReindexDefaultWorkspace() {
            return reindexDefaultWorkspace;
        }

        @GraphQLField
        @GraphQLName("maxExecutionTimeInMs")
        @GraphQLDescription("Maximum execution time in milliseconds (0 = unlimited)")
        public long getMaxExecutionTimeInMs() {
            return maxExecutionTimeInMs;
        }
    }
}
