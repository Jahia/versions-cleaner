package org.jahia.community.versionscleaner;

import org.jahia.api.Constants;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link CleanCommand#scheduleReindexing(CleanCommand.ReindexScheduler, boolean)}.
 *
 * <p>{@code JahiaRepositoryImpl.scheduleReindexing(String)} always forces a consistency check of the search index,
 * which can take much longer than the reindexing itself on a large repository (issue #108). The default workspace
 * must be reindexed through the {@code (String, boolean)} overload, with the check only when asked for.
 */
public class CleanCommandReindexTest {

    @Test
    public void reindexesDefaultWorkspaceWithoutConsistencyCheckWhenNotForced() throws Exception {
        final CleanCommand.ReindexScheduler scheduler = mock(CleanCommand.ReindexScheduler.class);

        CleanCommand.scheduleReindexing(scheduler, false);

        verify(scheduler).scheduleReindexing(Constants.EDIT_WORKSPACE, false);
        verifyNoMoreInteractions(scheduler);
    }

    @Test
    public void reindexesDefaultWorkspaceWithConsistencyCheckWhenForced() throws Exception {
        final CleanCommand.ReindexScheduler scheduler = mock(CleanCommand.ReindexScheduler.class);

        CleanCommand.scheduleReindexing(scheduler, true);

        verify(scheduler).scheduleReindexing(Constants.EDIT_WORKSPACE, true);
        verifyNoMoreInteractions(scheduler);
    }
}
