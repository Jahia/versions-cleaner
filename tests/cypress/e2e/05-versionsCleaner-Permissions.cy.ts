import {DocumentNode} from 'graphql';
import {createUser, deleteUser, grantRoles} from '@jahia/cypress';

/**
 * Regression tests for the fine-grained `versionsCleanerAdmin` permission.
 *
 * These guard against the gate being silently removed or mismatched across the stack:
 *  - Backend: every GraphQL field is annotated `@GraphQLRequiresPermission("versionsCleanerAdmin")`,
 *    enforced as `session.getNode("/").hasPermission("versionsCleanerAdmin")` (root-node ACL check).
 *  - Frontend: `requiredPermission: 'versionsCleanerAdmin'` in register.jsx gates the admin routes.
 *  - RBAC content: the module ships the assignable `versions-cleaner-administrator` role
 *    (src/main/import/roles.xml) granting only `administrationAccess` + `versionsCleanerAdmin`.
 *
 * The "allowed" user is granted that role and nothing else — never `admin` — so the tests prove
 * fine-grained granularity, not merely that a full administrator can pass.
 */
describe('Versions Cleaner — permission enforcement', () => {
    const ROLE_NAME = 'versions-cleaner-administrator';
    const DENIED_USER = 'vcDeniedUser';
    const ALLOWED_USER = 'vcAllowedUser';
    const PASSWORD = 'VcPerm9PwdTest';
    const ADMIN_PATH = '/jahia/administration/versionsCleanerExecution';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const isRunning: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/isRunning.graphql');

    const errorsOf = (result: {graphQLErrors?: Array<{message: string}>; errors?: Array<{message: string}>}) =>
        result.graphQLErrors ?? result.errors ?? [];

    const queryIsRunningAs = (username: string) => {
        cy.apolloClient({username, password: PASSWORD});
        return cy.apollo({query: isRunning});
    };

    before(() => {
        cy.login();
        createUser(DENIED_USER, PASSWORD);
        createUser(ALLOWED_USER, PASSWORD);
        // The annotation resolves the permission on the JCR root node, so grant the
        // module-shipped single-permission role on `/`.
        grantRoles('/', [ROLE_NAME], ALLOWED_USER, 'USER');
    });

    after(() => {
        cy.apolloClient(); // reset the current Apollo client back to root
        cy.login();
        deleteUser(DENIED_USER);
        deleteUser(ALLOWED_USER);
    });

    describe('GraphQL API authorization', () => {
        it('denies the gated query for a user without the permission', () => {
            queryIsRunningAs(DENIED_USER).then((result: never) => {
                const errs = errorsOf(result);
                expect(errs, 'denial errors').to.have.length.greaterThan(0);
                expect(errs.map((e: {message: string}) => e.message).join(' ')).to.contain('Permission denied');
            });
        });

        it('allows the gated query for a user granted only the module permission', () => {
            queryIsRunningAs(ALLOWED_USER).then((result: never) => {
                expect(errorsOf(result), 'should have no errors').to.have.length(0);
                expect((result as {data: {versionsCleanerIsRunning: boolean}}).data.versionsCleanerIsRunning).to.be.a('boolean');
            });
        });
    });

    describe('Admin UI authorization', () => {
        it('hides the admin panel from a user without the permission', () => {
            cy.login(DENIED_USER, PASSWORD);
            cy.visit(ADMIN_PATH, {failOnStatusCode: false});
            cy.contains('h2', 'Versions Cleaner').should('not.exist');
        });

        it('shows the admin panel to a user granted only the module permission', () => {
            cy.login(ALLOWED_USER, PASSWORD);
            cy.visit(ADMIN_PATH);
            cy.contains('h2', 'Versions Cleaner').should('be.visible');
        });
    });
});
