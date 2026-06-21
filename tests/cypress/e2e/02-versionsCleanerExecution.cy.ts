import {DocumentNode} from 'graphql';

describe('Versions Cleaner - Execution UI', () => {
    const adminPath = '/jahia/administration/versionsCleanerExecution';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const isRunning: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/isRunning.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const setStartupDelay: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/setStartupDelay.graphql');

    before(() => {
        cy.login();
    });

    // --- Page structure ---

    it('shows the page title', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('h2', 'Versions Cleaner').should('be.visible');
    });

    it('shows the description block', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[class*="vc_description"]').should('be.visible');
    });

    // --- Form fields ---

    it('shows the Versions to keep field', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('#vc-nb-versions').should('be.visible');
    });

    it('shows the Max execution time field', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('#vc-max-time').should('be.visible');
    });

    it('shows the Pause between deletions field', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('#vc-pause').should('be.visible');
    });

    it('shows the Subtree path field', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('#vc-subtree').should('be.visible');
    });

    it('shows the Delete orphaned versions checkbox', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[aria-labelledby="vc-delete-orphaned-label"]').should('exist');
    });

    it('shows the Check integrity checkbox', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[aria-labelledby="vc-check-integrity-label"]').should('exist');
    });

    it('shows the Reindex workspace checkbox', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[aria-labelledby="vc-reindex-label"]').should('exist');
    });

    it('shows the Force restart checkbox', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[aria-labelledby="vc-force-restart-label"]').should('exist');
    });

    // --- Run button ---

    it('shows the Run cleaner button enabled', () => {
        cy.login();
        cy.visit(adminPath);
        // Wait for any in-progress run from API tests to complete
        cy.waitUntil(
            () => cy.apollo({query: isRunning}).its('data.versionsCleaner.isRunning').then(v => v === false),
            {timeout: 30000, interval: 1000}
        );
        cy.contains('button', 'Run cleaner').should('not.be.disabled');
    });

    // --- Run flow ---

    it('shows a success alert after clicking Run cleaner', () => {
        cy.login();
        cy.visit(adminPath);
        cy.waitUntil(
            () => cy.apollo({query: isRunning}).its('data.versionsCleaner.isRunning').then(v => v === false),
            {timeout: 30000, interval: 1000}
        );
        cy.contains('button', 'Run cleaner').click();
        cy.get('[class*="vc_alert--success"]', {timeout: 10000}).should('be.visible');
    });

    it('disables the Run button while clean is in progress', () => {
        cy.login();
        cy.waitUntil(
            () => cy.apollo({query: isRunning}).its('data.versionsCleaner.isRunning').then(v => v === false),
            {timeout: 30000, interval: 1000}
        );
        // Arm a 5-second startup delay so the job stays running long enough to assert the UI state,
        // even on an empty repository where there are no versions to delete.
        cy.apollo({mutation: setStartupDelay, variables: {delayMs: 5000}});
        cy.visit(adminPath);
        cy.contains('button', 'Run cleaner').click();
        // Button label changes and becomes disabled while running
        cy.contains('button', 'Clean in progress').should('be.disabled');
        // Clear the delay after the test so subsequent runs are not affected.
        cy.apollo({mutation: setStartupDelay, variables: {delayMs: 0}});
    });
});
