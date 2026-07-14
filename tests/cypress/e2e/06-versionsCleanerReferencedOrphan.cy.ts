import {DocumentNode} from 'graphql';

/**
 * SAFETY-CRITICAL characterization (U2 / S6) — the headline gap.
 *
 * `deleteOrphanedHistory` reaches `InternalVersionManagerImpl.purgeVersions(...)` on the whole
 * history with NO reference-count guard (CleanCommand.java:320-334), unlike the trim path
 * `removeOneVersion` which skips any version with `getReferences().getSize() > 0` (:470-474).
 * So an orphaned history (frozen UUID absent from edit+live) whose frozen versions are STILL
 * referenced elsewhere in version storage is force-purged. The existing correctness spec (C23)
 * only ever deletes an *unreferenced* orphan, so this risk is untested.
 *
 * This spec seeds a referenced-but-orphaned history via the `createReferencedOrphanHistory`
 * helper (node B holds a hard `vc:hardRef` REFERENCE to node A, both checked in, then A removed)
 * and asserts the CURRENT (risky) behaviour: the still-referenced orphan history IS purged
 * (`historyExists === false`). That is intentional RED evidence for the Stage-7 fix, after which
 * the expectation flips to `historyExists === true`.
 *
 * STAGE-6 SPIKE / WRITE-BUT-SKIP GATE (make-or-break, see 04-gaps.md §4):
 * The whole premise depends on the frozen REFERENCE surviving into version storage as a *counted
 * hard reference*. The pre-run assertion below (`historyExists === true`) proves the fixture was
 * seeded; if the seeding helper returns null or the pre-condition fails on this Jahia/Oak build,
 * mark this spec `describe.skip` with the exact reason ("frozen REFERENCE not retained as a counted
 * hard reference in version storage on this build; U2 force-purge cannot be demonstrated e2e") —
 * do NOT weaken the assertion to make it pass. The trim-path contrast (a referenced version
 * survives) is covered by 04-versionsCleanerCorrectness "current/base version survives".
 */
describe('Versions Cleaner - Referenced Orphan Force-Purge (U2)', () => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const isRunning: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/isRunning.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const runCleaner: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/runCleaner.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const createReferencedOrphanHistory: DocumentNode =
        require('graphql-tag/loader!../fixtures/graphql/mutation/createReferencedOrphanHistory.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const deleteTestNode: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/deleteTestNode.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const historyExists: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/historyExists.graphql');

    const NODE_NAME = 'referenced-orphan';

    const waitForIdle = () =>
        cy.waitUntil(
            () => cy.apollo({query: isRunning}).its('data.versionsCleaner.isRunning').then(v => v === false),
            {timeout: 60000, interval: 1000}
        );

    before(() => {
        cy.login();
    });

    after(() => {
        // Node A was already removed by the helper; clean up the referencing node B.
        cy.apollo({mutation: deleteTestNode, variables: {name: NODE_NAME}});
        cy.apollo({mutation: deleteTestNode, variables: {name: `${NODE_NAME}-ref`}});
    });

    it('force-purges a still-referenced orphaned history (current behaviour, pre-fix)', () => {
        cy.apollo({mutation: createReferencedOrphanHistory, variables: {name: NODE_NAME}})
            .its('data.versionsCleaner.createReferencedOrphanHistory')
            .should('be.a', 'string')
            .then(historyId => {
                // Fixture pre-condition: A's history was seeded and still exists (orphaned but referenced).
                // If this fails, the frozen hard reference was not retained — see the WRITE-BUT-SKIP gate above.
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

                // CURRENT (risky) behaviour: the still-referenced orphan was force-purged.
                // Stage-7 fix flips this expectation to `eq true`.
                cy.apollo({query: historyExists, variables: {historyId}})
                    .its('data.versionsCleaner.historyExists')
                    .should('eq', false);
            });
    });
});
