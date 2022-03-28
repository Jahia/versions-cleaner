package org.jahia.community.versionscleaner;

import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

public class CleanBackgroundJob extends BackgroundJob {

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) throws Exception {
        final JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        final Boolean reindexDefaultWorkspace = jobDataMap.getBoolean("reindexDefaultWorkspace");
        final Boolean checkIntegrity = jobDataMap.getBoolean("checkIntegrity");
        final Long nbVersionsToKeep = jobDataMap.getLong("nbVersionsToKeep");
        final Long maxExecutionTimeInMs = jobDataMap.getLong("maxExecutionTimeInMs");
        final Boolean deleteOrphanedVersions = jobDataMap.getBoolean("deleteOrphanedVersions");
        CleanCommand.deleteVersions(reindexDefaultWorkspace, checkIntegrity, nbVersionsToKeep, maxExecutionTimeInMs, deleteOrphanedVersions);
    }
}
