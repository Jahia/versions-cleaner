package org.jahia.community.versionscleaner;

import org.junit.Test;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SAFETY tests for the U6 opt-in integrity fix.
 *
 * <p>{@code checkNodeIntegrity} used to be called with {@code fix=true} hardcoded, so a mere
 * "check integrity" run silently nulled dangling references and removed offending nodes. The fix
 * makes the mutation opt-in ({@code fixIntegrity}, report-only by default). These tests pin:
 * <ul>
 *   <li>the {@code fixIntegrity} flag defaults to {@code false} (report-only);</li>
 *   <li>with {@code fix=false}, a node carrying a dangling REFERENCE is detected but NOT mutated —
 *       the reference property is never nulled and the node is never removed.</li>
 * </ul>
 */
public class CleanCommandIntegrityFixTest {

    private static final String DANGLING_UUID = "deadbeef-0000-0000-0000-000000000000";

    @Test
    public void fixIntegrityDefaultsToFalse() {
        assertThat(new CleanerContext().isFixIntegrity()).isFalse();
        assertThat(new CleanerContext().setFixIntegrity(true).isFixIntegrity()).isTrue();
    }

    @Test
    public void reportOnlyRunDetectsButDoesNotMutateADanglingReference() throws Exception {
        // Arrange — a node with a single, single-valued, dangling REFERENCE property.
        final Session session = mock(Session.class);
        final Node node = mock(Node.class);
        final Property property = mock(Property.class);
        final Value value = mock(Value.class);
        final PropertyIterator propIt = mock(PropertyIterator.class);

        when(node.getProperties()).thenReturn(propIt);
        when(propIt.hasNext()).thenReturn(true, false);
        when(propIt.nextProperty()).thenReturn(property);
        when(property.isMultiple()).thenReturn(false);
        when(property.getValue()).thenReturn(value);
        when(property.getPath()).thenReturn("/content/node/reference");
        when(value.getType()).thenReturn(PropertyType.REFERENCE);
        when(value.getString()).thenReturn(DANGLING_UUID);

        // The referenced node cannot be resolved → dangling reference.
        when(session.getNodeByIdentifier(DANGLING_UUID)).thenThrow(new ItemNotFoundException(DANGLING_UUID));

        // isExternalMapping() consults the DB connection; stub the chain to report "no mapping".
        final Connection conn = mock(Connection.class);
        final PreparedStatement ps = mock(PreparedStatement.class);
        final ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        final CleanerContext context = new CleanerContext()
                .setCheckIntegrity(true)   // integrity check is ON
                .setFixIntegrity(false)    // ...but report-only (the fix's safe default)
                .setDbConnection(conn);

        // Act — report-only pass (fix=false), references checked.
        CleanCommand.checkNodeIntegrity(session, node, false, true, context);

        // Assert — the dangling reference was looked up (detected) ...
        verify(session).getNodeByIdentifier(DANGLING_UUID);
        // ... but NOTHING was mutated: property not nulled, node not removed.
        verify(property, never()).setValue((Value) null);
        verify(property, never()).setValue(any(Value[].class));
        verify(node, never()).remove();
    }
}
