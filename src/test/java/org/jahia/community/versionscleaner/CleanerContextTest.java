package org.jahia.community.versionscleaner;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link CleanerContext} configuration guards and deletion counters.
 */
public class CleanerContextTest {

    @Test
    public void refreshSessionsDoesNotThrowWhenIntervalIsZero() {
        final CleanerContext context = new CleanerContext().setSessionRefreshInterval(0L);

        // No modulo-by-zero ArithmeticException should escape and abort the scan.
        assertThatCode(context::refreshSessions).doesNotThrowAnyException();
    }

    @Test
    public void refreshSessionsDoesNotThrowWhenIntervalIsNegative() {
        final CleanerContext context = new CleanerContext().setSessionRefreshInterval(-5L);

        assertThatCode(context::refreshSessions).doesNotThrowAnyException();
    }

    @Test
    public void refreshSessionsToleratesNullSessions() {
        // Default interval is 100; processedVersionHistoriesCount starts at 0, so 0 % 100 == 0
        // and the refresh branch is reached even though no sessions were set.
        final CleanerContext context = new CleanerContext();

        assertThatCode(context::refreshSessions).doesNotThrowAnyException();
    }

    @Test
    public void deleteNonOrphanVersionsRequiresNonNegativeKeepCount() {
        assertThat(new CleanerContext().setNbVersionsToKeep(-1L).deleteNonOrphanVersions()).isFalse();
        assertThat(new CleanerContext().setNbVersionsToKeep(0L).deleteNonOrphanVersions()).isTrue();
        assertThat(new CleanerContext().setNbVersionsToKeep(5L).deleteNonOrphanVersions()).isTrue();
    }

    @Test
    public void scanVersionsTreeWhenEitherActionEnabled() {
        assertThat(new CleanerContext().scanVersionsTree()).isFalse();
        assertThat(new CleanerContext().setDeleteOrphanedVersions(true).scanVersionsTree()).isTrue();
        assertThat(new CleanerContext().setNbVersionsToKeep(2L).scanVersionsTree()).isTrue();
    }

    @Test
    public void trackingSeparatesOrphanAndNonOrphanCounters() {
        final CleanerContext context = new CleanerContext();

        context.trackDeletedVersions(3L, false);
        context.trackDeletedVersions(2L, true);
        context.trackDeletedVersionHistory(false);
        context.trackDeletedVersionHistory(true);

        assertThat(context.getDeletedVersionsCount()).isEqualTo(3L);
        assertThat(context.getDeletedOrphanVersionsCount()).isEqualTo(2L);
        assertThat(context.getDeletedVersionHistoriesCount()).isEqualTo(1L);
        assertThat(context.getDeletedOrphanVersionHistoriesCount()).isEqualTo(1L);
    }
}
