log.info stopPurge
log.info stopPurge.class
System.setProperty("versions-cleaner.interrupt", Boolean.toString(stopPurge))

// Script configurations
//script.title=Interrupt the current version purge
//script.description=If executed while a purge is running, the process will gracefully stop where it is arrived to
//script.parameters.names=stopPurge
//script.param.stopPurge.label=Check to stop
//script.param.stopPurge.default=true
