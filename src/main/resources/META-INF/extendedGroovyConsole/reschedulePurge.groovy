import org.apache.commons.lang.math.NumberUtils
import org.jahia.registries.ServicesRegistry
import org.jahia.services.SpringContextSingleton
import org.jahia.services.scheduler.JobSchedulingBean
import org.jahia.services.scheduler.SchedulerService
import org.quartz.JobDetail
import org.quartz.Trigger

final SchedulerService schedulerService = ServicesRegistry.getInstance().getSchedulerService()
final List<JobDetail> jobs = schedulerService.getAllActiveJobs("Maintenance")
boolean isRunning = false
for (JobDetail jd : jobs) {
    if ("VersionsCleanerJob".equals(jd.getName())) {
        isRunning = true
    }
}

if (isRunning) {
    log.info "A scheduled purge is already running, you need to stop it before rescheduling"
} else {
    final JobSchedulingBean bean = SpringContextSingleton.getBeanInModulesContext("scheduled-versions-purge")
    if (bean.disabled) {
        log.warn "The scheduled purge is disable, impossible to reschedule it"
    } else {
        bean.destroy()
        Trigger trigger = bean.triggers.get(0)
        trigger.setCronExpression(cron)
        bean.jobDetail.getJobDataMap().put("maxExecutionTimeInMs", NumberUtils.toLong("duration", 60L) * 60L * 1000L)
        bean.afterPropertiesSet()
    }
}



// Script configurations
//script.title=Versions purge scheduling
//script.description=The purge will be executed according to the new schedule. If the module (or server) is restarted, the schedule will use the persisted configurations back
//script.visibilityCondition={jahia.versions.cleaner.job.disabled=false}
//script.parameters.names=cron,duration
//script.param.cron.label=CRON
//script.param.cron.type=text
//script.param.cron.default=0 30 1 * * ?
//script.param.duration.label=Duration (in minutes)
//script.param.duration.type=text
//script.param.duration.default=1
