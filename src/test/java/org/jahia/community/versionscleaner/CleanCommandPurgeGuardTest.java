package org.jahia.community.versionscleaner;

import org.apache.jackrabbit.JcrConstants;
import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.version.VersionHistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SAFETY tests for the U2 purge-guard: {@link CleanCommand#hasReferencedVersion(VersionHistory, CleanerContext)}.
 *
 * <p>The force-purge path ({@code deleteOrphanedHistory} → {@code purgeVersions}) used to force-delete
 * a WHOLE orphaned history without any reference check, whereas the per-version trim path skips any
 * version that is still referenced ({@code removeOneVersion}, {@code getReferences().getSize() > 0}).
 * The fix reuses the same reference-count guard before force-purging. These tests pin that predicate:
 * <ul>
 *   <li>a still-referenced version makes the guard trip (→ the purge is SKIPPED, the history retained);</li>
 *   <li>an entirely unreferenced history does not trip the guard (→ purge proceeds, as before);</li>
 *   <li>the implicit {@code jcr:rootVersion} is ignored (it is never a deletion/purge candidate).</li>
 * </ul>
 */
public class CleanCommandPurgeGuardTest {

    private static Node versionNode(String name, long referenceCount) throws RepositoryException {
        final Node version = mock(Node.class);
        when(version.isNodeType(JcrConstants.NT_VERSION)).thenReturn(true);
        when(version.getName()).thenReturn(name);
        final PropertyIterator refs = mock(PropertyIterator.class);
        when(refs.getSize()).thenReturn(referenceCount);
        when(version.getReferences()).thenReturn(refs);
        return version;
    }

    private static VersionHistory historyOf(Node... versions) throws RepositoryException {
        final VersionHistory vh = mock(VersionHistory.class);
        final NodeIterator it = mock(NodeIterator.class);
        final Boolean[] hasNext = new Boolean[versions.length + 1];
        for (int i = 0; i < versions.length; i++) hasNext[i] = Boolean.TRUE;
        hasNext[versions.length] = Boolean.FALSE;
        if (versions.length == 0) {
            when(it.hasNext()).thenReturn(false);
        } else {
            when(it.hasNext()).thenReturn(hasNext[0], java.util.Arrays.copyOfRange(hasNext, 1, hasNext.length));
            when(it.next()).thenReturn(versions[0],
                    (Object[]) java.util.Arrays.copyOfRange(versions, 1, versions.length, Object[].class));
        }
        when(vh.getNodes()).thenReturn(it);
        return vh;
    }

    @Test
    public void tripsWhenAnyVersionIsStillReferenced() throws RepositoryException {
        // Arrange — one referenced version in the (orphaned) history.
        final VersionHistory vh = historyOf(versionNode("1.0", 1L));

        // Act
        final boolean referenced = CleanCommand.hasReferencedVersion(vh, new CleanerContext());

        // Assert — the guard trips, so the caller will SKIP the force-purge (data preserved).
        assertThat(referenced).isTrue();
    }

    @Test
    public void doesNotTripForAFullyUnreferencedHistory() throws RepositoryException {
        // Arrange — two versions, neither referenced.
        final VersionHistory vh = historyOf(versionNode("1.0", 0L), versionNode("1.1", 0L));

        // Act
        final boolean referenced = CleanCommand.hasReferencedVersion(vh, new CleanerContext());

        // Assert — no reference → purge proceeds exactly as before the fix.
        assertThat(referenced).isFalse();
    }

    @Test
    public void ignoresTheImplicitRootVersionEvenIfItReportsReferences() throws RepositoryException {
        // Arrange — only the root version is (spuriously) referenced; it is never a purge candidate.
        final VersionHistory vh = historyOf(versionNode(JcrConstants.JCR_ROOTVERSION, 5L));

        // Act
        final boolean referenced = CleanCommand.hasReferencedVersion(vh, new CleanerContext());

        // Assert
        assertThat(referenced).isFalse();
    }
}
