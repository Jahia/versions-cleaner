package org.jahia.community.versionscleaner;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.core.JahiaRepositoryImpl;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.id.NodeId;
import org.apache.jackrabbit.core.version.InternalVersionHistory;
import org.apache.jackrabbit.core.version.InternalVersionManager;
import org.apache.jackrabbit.core.version.InternalVersionManagerImpl;
import org.apache.jackrabbit.core.version.InternalXAVersionManager;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jahia.api.Constants;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.impl.jackrabbit.SpringJackrabbitRepository;
import org.jahia.services.scheduler.SchedulerService;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.DatabaseUtils;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RangeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Command(scope = "versions-cleaner", name = "run", description = "Run a scan the versions tree, and perform the configured actions")
@Service
public class CleanCommand implements Action {

    private static final Logger logger = LoggerFactory.getLogger(CleanCommand.class);
    private static final String HUMAN_READABLE_FORMAT = "d' days 'H' hours 'm' minutes 's' seconds'";
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING = new java.util.concurrent.atomic.AtomicBoolean(false);

    public static boolean isRunning() {
        return RUNNING.get();
    }
    // S1075: this is the JCR-specification-mandated version storage path, not a configurable location.
    @SuppressWarnings("java:S1075")
    private static final String VERSIONS_PATH = "/jcr:system/jcr:versionStorage";
    private static final String[] INVALID_REFERENCE_NODE_TYPES_TO_REMOVE = new String[]{
            JcrConstants.NT_HIERARCHYNODE,
            Constants.JAHIANT_MEMBER,
            "jnt:reference"
    };
    private static final String INTERRUPT_MARKER = "versions-cleaner.interrupt";
    private static final String PAUSE_DURATION_MARKER = "versions-cleaner.pause.duration";
    private static final String STARTUP_DELAY_MARKER = "versions-cleaner.startup.delay";
    private static final String FAILED_TO_REMOVE = "Failed to remove ";
    // The version storage of a node always contains an implicit jcr:rootVersion that must never be deleted.
    // Retention math is expressed in terms of deletable (non-root) versions, so the desired count is offset by it.
    private static final int ROOT_VERSION_COUNT = 1;
    // Default truncation length applied to version names before logging, to avoid flooding logs with huge names.
    private static final int MAX_PRINTABLE_NAME_LENGTH = 2000;

    @Option(name = "-r", aliases = "--reindex-default-workspace", description = "Reindex default workspace before cleaning")
    private Boolean reindexDefaultWorkspace = Boolean.FALSE;

    @Option(name = "-c", aliases = "--check-integrity", description = "Check integrity of the versions")
    private Boolean checkIntegrity = Boolean.FALSE;

    @Option(name = "-n", aliases = "--nb-versions-to-keep", description = "Number of versions to keep")
    private Long nbVersionsToKeep = -1L;

    @Option(name = "-t", aliases = "--max-execution-time-in-ms", description = "Max execution time in ms")
    private Long maxExecutionTimeInMs = 0L;

    @Option(name = "-o", aliases = "--delete-orphaned-versions", description = "Delete orphaned versions")
    private Boolean deleteOrphanedVersions = Boolean.FALSE;

    @Option(name = "-p", aliases = "--subtree-path", description = "Subtree of versions tree where the purge has to be run")
    private String subtreePath = null;

    @Option(name = "-pause", description = "Duration of the pause between 2 version deletions. No pause if less or equal to zero. Zero by default")
    private Long pauseDuration = 0L;

    @Option(name = "-skip", aliases = "--skip-subtree", multiValued = true, description = "Path to be skipped by the process. Useful for example if you have identified some version histories which are particularly massive, and you want to iterate over the rest first. Several paths can be defined")
    private List<String> skippedPaths = null;

    @Option(name = "-threshold-long-history-purge-strategy", description = "Number of versions over which orphaned histories are purged by deleting the versions one by one, to reduce the memory footprint. 1000 by default")
    private long thresholdLongHistoryPurgeStrategy = 1000L;

    @Option(name = "-force-restart-from-the-beginning", description = "If specified, the process will restart from the beginning of the tree. Otherwise, it will try to restart from where the previous execution had stopped")
    private boolean forceRestartFromBeginning = false;

    @Override
    public Object execute() throws RepositoryException {
        final CleanerContext context = new CleanerContext()
                .setReindexDefaultWorkspace(reindexDefaultWorkspace)
                .setCheckIntegrity(checkIntegrity)
                .setNbVersionsToKeep(nbVersionsToKeep)
                .setMaxExecutionTimeInMs(maxExecutionTimeInMs)
                .setDeleteOrphanedVersions(deleteOrphanedVersions)
                .setSubtreePath(subtreePath)
                .setPauseDuration(pauseDuration)
                .setSkippedPaths(skippedPaths)
                .setThresholdLongHistoryPurgeStrategy(thresholdLongHistoryPurgeStrategy)
                .setRestartFromLastPosition(!forceRestartFromBeginning);

        execute(context);
        return null;
    }

    public static void execute(CleanerContext context) throws RepositoryException {
        if (!RUNNING.compareAndSet(false, true)) {
            logger.info("Versions cleaner execute requested but already running");
            return;
        }
        if (context.isRunAsynchronously()) {
            final Thread worker = new Thread(() -> {
                try {
                    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                    applyStartupDelay();
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    context.startProcess();
                    deleteVersions(context);
                } catch (RepositoryException e) {
                    logger.error("", e);
                } finally {
                    RUNNING.set(false);
                    context.finalizeProcess();
                }
            }, "versions-cleaner-worker");
            worker.setDaemon(true);
            worker.start();
        } else {
            try {
                context.startProcess();
                deleteVersions(context);
            } finally {
                RUNNING.set(false);
                context.finalizeProcess();
            }
        }
    }

    private static void deleteVersions(CleanerContext context) throws RepositoryException {
        if (!SettingsBean.getInstance().isProcessingServer()) {
            logger.info("This command can only be executed on the processing server");
            return;
        }

        System.clearProperty(INTERRUPT_MARKER);
        if (needsToInterrupt(context)) {
            return;
        }

        context.setEditSession(JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null));
        context.setLiveSession(JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.LIVE_WORKSPACE, null, null));

        if (context.isReindexDefaultWorkspace()) {
            performReindex();
        }

        if (needsToInterrupt(context)) {
            return;
        }

        if (context.scanVersionsTree()) {
            context.setStartTime();
            final JCRNodeWrapper node = getNode(context.getEditSession().getNode(VERSIONS_PATH), context.getSubtreePath());
            logger.info("Starting to scan the versions under {}", node.getPath());
            try (final Connection conn = DatabaseUtils.getDatasource().getConnection()) {
                context.setDbConnection(conn);
                processNode(node, context);
                if (logger.isInfoEnabled()) logger.info("Finished to scan the versions under {} in {}", node.getPath(), toReadableDuration(context.getStartTime()));
                printDeletionSummary(context);
                if (!needsToInterrupt(context)) context.endOfTreeReached();
            } catch (SQLException e) {
                logger.error("Failed to retrieve the DB connection", e);
            }
        }
    }

    private static void performReindex() {
        try {
            final long start = System.currentTimeMillis();
            logger.info("Starting reindexing of default workspace");
            ((JahiaRepositoryImpl) ((SpringJackrabbitRepository) JCRSessionFactory.getInstance().getDefaultProvider().getRepository()).getRepository()).scheduleReindexing(Constants.EDIT_WORKSPACE);
            final SchedulerService schedulerService = ServicesRegistry.getInstance().getSchedulerService();
            boolean continueChecking = true;
            while (continueChecking) {
                Thread.sleep(5000L);
                boolean reindexInProgress = false;
                final List<JobDetail> jobs = schedulerService.getAllRAMJobs();
                for (JobDetail job : jobs) {
                    if (job.getName().startsWith("JahiaSearchIndex") && !"successful".equals(job.getJobDataMap().getString("status"))) {
                        reindexInProgress = true;
                        logger.info("Reindexing is still in progress");
                    }
                }
                continueChecking = reindexInProgress;
            }
            if (logger.isInfoEnabled()) logger.info("Finished reindexing default workspace in {}", toReadableDuration(start));
        } catch (SchedulerException ex) {
            logger.error("Impossible to monitor reindexing job", ex);
        } catch (InterruptedException ex) {
            logger.error("Impossible to pause the thread", ex);
            Thread.currentThread().interrupt();
        } catch (RepositoryException ex) {
            logger.error("Failed to schedule reindexing", ex);
        }
    }

    private static void processNode(JCRNodeWrapper node, CleanerContext context) throws RepositoryException {
        if (needsToInterrupt(context)) return;

        final String path = node.getPath();
        if (CollectionUtils.isNotEmpty(context.getSkippedPaths()) && context.getSkippedPaths().contains(path)) {
            logger.info("Skipping {}", path);
            return;
        }

        if (node.isNodeType(Constants.NT_VERSIONHISTORY)) {
            if (!context.canProcess(node)) return;
            logger.debug("Processing {}", path);
            checkNodeIntegrity(context.getEditSession(), node, true, true, context);
            if (isOrphanedHistory(node, context)) {
                deleteOrphanedHistory((VersionHistory) node, context);
            } else {
                keepLastNVersions((VersionHistory) node, context);
            }
            context.refreshSessions();
        } else {
            final JCRNodeIteratorWrapper childNodes = node.getNodes();
            while (childNodes.hasNext()) {
                processNode((JCRNodeWrapper) childNodes.nextNode(), context);
                if (needsToInterrupt(context)) return;
            }
        }
    }

    private static boolean isOrphanedHistory(JCRNodeWrapper versionHistory, CleanerContext context) throws RepositoryException {
        final JCRNodeIteratorWrapper it = versionHistory.getNodes();
        while (it.hasNext()) {
            final JCRNodeWrapper node = (JCRNodeWrapper) it.next();
            if (node.isNodeType(JcrConstants.NT_VERSION) && node.hasNode(JcrConstants.JCR_FROZENNODE)) {
                final JCRNodeWrapper frozen = node.getNode(JcrConstants.JCR_FROZENNODE);
                if (frozen.hasProperty(JcrConstants.JCR_FROZENUUID)) {
                    final String uuid = frozen.getPropertyAsString(JcrConstants.JCR_FROZENUUID);
                    return isUuidOrphaned(uuid, context);
                }
            }
        }
        return false;
    }

    private static boolean isUuidOrphaned(String uuid, CleanerContext context) throws RepositoryException {
        try {
            context.getEditSession().getNodeByIdentifier(uuid);
            return false;
        } catch (ItemNotFoundException ex) {
            try {
                context.getLiveSession().getNodeByIdentifier(uuid);
                return false;
            } catch (ItemNotFoundException ex2) {
                return true;
            }
        }
    }

    private static void deleteOrphanedHistory(VersionHistory vh, CleanerContext context) throws RepositoryException {
        if (!context.isDeleteOrphanedVersions()) return;

        logger.debug("Orphan version history to delete: {}", vh.getPath());
        final RangeIterator versionIterator = getVersionsIterator(vh, context);
        long nbVersions = getVersionsCount(vh, versionIterator, context);
        if (nbVersions > context.getThresholdLongHistoryPurgeStrategy()) {
            final String vhPath = vh.getPath();
            logger.warn("{} has {} versions", vhPath, nbVersions);
            final List<String> versionNames = getNonRootVersionNames(versionIterator, context);
            if (needsToInterrupt(context)) return;
            final long deletedVersions = deleteVersionNodes(vh, versionNames, context);
            context.trackDeletedVersions(deletedVersions, true);
            if (needsToInterrupt(context)) return;

            final long remainingVersions = nbVersions - deletedVersions;
            if (remainingVersions > context.getThresholdLongHistoryPurgeStrategy()) {
                logger.debug("Finished processing {} , deleted {} versions, {} versions remaining", vhPath, deletedVersions, remainingVersions);
                return;
            } else {
                logger.debug("Finished processing {} , deleted {} versions, handling it from now on as any other orphaned version history", vhPath, deletedVersions);
                nbVersions = remainingVersions;
            }
        }

        if (needsToInterrupt(context)) return;

        final NodeId id = NodeId.valueOf(vh.getIdentifier());
        final SessionImpl providerSession = (SessionImpl) context.getEditSession().getProviderSession(context.getEditSession().getNode("/").getProvider());
        final InternalVersionManager vm = providerSession.getInternalVersionManager();
        final List<InternalVersionHistory> unusedVersions = Collections.singletonList(vm.getVersionHistory(id));
        int[] results = {0, 0};
        if (vm instanceof InternalVersionManagerImpl) {
            results = ((InternalVersionManagerImpl) vm).purgeVersions(providerSession, unusedVersions);
        } else if (vm instanceof InternalXAVersionManager) {
            results = ((InternalXAVersionManager) vm).purgeVersions(providerSession, unusedVersions);
        }
        if (results[0] + results[1] > 0) {
            // Assumption: purgeVersions either deletes the whole history or nothing (results[0]+results[1] > 0 means success)
            context.trackDeletedVersions(nbVersions, true);
            context.trackDeletedVersionHistory(true);
        }
    }

    private static List<String> getNonRootVersionNames(RangeIterator versionIterator, CleanerContext context) throws RepositoryException {
        if (versionIterator.getPosition() != 0) {
            throw new IllegalArgumentException("The provided iterator has already been iterated");
        }

        final List<String> versionNames = new ArrayList<>();
        while (!needsToInterrupt(context) && versionIterator.hasNext()) {
            final Node version = (Node) versionIterator.next();
            if (version.isNodeType(JcrConstants.NT_VERSION)) {
                final String versionName = version.getName();
                if (JcrConstants.JCR_ROOTVERSION.equals(versionName)) {
                    if (logger.isDebugEnabled()) logger.debug("Skipping {} as it is the root version", toPrintableName(versionName));
                } else {
                    versionNames.add(versionName);
                }
            }
        }
        return versionNames;
    }

    private static RangeIterator getVersionsIterator(VersionHistory vh, CleanerContext context) throws RepositoryException {
        if (context.isUseVersioningApi()) {
            if (vh instanceof JCRNodeWrapper) {
                final VersionHistory realNode = (VersionHistory) ((JCRNodeWrapper) vh).getRealNode();
                return realNode.getAllVersions();
            } else {
                return vh.getAllVersions();
            }
        } else {
            return vh.getNodes();
        }
    }

    private static long getVersionsCount(VersionHistory vh, RangeIterator versionIterator, CleanerContext context) throws RepositoryException {
        return context.isUseVersioningApi() || !vh.hasNode("jcr:versionLabels") ? versionIterator.getSize() : versionIterator.getSize() - 1;
    }

    private static void keepLastNVersions(VersionHistory vh, CleanerContext context) {
        final long nbVersionsToKeep = context.getNbVersionsToKeep();
        if (nbVersionsToKeep < 0) return;

        String path;
        try {
            path = vh.getPath();
        } catch (RepositoryException e) {
            logger.error("Failed to get version history path", e);
            path = "<failed to calculate the path>";
        }
        logger.debug("Non orphan version history to reduce: {}", path);
        try {
            final RangeIterator versionIterator = getVersionsIterator(vh, context);
            final long nbVersions = getVersionsCount(vh, versionIterator, context);
            logger.debug("{} has {} versions", path, nbVersions);
            // Do clean only if we have more versions than the desired number + the implicit root version.
            if (nbVersions > nbVersionsToKeep + ROOT_VERSION_COUNT) {
                final List<String> versionNames = getNonRootVersionNames(versionIterator, context);
                retainNewestVersions(versionNames, nbVersionsToKeep);
                final long deletedVersions = deleteVersionNodes(vh, versionNames, context);
                context.trackDeletedVersions(deletedVersions, false);
            }
        } catch (Exception ex) {
            // Isolate the failure to this version history: log with context (this is a destructive
            // operation) and let the scan continue with the remaining histories rather than aborting.
            logger.error("Failed to reduce version history {}", path, ex);
        }
    }

    /**
     * Trims {@code versionNames} (ordered oldest-first, root already excluded) so that only the
     * {@code nbVersionsToKeep} newest entries are removed from the deletion candidate list, leaving the
     * oldest ones to be deleted. The cut point is clamped defensively: counting differences between
     * the version-storage iterator and the materialised candidate list must never produce a negative
     * index or attempt to keep more versions than exist.
     */
    static void retainNewestVersions(List<String> versionNames, long nbVersionsToKeep) {
        final int size = versionNames.size();
        // The retain count never exceeds the number of versions for a node and so fits within an int range.
        // Clamp it anyway to stay safe against pathological or overflowing configuration values.
        final int nbToKeep = (int) Math.min(nbVersionsToKeep, size);
        final int fromIndex = Math.max(0, size - nbToKeep);
        if (fromIndex >= size) return;
        versionNames.subList(fromIndex, size).clear();
    }

    private static long deleteVersionNodes(VersionHistory vh, List<String> names, CleanerContext context) {
        long deletedVersions = 0L;
        final List<String> versionNames = new ArrayList<>(names);
        int nbVersionPurgedInCurrentLoop;
        int nbLoops = 0;
        final List<String> skippedVersionNames = new ArrayList<>();
        do {
            nbLoops++;
            nbVersionPurgedInCurrentLoop = 0;
            for (String versionName : versionNames) {
                if (removeOneVersion(vh, versionName, skippedVersionNames, context, nbLoops, deletedVersions)) {
                    nbVersionPurgedInCurrentLoop++;
                    deletedVersions++;
                }
                if (needsToInterrupt(context)) break;
            }
            versionNames.clear();
            versionNames.addAll(skippedVersionNames);
            skippedVersionNames.clear();
        } while (!needsToInterrupt(context) && nbVersionPurgedInCurrentLoop > 0 && !versionNames.isEmpty());

        if (deletedVersions < names.size()) {
            try {
                logger.warn("Skipped {} versions on {}", names.size() - deletedVersions, vh.getPath());
            } catch (RepositoryException e) {
                logger.warn("Skipped some versions (path unavailable)", e);
            }
        }
        return deletedVersions;
    }

    private static boolean removeOneVersion(VersionHistory vh, String versionName,
            List<String> skippedVersionNames, CleanerContext context, int nbLoops, long totalDeleted) {
        long nbReferences = -1;
        boolean canProcess = false;
        final StopWatch stopWatch = new StopWatch();
        stopWatch.start("Load and count references");
        try {
            final Version version = vh.getVersion(versionName);
            nbReferences = version.getReferences().getSize();
            canProcess = true;
        } catch (RepositoryException e) {
            logger.error(FAILED_TO_REMOVE, versionName, e);
            skippedVersionNames.add(versionName);
        }
        stopWatch.stop();
        if (!canProcess) {
            return false;
        }
        if (nbReferences > 0) {
            if (logger.isDebugEnabled()) logger.debug("Skipping {} as it is referenced", toPrintableName(versionName));
            skippedVersionNames.add(versionName);
            return false;
        }
        stopWatch.start("Version remove");
        boolean deleted = false;
        try {
            vh.removeVersion(versionName);
            deleted = true;
            if (logger.isDebugEnabled()) logger.debug("Removed a version (deleted {} versions, deleted={} / skipped={} in loop {}): {}",
                    totalDeleted + 1, 1, skippedVersionNames.size(), nbLoops, toPrintableName(versionName));
        } catch (RepositoryException | RuntimeException e) {
            logger.error(FAILED_TO_REMOVE, versionName, e);
            skippedVersionNames.add(versionName);
        }
        stopWatch.stop();
        pauseBetweenDeletions(context, stopWatch);
        if (logger.isDebugEnabled()) logger.debug(stopWatch.prettyPrint());
        return deleted;
    }

    private static void pauseBetweenDeletions(CleanerContext context, StopWatch stopWatch) {
        final long sleepDuration = getSleepDuration(context);
        if (sleepDuration > 0) {
            stopWatch.start("Pause");
            try {
                Thread.sleep(sleepDuration);
            } catch (InterruptedException e) {
                logger.error("Thread interrupted during pause between version deletions", e);
                Thread.currentThread().interrupt();
            }
            stopWatch.stop();
        }
    }

    private static long getSleepDuration(CleanerContext context) {
        try {
            return Long.parseLong(System.getProperty(PAUSE_DURATION_MARKER));
        } catch (NumberFormatException ignored) {
            return context.getPauseDuration();
        }
    }

    private static void applyStartupDelay() {
        try {
            final long delay = Long.parseLong(System.getProperty(STARTUP_DELAY_MARKER, "0"));
            if (delay > 0) {
                Thread.sleep(delay);
            }
        } catch (NumberFormatException ignored) {
            // property not set or invalid — no delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void setStartupDelay(long delayMs) {
        if (delayMs > 0) {
            System.setProperty(STARTUP_DELAY_MARKER, String.valueOf(delayMs));
        } else {
            System.clearProperty(STARTUP_DELAY_MARKER);
        }
    }

    private static void checkNodeIntegrity(Session session, Node node,
                                           boolean fix, boolean referencesCheck, CleanerContext context) throws RepositoryException {
        if (!context.isCheckIntegrity()) return;

        try {
            if (fix || referencesCheck) {
                PropertyIterator propertyIterator = node.getProperties();
                while (propertyIterator.hasNext()) {
                    if (!processProperty(session, node, propertyIterator.nextProperty(), fix, referencesCheck, context)) {
                        return;
                    }
                }
            }
        } catch (RepositoryException ex) {
            logger.warn("Exception while processing node {}", node.getPath(), ex);
        }
    }

    private static boolean processProperty(Session session, Node node, Property property,
                                            boolean fix, boolean referencesCheck, CleanerContext context) throws RepositoryException {
        if (property.isMultiple()) {
            return processMultipleValueProperty(session, node, property, fix, referencesCheck, context);
        }
        return processPropertyValue(session, node, property, property.getValue(), fix, referencesCheck, context);
    }

    private static boolean processMultipleValueProperty(Session session, Node node, Property property,
                                                         boolean fix, boolean referencesCheck, CleanerContext context) throws RepositoryException {
        try {
            Value[] values = property.getValues();
            for (Value value : values) {
                if (!processPropertyValue(session, node, property, value, fix, referencesCheck, context)) {
                    return false;
                }
            }
        } catch (ConstraintViolationException ex) {
            logger.warn("Property definition for node {} is missing", node.getPath(), ex);
        }
        return true;
    }

    private static boolean processPropertyValue(Session session, Node node, Property property, Value propertyValue,
                                                boolean fix, boolean referencesCheck, CleanerContext context) throws RepositoryException {
        int propertyType = propertyValue.getType();
        if ((propertyType == PropertyType.REFERENCE || propertyType == PropertyType.WEAKREFERENCE) && referencesCheck) {
            return processReferenceProperty(session, node, property, propertyValue.getString(), fix, context);
        }
        return true;
    }

    private static boolean processReferenceProperty(Session session, Node node, Property property,
                                                     String uuid, boolean fix, CleanerContext context) throws RepositoryException {
        try {
            session.getNodeByIdentifier(uuid);
        } catch (ItemNotFoundException infe) {
            if (isExternalMapping(uuid, context)) {
                logger.info("Mapping found towards an external provider for UUID {} (referenced from property {}), please check mount points", uuid, property.getPath());
                if (fix) {
                    logger.info("It will not be fixed automatically");
                }
                return true;
            }
            logger.info("Couldn't find referenced node with UUID {} referenced from property {}", uuid, property.getPath());
            if (fix) {
                if (mustRemoveParentNode(node)) {
                    fixInvalidNodeReference(node);
                    return false;
                } else {
                    fixInvalidPropertyReference(node, property, uuid);
                }
            }
        }
        return true;
    }

    private static boolean isExternalMapping(String uuid, CleanerContext context) {
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            statement = context.getDbConnection().prepareStatement(
                    "select externalId from jahia_external_mapping where internalUuid=?");
            statement.setString(1, uuid);
            resultSet = statement.executeQuery();
            return resultSet.next();
        } catch (SQLException ex) {
            return false;
        } finally {
            DatabaseUtils.closeQuietly(resultSet);
            DatabaseUtils.closeQuietly(statement);
        }
    }

    private static void fixInvalidPropertyReference(Node node, Property property, String uuid) throws RepositoryException {
        logger.info("Fixing invalid reference by setting reference property {} to null...", property.getPath());
        Calendar originalLastModificationDate;
        try {
            originalLastModificationDate = node.getProperty(Constants.JCR_LASTMODIFIED).getDate();
        } catch (PathNotFoundException pnfe) {
            originalLastModificationDate = null;
        }
        if (property.isMultiple()) {
            Value[] oldValues = property.getValues();
            List<Value> newValues = new LinkedList<>();
            for (Value oldValue : oldValues) {
                if (!oldValue.getString().equals(uuid)) {
                    newValues.add(oldValue);
                }
            }
            property.setValue(newValues.toArray(new Value[]{}));
        } else {
            property.setValue((Value) null);
        }
        Session nodeSession = node.getSession();
        nodeSession.save();
        // let's reload the node to make sure we don't have any cache issues.
        node = nodeSession.getNodeByIdentifier(node.getIdentifier());
        Calendar newLastModificationDate;
        try {
            newLastModificationDate = node.getProperty(Constants.JCR_LASTMODIFIED).getDate();
        } catch (PathNotFoundException pnfe) {
            newLastModificationDate = null;
        }
        if (!java.util.Objects.equals(newLastModificationDate, originalLastModificationDate)) {
            node.setProperty(Constants.JCR_LASTMODIFIED, originalLastModificationDate);
            nodeSession.save();
        }
    }

    private static void fixInvalidNodeReference(Node node) throws RepositoryException {
        logger.info("Fixing invalid reference by removing node {} from repository...", node.getPath());
        Node parentNode = node.getParent();
        Calendar originalLastModificationDate;
        try {
            originalLastModificationDate = parentNode.getProperty(Constants.JCR_LASTMODIFIED).getDate();
        } catch (PathNotFoundException pnfe) {
            originalLastModificationDate = null;
        }
        Session nodeSession = node.getSession();
        if (!parentNode.isCheckedOut()) {
            nodeSession.getWorkspace().getVersionManager().checkout(parentNode.getPath());
        }
        node.remove();
        nodeSession.save();
        // let's reload the node to make sure we don't have any cache issues.
        parentNode = nodeSession.getNodeByIdentifier(parentNode.getIdentifier());
        Calendar newLastModificationDate;
        try {
            newLastModificationDate = parentNode.getProperty(Constants.JCR_LASTMODIFIED).getDate();
        } catch (PathNotFoundException pnfe) {
            newLastModificationDate = null;
        }
        if (!java.util.Objects.equals(newLastModificationDate, originalLastModificationDate)) {
            parentNode.setProperty(Constants.JCR_LASTMODIFIED, originalLastModificationDate);
            nodeSession.save();
        }
    }

    private static boolean mustRemoveParentNode(Node node) throws RepositoryException {
        for (String nodeTypeToTest : INVALID_REFERENCE_NODE_TYPES_TO_REMOVE) {
            if (node.isNodeType(nodeTypeToTest)) {
                return true;
            }
        }
        return false;
    }

    private static boolean needsToInterrupt(CleanerContext context) {
        if (Boolean.getBoolean(INTERRUPT_MARKER)) {
            logger.info("Interrupting the process");
            System.clearProperty(INTERRUPT_MARKER);
            context.getInterruptionHandler().set(Boolean.TRUE);
            return Boolean.TRUE;
        }
        if (context.getInterruptionHandler().get()) return Boolean.TRUE;
        if (context.getMaxExecutionTimeInMs() <= 0) return Boolean.FALSE;
        if (context.getStartTime() < 0) return Boolean.FALSE;
        return System.currentTimeMillis() >= context.getStartTime() + context.getMaxExecutionTimeInMs();
    }

    private static JCRNodeWrapper getNode(JCRNodeWrapper parent, String relativePath) throws RepositoryException {
        if (StringUtils.isBlank(relativePath) || "/".equals(relativePath)) {
            return parent;
        }
        return parent.getNode(normalizeSubtreePath(relativePath));
    }

    /**
     * Normalises a configured subtree path to a relative path under the version storage root and rejects any
     * parent ({@code ..}) or self ({@code .}) segment, so the destructive scan can never escape
     * {@code /jcr:system/jcr:versionStorage}. Returns the relative path (leading slash stripped).
     */
    static String normalizeSubtreePath(String relativePath) {
        final String normalized = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment) || ".".equals(segment)) {
                throw new IllegalArgumentException("Invalid subtree path: parent or self segments are not allowed");
            }
        }
        return normalized;
    }

    private static String toPrintableName(String versionName) {
        return toPrintableName(versionName, MAX_PRINTABLE_NAME_LENGTH);
    }

    private static String toPrintableName(String versionName, int maxLength) {
        if (versionName == null || versionName.length() <= maxLength) return versionName;
        return String.format("%s ... [full length = %d]", versionName.substring(0, maxLength), versionName.length());
    }

    private static void printDeletionSummary(CleanerContext context) {
        if (context.isDeleteOrphanedVersions() && context.deleteNonOrphanVersions()) {
            logger.info("Deleted: [valid version histories={} / valid versions={}] [orphan version histories={} / orphan versions={}]",
                    context.getDeletedVersionHistoriesCount(), context.getDeletedVersionsCount(),
                    context.getDeletedOrphanVersionHistoriesCount(), context.getDeletedOrphanVersionsCount());
        } else if (context.deleteNonOrphanVersions()) {
            logger.info("Deleted: [valid version histories={} / valid versions={}]",
                    context.getDeletedVersionHistoriesCount(), context.getDeletedVersionsCount());
        } else if (context.isDeleteOrphanedVersions()) {
            logger.info("Deleted: [orphan version histories={} / orphan versions={}]",
                    context.getDeletedOrphanVersionHistoriesCount(), context.getDeletedOrphanVersionsCount());
        }
    }

    private static String toReadableDuration(long start) {
        return DurationFormatUtils.formatDuration(System.currentTimeMillis() - start, HUMAN_READABLE_FORMAT, true);
    }
}
