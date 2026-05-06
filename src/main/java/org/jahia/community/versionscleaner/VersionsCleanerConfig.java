package org.jahia.community.versionscleaner;

import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.annotations.Component;

import java.util.Dictionary;

@Component(immediate = true, service = {VersionsCleanerConfig.class, ManagedService.class},
        property = Constants.SERVICE_PID + "=org.jahia.community.versionscleaner")
public class VersionsCleanerConfig implements ManagedService {

    private boolean disabled = true;
    private String cronExpression = "0 30 1 * * ?";
    private boolean reindexDefaultWorkspace = false;
    private boolean checkIntegrity = false;
    private long nbVersionsToKeep = 2L;
    private long maxExecutionTimeInMs = 60000L;
    private boolean deleteOrphanedVersions = false;

    @Override
    public void updated(Dictionary<String, ?> props) throws ConfigurationException {
        if (props == null) {
            return;
        }
        if (props.get("disabled") != null) {
            disabled = Boolean.parseBoolean(String.valueOf(props.get("disabled")));
        }
        if (props.get("cronExpression") != null) {
            cronExpression = String.valueOf(props.get("cronExpression"));
        }
        if (props.get("reindexDefaultWorkspace") != null) {
            reindexDefaultWorkspace = Boolean.parseBoolean(String.valueOf(props.get("reindexDefaultWorkspace")));
        }
        if (props.get("checkIntegrity") != null) {
            checkIntegrity = Boolean.parseBoolean(String.valueOf(props.get("checkIntegrity")));
        }
        if (props.get("nbVersionsToKeep") != null) {
            nbVersionsToKeep = Long.parseLong(String.valueOf(props.get("nbVersionsToKeep")));
        }
        if (props.get("maxExecutionTimeInMs") != null) {
            maxExecutionTimeInMs = Long.parseLong(String.valueOf(props.get("maxExecutionTimeInMs")));
        }
        if (props.get("deleteOrphanedVersions") != null) {
            deleteOrphanedVersions = Boolean.parseBoolean(String.valueOf(props.get("deleteOrphanedVersions")));
        }
    }

    public boolean isDisabled() {
        return disabled;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public boolean isReindexDefaultWorkspace() {
        return reindexDefaultWorkspace;
    }

    public boolean isCheckIntegrity() {
        return checkIntegrity;
    }

    public long getNbVersionsToKeep() {
        return nbVersionsToKeep;
    }

    public long getMaxExecutionTimeInMs() {
        return maxExecutionTimeInMs;
    }

    public boolean isDeleteOrphanedVersions() {
        return deleteOrphanedVersions;
    }
}
