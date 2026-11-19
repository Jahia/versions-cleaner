package org.jahia.community.versionscleaner;

import org.junit.Test;

import java.util.Hashtable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@code forceConsistencyCheck} property of {@link VersionsCleanerConfig}.
 */
public class VersionsCleanerConfigTest {

    @Test
    public void consistencyCheckIsNotForcedByDefault() throws Exception {
        final VersionsCleanerConfig config = new VersionsCleanerConfig();

        config.updated(new Hashtable<>());

        assertThat(config.isForceConsistencyCheck()).isFalse();
    }

    @Test
    public void consistencyCheckCanBeForcedFromConfiguration() throws Exception {
        final VersionsCleanerConfig config = new VersionsCleanerConfig();
        final Hashtable<String, Object> props = new Hashtable<>();
        props.put("forceConsistencyCheck", "true");

        config.updated(props);

        assertThat(config.isForceConsistencyCheck()).isTrue();
    }
}
