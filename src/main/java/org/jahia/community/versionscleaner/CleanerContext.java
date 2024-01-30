package org.jahia.community.versionscleaner;

import org.jahia.bin.filters.jcr.JcrSessionFilter;
import org.jahia.services.content.JCRSessionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CleanerContext {

    private static final Logger logger = LoggerFactory.getLogger(CleanerContext.class);

    private boolean reindexDefaultWorkspace = Boolean.FALSE;
    private boolean checkIntegrity = Boolean.FALSE;
    private long nbVersionsToKeep = -1L;
    private long maxExecutionTimeInMs = -1L;
    private boolean deleteOrphanedVersions = Boolean.FALSE;
    private String subtreePath = null;
    private long pauseDuration = -1L;
    private List<String> skippedPaths = null;
    private boolean restartFromLastPosition = Boolean.FALSE;
    private boolean runAsynchronously = Boolean.TRUE;
    private long thresholdLongHistoryPurgeStrategy = 1000;
    private boolean useVersioningApi = Boolean.FALSE;
    private AtomicBoolean interruptionHandler;
    private long startTime;
    private long deletedVersionsCount;
    private long deletedVersionHistoriesCount;
    private long deletedOrphanVersionsCount;
    private long deletedOrphanVersionHistoriesCount;

    private Connection dbConnection;
    private JCRSessionWrapper editSession;
    private JCRSessionWrapper liveSession;

    public CleanerContext() {
        interruptionHandler = new AtomicBoolean();
    }

    public void startProcess() {
        interruptionHandler.set(Boolean.FALSE);
        startTime = -1L;
        deletedVersionsCount = 0L;
        deletedVersionHistoriesCount = 0L;
        deletedOrphanVersionsCount = 0L;
        deletedOrphanVersionHistoriesCount = 0L;
    }

    public void finalizeProcess() {
        dbConnection = null;
        editSession = null;
        liveSession = null;
        JcrSessionFilter.endRequest();
    }

    public boolean deleteNonOrphanVersions() {
        return nbVersionsToKeep >= 0;
    }

    public boolean scanVersionsTree() {
        return deleteOrphanedVersions || deleteNonOrphanVersions();
    }

    public void trackDeletedVersions(long count, boolean areOrphan) {
        if (areOrphan) deletedOrphanVersionsCount += count;
        else deletedVersionsCount += count;
    }

    public void trackDeletedVersionHistory(boolean isOrphan) {
        if (isOrphan) deletedOrphanVersionHistoriesCount += 1L;
        else deletedVersionHistoriesCount += 1L;
    }

    public long getDeletedVersionsCount() {
        return deletedVersionsCount;
    }

    public long getDeletedVersionHistoriesCount() {
        return deletedVersionHistoriesCount;
    }

    public long getDeletedOrphanVersionsCount() {
        return deletedOrphanVersionsCount;
    }

    public long getDeletedOrphanVersionHistoriesCount() {
        return deletedOrphanVersionHistoriesCount;
    }

    public boolean isReindexDefaultWorkspace() {
        return reindexDefaultWorkspace;
    }

    public CleanerContext setReindexDefaultWorkspace(boolean reindexDefaultWorkspace) {
        this.reindexDefaultWorkspace = reindexDefaultWorkspace;
        return this;
    }

    public boolean isCheckIntegrity() {
        return checkIntegrity;
    }

    public CleanerContext setCheckIntegrity(boolean checkIntegrity) {
        this.checkIntegrity = checkIntegrity;
        return this;
    }

    public long getNbVersionsToKeep() {
        return nbVersionsToKeep;
    }

    public CleanerContext setNbVersionsToKeep(long nbVersionsToKeep) {
        this.nbVersionsToKeep = nbVersionsToKeep;
        return this;
    }

    public long getMaxExecutionTimeInMs() {
        return maxExecutionTimeInMs;
    }

    public CleanerContext setMaxExecutionTimeInMs(long maxExecutionTimeInMs) {
        this.maxExecutionTimeInMs = maxExecutionTimeInMs;
        return this;
    }

    public boolean isDeleteOrphanedVersions() {
        return deleteOrphanedVersions;
    }

    public CleanerContext setDeleteOrphanedVersions(boolean deleteOrphanedVersions) {
        this.deleteOrphanedVersions = deleteOrphanedVersions;
        return this;
    }

    public String getSubtreePath() {
        return subtreePath;
    }

    public CleanerContext setSubtreePath(String subtreePath) {
        this.subtreePath = subtreePath;
        return this;
    }

    public long getPauseDuration() {
        return pauseDuration;
    }

    public CleanerContext setPauseDuration(long pauseDuration) {
        this.pauseDuration = pauseDuration;
        return this;
    }

    public List<String> getSkippedPaths() {
        return skippedPaths;
    }

    public CleanerContext setSkippedPaths(List<String> skippedPaths) {
        this.skippedPaths = skippedPaths;
        return this;
    }

    public boolean isRestartFromLastPosition() {
        return restartFromLastPosition;
    }

    public CleanerContext setRestartFromLastPosition(boolean restartFromLastPosition) {
        this.restartFromLastPosition = restartFromLastPosition;
        return this;
    }

    public boolean isRunAsynchronously() {
        return runAsynchronously;
    }

    public CleanerContext setRunAsynchronously(boolean runAsynchronously) {
        this.runAsynchronously = runAsynchronously;
        return this;
    }

    public long getThresholdLongHistoryPurgeStrategy() {
        return thresholdLongHistoryPurgeStrategy;
    }

    public CleanerContext setThresholdLongHistoryPurgeStrategy(long thresholdLongHistoryPurgeStrategy) {
        this.thresholdLongHistoryPurgeStrategy = thresholdLongHistoryPurgeStrategy;
        return this;
    }

    public boolean isUseVersioningApi() {
        return useVersioningApi;
    }

    public CleanerContext setUseVersioningApi(boolean useVersioningApi) {
        this.useVersioningApi = useVersioningApi;
        return this;
    }

    public AtomicBoolean getInterruptionHandler() {
        return interruptionHandler;
    }

    public Connection getDbConnection() {
        return dbConnection;
    }

    public CleanerContext setDbConnection(Connection dbConnection) {
        this.dbConnection = dbConnection;
        return this;
    }

    public long getStartTime() {
        return startTime;
    }

    public CleanerContext setStartTime() {
        startTime = System.currentTimeMillis();
        return this;
    }

    public JCRSessionWrapper getEditSession() {
        return editSession;
    }

    public CleanerContext setEditSession(JCRSessionWrapper editSession) {
        this.editSession = editSession;
        return this;
    }

    public JCRSessionWrapper getLiveSession() {
        return liveSession;
    }

    public CleanerContext setLiveSession(JCRSessionWrapper liveSession) {
        this.liveSession = liveSession;
        return this;
    }
}
