import {DocumentNode} from 'graphql';

/**
 * SAFETY-CRITICAL (U6 / S8) — first automated coverage of a run with `checkIntegrity=true`.
 *
 * `checkNodeIntegrity` is called for every `nt:versionHistory` node with `fix=true` hardcoded
 * (CleanCommand.java:248): there is no report-only mode. Dangling REFERENCE properties get nulled
 * (`fixInvalidPropertyReference`) and certain node types get removed (`fixInvalidNodeReference`).
 * No existing spec ever sets `checkIntegrity=true` (every run passes `checkIntegrity: false`), so
 * the entire integrity-fix path is unexercised.
 *
 * This spec provides the smoke coverage that was missing: a run with `checkIntegrity=true`
 * completes and still performs a correct trim (proving the integrity pass runs alongside deletion
 * without corrupting a healthy history).
 *
 * DEFERRED to a later stage (harder fixture, see 04-gaps.md §4 / G2): the stronger characterization
 * that a dangling reference on a version-history-scoped node is silently nulled after the run
 * (and left intact when `checkIntegrity=false`). That requires seeding a dangling REFERENCE on an
 * `nt:versionHistory` child (the `:245` gate), which is not yet supported by a test helper. When
 * added, it drives the Stage-7 decision to make `fix` opt-in / add a report-only mode.
 */
describe('Versions Cleaner - Integrity check run (U6)', () => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const isRunning: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/isRunning.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const runCleaner: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/runCleaner.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const createTestVersions: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/createTestVersions.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const deleteTestNode: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/deleteTestNode.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getVersionCount: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getVersionCount.graphql');

    const NODE_NAME = 'integrity-smoke';
    const NODE_PATH = `/sites/systemsite/contents/vc-test-${NODE_NAME}`;

    const waitForIdle = () =>
        cy.waitUntil(
            () => cy.apollo({query: isRunning}).its('data.versionsCleaner.isRunning').then(v => v === false),
            {timeout: 60000, interval: 1000}
        );

    before(() => {
        cy.login();
    });

    after(() => {
        cy.apollo({mutation: deleteTestNode, variables: {name: NODE_NAME}});
    });

    it('completes a run with checkIntegrity=true and still trims a healthy history', () => {
        cy.apollo({mutation: createTestVersions, variables: {name: NODE_NAME, versionCount: 5}})
            .its('data.versionsCleaner.createTestVersions')
            .should('be.a', 'string');

        waitForIdle();

        cy.apollo({
            mutation: runCleaner,
            variables: {
                nbVersionsToKeep: 2,
                deleteOrphanedVersions: false,
                checkIntegrity: true,
                reindexDefaultWorkspace: false,
                maxExecutionTimeInMs: 0,
                pauseDuration: 0,
                forceRestartFromBeginning: true
            }
        }).its('data.versionsCleaner.run').should('eq', true);

        waitForIdle();

        cy.apollo({query: getVersionCount, variables: {nodePath: NODE_PATH}})
            .its('data.versionsCleaner.versionCount')
            .should('eq', 2);
    });
});
