package org.jahia.community.versionscleaner;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CleanCommand#normalizeSubtreePath} which guards the destructive scan against
 * escaping the version-storage root via parent/self path segments.
 */
public class CleanCommandPathTest {

    @Test
    public void stripsLeadingSlash() {
        assertThat(CleanCommand.normalizeSubtreePath("/a/b/c")).isEqualTo("a/b/c");
    }

    @Test
    public void leavesRelativePathUnchanged() {
        assertThat(CleanCommand.normalizeSubtreePath("a/b/c")).isEqualTo("a/b/c");
    }

    @Test
    public void rejectsParentTraversalSegment() {
        assertThatThrownBy(() -> CleanCommand.normalizeSubtreePath("a/../b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parent or self");
    }

    @Test
    public void rejectsLeadingParentTraversalSegment() {
        assertThatThrownBy(() -> CleanCommand.normalizeSubtreePath("/../etc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejectsSelfSegment() {
        assertThatThrownBy(() -> CleanCommand.normalizeSubtreePath("a/./b"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
