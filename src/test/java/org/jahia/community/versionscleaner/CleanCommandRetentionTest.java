package org.jahia.community.versionscleaner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Focused tests for the retention/bounds logic of {@link CleanCommand#retainNewestVersions}.
 *
 * The list is ordered oldest-first (root already excluded). After the call, the list must contain
 * only the versions that are still candidates for deletion (i.e. the oldest ones), with the newest
 * {@code nbVersionsToKeep} entries removed from the candidate list.
 */
public class CleanCommandRetentionTest {

    private static List<String> versions(String... names) {
        return new ArrayList<>(Arrays.asList(names));
    }

    @Test
    public void keepsNewestEntriesAndLeavesOldestAsDeletionCandidates() {
        // Arrange
        final List<String> names = versions("v1", "v2", "v3", "v4", "v5");

        // Act
        CleanCommand.retainNewestVersions(names, 2);

        // Assert - keep v4,v5 (newest two), delete v1,v2,v3
        assertThat(names).containsExactly("v1", "v2", "v3");
    }

    @Test
    public void keepingZeroLeavesAllAsDeletionCandidates() {
        final List<String> names = versions("v1", "v2", "v3");

        CleanCommand.retainNewestVersions(names, 0);

        assertThat(names).containsExactly("v1", "v2", "v3");
    }

    @Test
    public void keepingMoreThanAvailableDeletesNothing() {
        final List<String> names = versions("v1", "v2");

        CleanCommand.retainNewestVersions(names, 5);

        assertThat(names).isEmpty();
    }

    @Test
    public void keepingExactlyAvailableDeletesNothing() {
        final List<String> names = versions("v1", "v2", "v3");

        CleanCommand.retainNewestVersions(names, 3);

        assertThat(names).isEmpty();
    }

    @Test
    public void doesNotThrowWhenKeepCountExceedsSize() {
        final List<String> names = versions("only");

        assertThatCode(() -> CleanCommand.retainNewestVersions(names, Long.MAX_VALUE))
                .doesNotThrowAnyException();
        assertThat(names).isEmpty();
    }

    @Test
    public void doesNotThrowOnEmptyList() {
        final List<String> names = versions();

        assertThatCode(() -> CleanCommand.retainNewestVersions(names, 2))
                .doesNotThrowAnyException();
        assertThat(names).isEmpty();
    }

    @Test
    public void keepsSingleNewestEntry() {
        final List<String> names = versions("v1", "v2", "v3", "v4");

        CleanCommand.retainNewestVersions(names, 1);

        assertThat(names).containsExactly("v1", "v2", "v3");
    }
}
