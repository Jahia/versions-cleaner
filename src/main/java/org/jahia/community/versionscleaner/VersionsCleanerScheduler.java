package org.jahia.community.versionscleaner;

import org.jahia.services.scheduler.BackgroundJob;
import org.jahia.services.scheduler.SchedulerService;
import org.jahia.settings.SettingsBean;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class VersionsCleanerScheduler {

    private static final Logger logger = LoggerFactory.getLogger(VersionsCleanerScheduler.class);

    private SchedulerService schedulerService;
    private VersionsCleanerConfig config;
    private JobDetail jobDetail;

    @Reference
    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @Reference
    public void setConfig(VersionsCleanerConfig config) {
        this.config = config;
    }

    @Activate
    public void start() throws org.quartz.SchedulerException, java.text.ParseException {
        if (config.isDisabled()) {
            logger.info("Versions Cleaner scheduled job is disabled");
            return;
        }
        if (!SettingsBean.getInstance().isProcessingServer()) {
            logger.info("Versions Cleaner scheduled job skipped: not on processing server");
            return;
        }

        jobDetail = BackgroundJob.createJahiaJob("Versions Cleaner", CleanBackgroundJob.class);
        if (schedulerService.getAllJobs(jobDetail.getGroup()).isEmpty()) {
            final JobDataMap map = jobDetail.getJobDataMap();
            map.put("reindexDefaultWorkspace", config.isReindexDefaultWorkspace());
            map.put("checkIntegrity", config.isCheckIntegrity());
            map.put("nbVersionsToKeep", config.getNbVersionsToKeep());
            map.put("maxExecutionTimeInMs", config.getMaxExecutionTimeInMs());
            map.put("deleteOrphanedVersions", config.isDeleteOrphanedVersions());

            final CronTrigger trigger = new CronTrigger(
                    "VersionsCleanerJobTrigger", jobDetail.getGroup(), config.getCronExpression());
            schedulerService.getScheduler().scheduleJob(jobDetail, trigger);
            logger.info("Versions Cleaner scheduled job registered with cron: {}", config.getCronExpression());
        }
    }

    @Deactivate
    public void stop() throws org.quartz.SchedulerException {
        if (jobDetail == null) {
            return;
        }
        if (!schedulerService.getAllJobs(jobDetail.getGroup()).isEmpty()
                && SettingsBean.getInstance().isProcessingServer()) {
            schedulerService.getScheduler().deleteJob(jobDetail.getName(), jobDetail.getGroup());
            logger.info("Versions Cleaner scheduled job unregistered");
        }
    }
}
