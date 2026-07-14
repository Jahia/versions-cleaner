package org.jahia.community.versionscleaner;

import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization / tripwire for the Karaf {@code versions-cleaner:run} command authorization surface.
 *
 * <p>Unlike the GraphQL/UI surface — every field of which is gated by
 * {@code @GraphQLRequiresPermission("versionsCleanerAdmin")} — the Karaf {@link CleanCommand} carries
 * <b>no</b> Jahia/GraphQL permission annotation. Authorization on that path is only whatever gates
 * Karaf shell/SSH access. This test documents that asymmetry and trips if a future refactor
 * accidentally adds or removes a permission gate on the shell command, so the change is noticed and
 * the docs/decision (Stage 7) can be revisited.
 */
public class CleanCommandKarafGateTest {

    private static boolean isPermissionAnnotation(Annotation annotation) {
        final String name = annotation.annotationType().getSimpleName();
        return name.contains("RequiresPermission") || name.contains("Permission");
    }

    @Test
    public void karafCommandClassCarriesNoJahiaPermissionAnnotation() {
        final Annotation[] annotations = CleanCommand.class.getAnnotations();

        assertThat(annotations)
                .as("CleanCommand (Karaf @Command) intentionally has no versionsCleanerAdmin gate — "
                        + "if this changes, revisit the documented Karaf/interrupt auth asymmetry")
                .noneMatch(CleanCommandKarafGateTest::isPermissionAnnotation);
    }

    @Test
    public void karafCommandExecuteMethodsCarryNoJahiaPermissionAnnotation() {
        final boolean anyGated = Arrays.stream(CleanCommand.class.getDeclaredMethods())
                .filter(m -> "execute".equals(m.getName()))
                .map(Method::getAnnotations)
                .flatMap(Arrays::stream)
                .anyMatch(CleanCommandKarafGateTest::isPermissionAnnotation);

        assertThat(anyGated)
                .as("execute() entry points are not permission-gated on the Karaf path")
                .isFalse();
    }
}
