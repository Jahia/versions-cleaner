package org.jahia.community.versionscleaner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;

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
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.impl.jackrabbit.SpringJackrabbitRepository;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.DatabaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "versions-cleaner", name = "keep-n", description = "Delete all version except the last N versions")
@Service
public class CleanCommand implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanCommand.class);
    private static final String HUMAN_READABLE_FORMAT = "d' days 'H' hours 'm' minutes 's' seconds'";
    private static final String VERSIONS_PATH = "/jcr:system/jcr:versionStorage";
    private static final String[] INVALID_REFERENCE_NODE_TYPES_TO_REMOVE = new String[]{
            JcrConstants.NT_HIERARCHYNODE,
            Constants.JAHIANT_MEMBER,
            "jnt:reference"
    };
    private static final String INTERRUPT_MARKER = "versions-cleaner.interrupt";

    @Option(name = "-r", aliases = "--reindex-default-workspace", description = "Reindex default workspace before cleaning")
    private Boolean reindexDefaultWorkspace = Boolean.FALSE;

    @Option(name = "-c", aliases = "--check-integrity", description = "Check integrity of the versions")
    private Boolean checkIntegrity = Boolean.FALSE;

    @Option(name = "-n", aliases = "--nb-versions-to-keep", description = "Number of versions to keep")
    private Long nbVersionsToKeep = 2L;

    @Option(name = "-t", aliases = "--max-execution-time-in-ms", description = "Max execution time in ms")
    private Long maxExecutionTimeInMs = 0L;

    @Option(name = "-o", aliases = "--delete-orphaned-versions", description = "Delete orphaned versions")
    private Boolean deleteOrphanedVersions = Boolean.FALSE;

    @Override
    public Object execute() throws RepositoryException {
        deleteVersions(reindexDefaultWorkspace, checkIntegrity, nbVersionsToKeep, maxExecutionTimeInMs, deleteOrphanedVersions);
        return null;
    }

    public static void deleteVersions(Boolean reindexDefaultWorkspace, Boolean checkIntegrity, Long nbVersionsToKeep, Long maxExecutionTimeInMs, Boolean deleteOrphanedVersions) throws RepositoryException {
        deleteVersions(reindexDefaultWorkspace, checkIntegrity, nbVersionsToKeep, maxExecutionTimeInMs, deleteOrphanedVersions, new AtomicBoolean());
    }

    private static void deleteVersions(Boolean reindexDefaultWorkspace, Boolean checkIntegrity, Long nbVersionsToKeep, Long maxExecutionTimeInMs, Boolean deleteOrphanedVersions, AtomicBoolean interruptionHandler) throws RepositoryException {
        if (SettingsBean.getInstance().isProcessingServer()) {

            if (needsToInterrupt(interruptionHandler)) return;
            if (reindexDefaultWorkspace) {
                final long start = System.currentTimeMillis();
                LOGGER.info("Starting reindexing of default workspace");
                ((JahiaRepositoryImpl) ((SpringJackrabbitRepository) JCRSessionFactory.getInstance().getDefaultProvider().getRepository()).getRepository()).scheduleReindexing(Constants.EDIT_WORKSPACE);
                final long end = System.currentTimeMillis();
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(String.format("Finished reindexing default workspace in %s", DurationFormatUtils.formatDuration(end - start, HUMAN_READABLE_FORMAT, true)));
                }
            }

            if (needsToInterrupt(interruptionHandler)) return;
            final long start = System.currentTimeMillis();
            long end;
            if (nbVersionsToKeep >= 0) {
                LOGGER.info("Starting to delete versions");
                final Long deletedVersions = JCRTemplate.getInstance().doExecuteWithSystemSession((JCRSessionWrapper session) -> deleteVersions(session, VERSIONS_PATH, checkIntegrity, nbVersionsToKeep, start, maxExecutionTimeInMs, interruptionHandler));
                end = System.currentTimeMillis();
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(String.format("Finished to delete %d versions in %s", deletedVersions, DurationFormatUtils.formatDuration(end - start, HUMAN_READABLE_FORMAT, true)));
                }
            }

            if (deleteOrphanedVersions) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Starting to delete orphaned versions");
                }
                final long orphanStart = System.currentTimeMillis();
                final Long deletedOrphanedVersions = JCRTemplate.getInstance().doExecuteWithSystemSession((JCRSessionWrapper session) -> deleteOrphanedVersions(session, session.getNode(VERSIONS_PATH), start, maxExecutionTimeInMs, interruptionHandler));
                end = System.currentTimeMillis();
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(String.format("Finished to delete %d orphaned versions in %s", deletedOrphanedVersions, DurationFormatUtils.formatDuration(end - orphanStart, HUMAN_READABLE_FORMAT, true)));
                }
            }
        } else if (LOGGER.isInfoEnabled()) {
            LOGGER.info("This command can only be executed on the processing server");
        }
    }

    private static Long deleteOrphanedVersions(JCRSessionWrapper session, JCRNodeWrapper startNode, long start, Long maxExecutionTimeInMs, AtomicBoolean interruptionHandler) {
        long deletedVersions = 0L;
        if (canContinue(start, maxExecutionTimeInMs, interruptionHandler)) {
            try {
                if (startNode.isNodeType(JcrConstants.NT_VERSIONHISTORY) && checkAndDeleteOrphanedVersionHistory(startNode, session)) {
                    deletedVersions++;
                    return deletedVersions;
                }
                if (startNode.hasNodes()) {
                    final JCRNodeIteratorWrapper it = startNode.getNodes();
                    while (it.hasNext() && canContinue(start, maxExecutionTimeInMs, interruptionHandler)) {
                        JCRNodeWrapper node = (JCRNodeWrapper) it.next();
                        deletedVersions = deletedVersions + deleteOrphanedVersions(session, node, start, maxExecutionTimeInMs, interruptionHandler);
                    }
                }
            } catch (Exception ex) {
                LOGGER.error(ex.getMessage(), ex);
            }
        }
        return deletedVersions;
    }

    private static boolean checkAndDeleteOrphanedVersionHistory(JCRNodeWrapper versionHistory, JCRSessionWrapper session) throws RepositoryException {
        JCRNodeIteratorWrapper it = versionHistory.getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper node = (JCRNodeWrapper) it.next();
            if (node.isNodeType(JcrConstants.NT_VERSION) && node.hasNode(JcrConstants.JCR_FROZENNODE)) {
                JCRNodeWrapper frozen = node.getNode(JcrConstants.JCR_FROZENNODE);
                if (frozen.hasProperty(JcrConstants.JCR_FROZENUUID)) {
                    try {
                        session.getNodeByIdentifier(frozen.getPropertyAsString(JcrConstants.JCR_FROZENUUID));
                    } catch (ItemNotFoundException ex) {
                        return deleteOrphaned((VersionHistory) versionHistory, session);
                    }
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean deleteOrphaned(VersionHistory vh, JCRSessionWrapper session) throws RepositoryException {
        final NodeId id = NodeId.valueOf(vh.getIdentifier());
        final SessionImpl providerSession = (SessionImpl) session.getProviderSession(session.getNode("/").getProvider());
        final InternalVersionManager vm = providerSession.getInternalVersionManager();
        final List<InternalVersionHistory> unusedVersions = new ArrayList<>();
        unusedVersions.add(vm.getVersionHistory(id));
        int[] results = {0, 0};
        if (vm instanceof InternalVersionManagerImpl) {
            results = ((InternalVersionManagerImpl) vm).purgeVersions(providerSession, unusedVersions);
        } else if (vm instanceof InternalXAVersionManager) {
            results = ((InternalXAVersionManager) vm).purgeVersions(providerSession, unusedVersions);
        }
        return results[0] + results[1] > 0;
    }

    private static Long deleteVersions(JCRSessionWrapper session, String rootPath, Boolean checkIntegrity, Long nbVersionsToKeep, long start, Long maxExecutionTimeInMs, AtomicBoolean interruptionHandler) throws RepositoryException {
        long deletedVersions = 0L;
        if (canContinue(start, maxExecutionTimeInMs, interruptionHandler)) {
            final JCRNodeWrapper rootNodeWrapper = session.getNode(rootPath, false);
            final JCRNodeIteratorWrapper childNodeIterator = rootNodeWrapper.getNodes();
            while (childNodeIterator.hasNext() && canContinue(start, maxExecutionTimeInMs, interruptionHandler)) {
                final JCRNodeWrapper childNode = (JCRNodeWrapper) childNodeIterator.next();
                long newDeletedVersions;
                if (childNode.getNodeTypes().contains(JcrConstants.NT_VERSIONHISTORY)) {
                    if (checkIntegrity) {
                        processNode(session, childNode, true, true);
                    }
                    newDeletedVersions = keepLastNVersions((VersionHistory) childNode, nbVersionsToKeep, session);
                } else {
                    newDeletedVersions = deleteVersions(session, childNode.getPath(), checkIntegrity, nbVersionsToKeep, start, maxExecutionTimeInMs, interruptionHandler);
                }
                if (newDeletedVersions > 0L) {
                    deletedVersions = deletedVersions + newDeletedVersions;
                }
            }
            session.refresh(false);
        }
        return deletedVersions;
    }

    private static long keepLastNVersions(VersionHistory vh, Long nbVersionsToKeep, JCRSessionWrapper session) {
        long deletedVersions = 0L;

        try {
            final VersionIterator versionIterator = vh.getAllVersions();
            final Long nbVersions = versionIterator.getSize();
            if (nbVersions > nbVersionsToKeep) {
                final List<String> unusedVersionsName = new ArrayList<>();
                final long maxPosition = nbVersions - nbVersionsToKeep;
                while (versionIterator.hasNext() && versionIterator.getPosition() < maxPosition) {
                    final Version version = versionIterator.nextVersion();
                    processNode(session, version, true, true);
                    final String versionName = version.getName();
                    if (version.getReferences().getSize() == 0 && !JcrConstants.JCR_ROOTVERSION.equals(versionName)) {
                        unusedVersionsName.add(versionName);
                    }
                }
                for (String unusedVersionName : unusedVersionsName) {
                    vh.removeVersion(unusedVersionName);
                    deletedVersions++;
                }
            }
        } catch (Exception ex) {
            LOGGER.info("Exception when trying to remove a version", ex);
        }
        return deletedVersions;
    }

    private static void processNode(Session session, Node node,
                                    boolean fix, boolean referencesCheck) throws RepositoryException {
        try {
            if (fix || referencesCheck) {
                PropertyIterator propertyIterator = node.getProperties();
                while (propertyIterator.hasNext()) {
                    Property property = propertyIterator.nextProperty();
                    if (property.isMultiple()) {
                        try {
                            Value[] values = property.getValues();
                            for (Value value : values) {
                                if (!processPropertyValue(session, node, property, value, fix, referencesCheck)) {
                                    return;
                                }
                            }
                        } catch (ConstraintViolationException ex) {
                            //Definition was changed, property is missing
                            LOGGER.warn(String.format("Warning: Property definition for node %s is missing", node.getPath()), ex);
                        }
                    } else {
                        if (!processPropertyValue(session, node, property, property.getValue(), fix, referencesCheck)) {
                            return;
                        }
                    }
                }
            }

        } catch (RepositoryException ex) {
            LOGGER.warn(String.format("Exception while processing node %s", node.getPath()), ex);
        }
    }

    private static boolean processPropertyValue(Session session, Node node, Property property, Value propertyValue,
                                                boolean fix, boolean referencesCheck) throws RepositoryException {
        int propertyType = propertyValue.getType();
        switch (propertyType) {
            case PropertyType.REFERENCE:
            case PropertyType.WEAKREFERENCE:
                if (!referencesCheck) {
                    break;
                }
                String uuid = propertyValue.getString();
                try {
                    session.getNodeByIdentifier(uuid);
                } catch (ItemNotFoundException infe) {
                    Connection conn = null;
                    PreparedStatement statement = null;
                    ResultSet resultSet = null;
                    try {
                        conn = DatabaseUtils.getDatasource().getConnection();
                        statement = conn.prepareStatement("select * from jahia_external_mapping where internalUuid=?");
                        statement.setString(1, uuid);
                        resultSet = statement.executeQuery();
                        if (resultSet.next()) {
                            LOGGER.info(String.format("Mapping found towards %s, this reference is not available at this time (referenced from property %s), please check your mount points and/or external providers", resultSet.getString("externalId"), property.getPath()));
                            if (fix) {
                                LOGGER.info("It will not be fixed automatically");
                            }
                            break;
                        }
                    } catch (SQLException throwables) {
                        //uuid is not an external reference
                    } finally {
                        DatabaseUtils.closeQuietly(resultSet);
                        DatabaseUtils.closeQuietly(statement);
                        DatabaseUtils.closeQuietly(conn);
                    }
                    LOGGER.info(String.format("Couldn't find referenced node with UUID %s referenced from property %s", uuid, property.getPath()));
                    if (fix) {
                        if (mustRemoveParentNode(node)) {
                            fixInvalidNodeReference(node);
                            return false;
                        } else {
                            fixInvalidPropertyReference(node, property, uuid);
                        }
                    }
                }
                break;
            default:
        }
        return true;
    }

    private static void fixInvalidPropertyReference(Node node, Property property, String uuid) throws RepositoryException {
        LOGGER.info(String.format("Fixing invalid reference by setting reference property %s to null...", property.getPath()));
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
        if (newLastModificationDate == null && originalLastModificationDate == null) {
            // do nothing, they are equal
        } else if (((newLastModificationDate != null) && (originalLastModificationDate == null))
                || ((newLastModificationDate == null) && (originalLastModificationDate != null))
                || (!newLastModificationDate.equals(originalLastModificationDate))) {
            node.setProperty(Constants.JCR_LASTMODIFIED, originalLastModificationDate);
            nodeSession.save();
        }
    }

    private static void fixInvalidNodeReference(Node node) throws RepositoryException {
        LOGGER.info(String.format("Fixing invalid reference by removing node %s from repository...", node.getPath()));
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
        if (newLastModificationDate == null && originalLastModificationDate == null) {
            // do nothing, they are equal
        } else if (((newLastModificationDate != null) && (originalLastModificationDate == null))
                || ((newLastModificationDate == null) && (originalLastModificationDate != null))
                || (!newLastModificationDate.equals(originalLastModificationDate))) {
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

    private static boolean canContinue(long start, Long maxExecutionTimeInMs, AtomicBoolean interruptionHandler) {
        if (needsToInterrupt(interruptionHandler)) return false;
        return maxExecutionTimeInMs == 0 || System.currentTimeMillis() < start + maxExecutionTimeInMs;
    }

    private static boolean needsToInterrupt(AtomicBoolean interruptionHandler) {
        if (Boolean.getBoolean(INTERRUPT_MARKER)) {
            LOGGER.info("Interrupting the process");
            System.clearProperty(INTERRUPT_MARKER);
            interruptionHandler.set(true);
        }
        return interruptionHandler.get();
    }
}
