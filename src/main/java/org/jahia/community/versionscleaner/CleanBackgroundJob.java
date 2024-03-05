package org.jahia.community.versionscleaner;

import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

public class CleanBackgroundJob extends BackgroundJob {

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) throws Exception {
        final JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        final CleanerContext configuration = new CleanerContext()
                .setRunAsynchronously(Boolean.FALSE)
                .setRestartFromLastPosition(Boolean.TRUE)
                .setReindexDefaultWorkspace(jobDataMap.getBoolean("reindexDefaultWorkspace"))
                .setCheckIntegrity(jobDataMap.getBoolean("checkIntegrity"))
                .setNbVersionsToKeep(jobDataMap.getLong("nbVersionsToKeep"))
                .setMaxExecutionTimeInMs(jobDataMap.getLong("maxExecutionTimeInMs"))
                .setDeleteOrphanedVersions(jobDataMap.getBoolean("deleteOrphanedVersions"));
        CleanCommand.execute(configuration);
    }
}
