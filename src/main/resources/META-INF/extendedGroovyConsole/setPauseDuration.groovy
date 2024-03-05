import org.apache.commons.lang.math.NumberUtils

int duration = NumberUtils.toInt(pauseDuration, -1)
if (duration < 0) {
    log.info("Using the duration configured on each purge")
    System.clearProperty("versions-cleaner.pause.duration")
} else {
    log.info "Set the pause duration to ${duration}ms"
    System.setProperty("versions-cleaner.pause.duration", Integer.toString(duration))
}
log.info ""

// Script configurations
//script.title=Set the pause duration between 2 version deletion
//script.description=This applies to every purge (running at the time when the script is executed or started later), until the server is restarted. The configured duration has the priority over the one configured while starting the purge. If a negative value is set by the script, then the purge will use its default value back.
//script.parameters.names=pauseDuration
//script.param.pauseDuration.label=Pause duration in ms
//script.param.pauseDuration.type=text
//script.param.pauseDuration.default=60000
