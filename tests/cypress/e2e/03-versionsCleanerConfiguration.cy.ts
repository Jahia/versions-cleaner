import {DocumentNode} from 'graphql';

describe('Versions Cleaner - Configuration UI', () => {
    const adminPath = '/jahia/administration/versionsCleanerConfiguration';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getConfig: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getConfig.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const saveConfig: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/saveConfig.graphql');

    before(() => {
        cy.login();
        // Start from a known state: scheduler disabled
        cy.apollo({
            mutation: saveConfig,
            variables: {
                disabled: true,
                cronExpression: '0 30 1 * * ?',
                nbVersionsToKeep: 2,
                deleteOrphanedVersions: false,
                checkIntegrity: false,
                reindexDefaultWorkspace: false,
                maxExecutionTimeInMs: 60000
            }
        });
    });

    after(() => {
        // Restore defaults
        cy.apollo({
            mutation: saveConfig,
            variables: {
                disabled: true,
                cronExpression: '0 30 1 * * ?',
                nbVersionsToKeep: 2,
                deleteOrphanedVersions: false,
                checkIntegrity: false,
                reindexDefaultWorkspace: false,
                maxExecutionTimeInMs: 60000
            }
        });
    });

    // --- Page structure ---

    it('shows the Configuration page title', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('h2', 'Configuration').should('be.visible');
    });

    it('shows the description block', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[class*="vc_description"]').should('be.visible');
    });

    it('shows the restart hint', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[class*="vc_restartHint"]').should('be.visible');
    });

    // --- Form fields ---

    it('shows the Enable scheduled job toggle', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[role="switch"]').should('exist');
    });

    it('shows the Cron expression field', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('#vc-cron').should('be.visible');
    });

    it('shows the Versions to keep field', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('#vc-cfg-nb-versions').should('be.visible');
    });

    it('shows the Max execution time field', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('#vc-cfg-max-time').should('be.visible');
    });

    it('shows the Delete orphaned versions checkbox', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[aria-labelledby="vc-cfg-delete-orphaned-label"]').should('exist');
    });

    it('shows the Check integrity checkbox', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[aria-labelledby="vc-cfg-check-integrity-label"]').should('exist');
    });

    it('shows the Reindex workspace checkbox', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[aria-labelledby="vc-cfg-reindex-label"]').should('exist');
    });

    // --- Save button ---

    it('shows the Save button enabled', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('button', 'Save').should('not.be.disabled');
    });

    // --- Cron expression disabled when scheduler is disabled ---

    it('disables the cron expression field when scheduler is disabled', () => {
        cy.login();
        cy.visit(adminPath);
        // Default state has scheduler disabled → cron field should be disabled
        cy.apollo({query: getConfig})
            .its('data.versionsCleanerConfig.disabled')
            .should('eq', true);
        cy.get('#vc-cron').should('have.attr', 'disabled');
    });

    // --- Save flow ---

    it('shows a success alert after saving configuration', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('button', 'Save').click();
        cy.get('[class*="vc_alert--success"]', {timeout: 10000}).should('be.visible');
    });

    it('persists nbVersionsToKeep change via the UI save button', () => {
        cy.login();
        cy.visit(adminPath);

        // Change the value in the number input
        cy.get('#vc-cfg-nb-versions').clear();
        cy.get('#vc-cfg-nb-versions').type('4');

        cy.contains('button', 'Save').click();
        cy.get('[class*="vc_alert--success"]', {timeout: 10000}).should('be.visible');

        // Verify via GraphQL that the value was actually persisted
        cy.apollo({query: getConfig})
            .its('data.versionsCleanerConfig.nbVersionsToKeep')
            .should('eq', 4);
    });
});
