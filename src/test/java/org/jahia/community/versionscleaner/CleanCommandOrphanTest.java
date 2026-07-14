package org.jahia.community.versionscleaner;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.Test;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression / safety tests for {@link CleanCommand#isUuidOrphaned(String, CleanerContext)}.
 *
 * <p>This is the core data-loss guard of the module: a version history is only considered orphaned
 * (and therefore eligible for a force-purge that ignores references) when its frozen UUID is absent
 * from <em>both</em> the edit and the live workspaces. The tests pin:
 * <ul>
 *   <li>present in edit → not orphaned, live is never consulted (short-circuit);</li>
 *   <li>absent in edit but present in live → not orphaned;</li>
 *   <li>absent in both → orphaned;</li>
 *   <li>a generic {@link RepositoryException} (e.g. a transient JCR error) must <b>propagate</b> and
 *       must NOT be silently interpreted as "orphaned" — otherwise a transient failure could turn
 *       into a false-orphan deletion. This is the key data-loss regression guard.</li>
 * </ul>
 */
public class CleanCommandOrphanTest {

    private static final String UUID = "11111111-1111-1111-1111-111111111111";

    private static CleanerContext contextWith(JCRSessionWrapper edit, JCRSessionWrapper live) {
        return new CleanerContext().setEditSession(edit).setLiveSession(live);
    }

    @Test
    public void notOrphanedWhenPresentInEditAndLiveIsNeverQueried() throws RepositoryException {
        // Arrange
        final JCRSessionWrapper edit = mock(JCRSessionWrapper.class);
        final JCRSessionWrapper live = mock(JCRSessionWrapper.class);
        when(edit.getNodeByIdentifier(UUID)).thenReturn(mock(JCRNodeWrapper.class));

        // Act
        final boolean orphaned = CleanCommand.isUuidOrphaned(UUID, contextWith(edit, live));

        // Assert
        assertThat(orphaned).isFalse();
        verify(live, never()).getNodeByIdentifier(UUID);
    }

    @Test
    public void notOrphanedWhenAbsentInEditButPresentInLive() throws RepositoryException {
        // Arrange
        final JCRSessionWrapper edit = mock(JCRSessionWrapper.class);
        final JCRSessionWrapper live = mock(JCRSessionWrapper.class);
        when(edit.getNodeByIdentifier(UUID)).thenThrow(new ItemNotFoundException(UUID));
        when(live.getNodeByIdentifier(UUID)).thenReturn(mock(JCRNodeWrapper.class));

        // Act
        final boolean orphaned = CleanCommand.isUuidOrphaned(UUID, contextWith(edit, live));

        // Assert
        assertThat(orphaned).isFalse();
    }

    @Test
    public void orphanedOnlyWhenAbsentInBothWorkspaces() throws RepositoryException {
        // Arrange
        final JCRSessionWrapper edit = mock(JCRSessionWrapper.class);
        final JCRSessionWrapper live = mock(JCRSessionWrapper.class);
        when(edit.getNodeByIdentifier(UUID)).thenThrow(new ItemNotFoundException(UUID));
        when(live.getNodeByIdentifier(UUID)).thenThrow(new ItemNotFoundException(UUID));

        // Act
        final boolean orphaned = CleanCommand.isUuidOrphaned(UUID, contextWith(edit, live));

        // Assert
        assertThat(orphaned).isTrue();
    }

    @Test
    public void genericRepositoryExceptionPropagatesAndIsNotTreatedAsOrphaned() throws RepositoryException {
        // Arrange — a transient/unexpected JCR error, NOT an ItemNotFoundException.
        final JCRSessionWrapper edit = mock(JCRSessionWrapper.class);
        final JCRSessionWrapper live = mock(JCRSessionWrapper.class);
        when(edit.getNodeByIdentifier(UUID)).thenThrow(new RepositoryException("transient JCR failure"));

        // Act + Assert — the exception must escape (fail-safe abort), never a false-orphan verdict.
        assertThatThrownBy(() -> CleanCommand.isUuidOrphaned(UUID, contextWith(edit, live)))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("transient JCR failure");
        verify(live, never()).getNodeByIdentifier(UUID);
    }
}
