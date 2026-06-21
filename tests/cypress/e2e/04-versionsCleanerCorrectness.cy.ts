import {DocumentNode} from 'graphql';

describe('Versions Cleaner - Semantic Correctness', () => {
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
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const historyExists: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/historyExists.graphql');

    const waitForIdle = () =>
        cy.waitUntil(
            () => cy.apollo({query: isRunning}).its('data.versionsCleaner.isRunning').then(v => v === false),
            {timeout: 60000, interval: 1000}
        );

    before(() => {
        cy.login();
    });

    after(() => {
        // Best-effort cleanup in case a test failed before its own teardown
        cy.apollo({mutation: deleteTestNode, variables: {name: 'correctness-keep'}});
        cy.apollo({mutation: deleteTestNode, variables: {name: 'correctness-orphan'}});
    });

    it('keeps exactly N versions after a run with nbVersionsToKeep=N', () => {
        const nodeName = 'correctness-keep';
        const nodePath = `/sites/systemsite/contents/vc-test-${nodeName}`;

        cy.apollo({mutation: createTestVersions, variables: {name: nodeName, versionCount: 5}})
            .its('data.versionsCleaner.createTestVersions')
            .should('be.a', 'string');

        waitForIdle();

        cy.apollo({
            mutation: runCleaner,
            variables: {
                nbVersionsToKeep: 2,
                deleteOrphanedVersions: false,
                checkIntegrity: false,
                reindexDefaultWorkspace: false,
                maxExecutionTimeInMs: 0,
                pauseDuration: 0,
                forceRestartFromBeginning: true
            }
        }).its('data.versionsCleaner.run').should('eq', true);

        waitForIdle();

        cy.apollo({query: getVersionCount, variables: {nodePath}})
            .its('data.versionsCleaner.versionCount')
            .should('eq', 2);

        cy.apollo({mutation: deleteTestNode, variables: {name: nodeName}});
    });

    it('deletes orphaned version histories when deleteOrphanedVersions is true', () => {
        const nodeName = 'correctness-orphan';

        cy.apollo({mutation: createTestVersions, variables: {name: nodeName, versionCount: 2}})
            .its('data.versionsCleaner.createTestVersions')
            .then(historyId => {
                cy.apollo({mutation: deleteTestNode, variables: {name: nodeName}});

                cy.apollo({query: historyExists, variables: {historyId}})
                    .its('data.versionsCleaner.historyExists')
                    .should('eq', true);

                waitForIdle();

                cy.apollo({
                    mutation: runCleaner,
                    variables: {
                        nbVersionsToKeep: -1,
                        deleteOrphanedVersions: true,
                        checkIntegrity: false,
                        reindexDefaultWorkspace: false,
                        maxExecutionTimeInMs: 0,
                        pauseDuration: 0,
                        forceRestartFromBeginning: true
                    }
                }).its('data.versionsCleaner.run').should('eq', true);

                waitForIdle();

                cy.apollo({query: historyExists, variables: {historyId}})
                    .its('data.versionsCleaner.historyExists')
                    .should('eq', false);
            });
    });
});
