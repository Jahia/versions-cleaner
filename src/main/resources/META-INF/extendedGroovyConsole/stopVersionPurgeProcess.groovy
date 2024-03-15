if (stopPurge) {
    log.info "Requested the interruption of the current scan"
    System.setProperty("versions-cleaner.interrupt", "true")
} else {
    log.info "Cancelled the interruption of the current scan"
    System.clearProperty("versions-cleaner.interrupt")
}
log.info ""

// Script configurations
//script.title=Interrupt the current version purge
//script.description=If executed while a purge is running, the process will gracefully stop where it is arrived to
//script.parameters.names=stopPurge
//script.param.stopPurge.label=Check to stop
//script.param.stopPurge.default=true
