import {DocumentNode} from 'graphql';

describe('Versions Cleaner - GraphQL API', () => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const isRunning: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/isRunning.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getConfig: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getConfig.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const saveConfig: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/saveConfig.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const runCleaner: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/runCleaner.graphql');

    before(() => {
        cy.login();
    });

    after(() => {
        // Restore defaults so other test files start from a known state
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

    // --- versionsCleaner.isRunning ---

    describe('versionsCleaner.isRunning', () => {
        it('returns a boolean', () => {
            cy.apollo({query: isRunning})
                .its('data.versionsCleaner.isRunning')
                .should('be.a', 'boolean');
        });

        it('returns false when no clean is in progress', () => {
            cy.apollo({query: isRunning})
                .its('data.versionsCleaner.isRunning')
                .should('eq', false);
        });
    });

    // --- versionsCleaner.config ---

    describe('versionsCleaner.config', () => {
        it('returns all config fields', () => {
            cy.apollo({query: getConfig})
                .its('data.versionsCleaner.config')
                .should(config => {
                    expect(config).to.have.property('disabled');
                    expect(config).to.have.property('cronExpression');
                    expect(config).to.have.property('nbVersionsToKeep');
                    expect(config).to.have.property('deleteOrphanedVersions');
                    expect(config).to.have.property('checkIntegrity');
                    expect(config).to.have.property('reindexDefaultWorkspace');
                    expect(config).to.have.property('maxExecutionTimeInMs');
                });
        });

        it('returns config fields with correct types', () => {
            cy.apollo({query: getConfig})
                .its('data.versionsCleaner.config')
                .should(config => {
                    expect(config.disabled).to.be.a('boolean');
                    expect(config.cronExpression).to.be.a('string').and.not.be.empty;
                    expect(config.nbVersionsToKeep).to.be.a('number');
                    expect(config.deleteOrphanedVersions).to.be.a('boolean');
                    expect(config.checkIntegrity).to.be.a('boolean');
                    expect(config.reindexDefaultWorkspace).to.be.a('boolean');
                    expect(config.maxExecutionTimeInMs).to.be.a('number');
                });
        });
    });

    // --- versionsCleaner.saveConfig ---

    describe('versionsCleaner.saveConfig', () => {
        it('saves config and returns true', () => {
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
            })
                .its('data.versionsCleaner.saveConfig')
                .should('eq', true);
        });

        it('saves nbVersionsToKeep and reads it back consistently', () => {
            cy.apollo({mutation: saveConfig, variables: {nbVersionsToKeep: 5}});
            cy.apollo({query: getConfig})
                .its('data.versionsCleaner.config.nbVersionsToKeep')
                .should('eq', 5);
        });

        it('saves maxExecutionTimeInMs and reads it back consistently', () => {
            cy.apollo({mutation: saveConfig, variables: {maxExecutionTimeInMs: 120000}});
            cy.apollo({query: getConfig})
                .its('data.versionsCleaner.config.maxExecutionTimeInMs')
                .should('eq', 120000);
        });

        it('saves disabled flag and reads it back consistently', () => {
            cy.apollo({mutation: saveConfig, variables: {disabled: false}});
            cy.apollo({query: getConfig})
                .its('data.versionsCleaner.config.disabled')
                .should('eq', false);
        });

        it('saves cronExpression and reads it back consistently', () => {
            const cron = '0 0 2 * * ?';
            cy.apollo({mutation: saveConfig, variables: {cronExpression: cron}});
            cy.apollo({query: getConfig})
                .its('data.versionsCleaner.config.cronExpression')
                .should('eq', cron);
        });
    });

    // --- versionsCleaner.run ---

    describe('versionsCleaner.run', () => {
        it('starts the cleaner and returns true', () => {
            cy.apollo({
                mutation: runCleaner,
                variables: {
                    nbVersionsToKeep: 2,
                    deleteOrphanedVersions: false,
                    checkIntegrity: false,
                    reindexDefaultWorkspace: false,
                    maxExecutionTimeInMs: 500,
                    pauseDuration: 0,
                    forceRestartFromBeginning: true
                }
            })
                .its('data.versionsCleaner.run')
                .should('eq', true);
        });

        it('reports as running immediately after start', () => {
            cy.apollo({
                mutation: runCleaner,
                variables: {
                    nbVersionsToKeep: 2,
                    deleteOrphanedVersions: false,
                    checkIntegrity: false,
                    reindexDefaultWorkspace: false,
                    maxExecutionTimeInMs: 10000,
                    pauseDuration: 200,
                    forceRestartFromBeginning: true
                }
            });
            cy.apollo({query: isRunning})
                .its('data.versionsCleaner.isRunning')
                .should('eq', true);
        });

        it('returns false when a clean is already in progress', () => {
            cy.apollo({
                mutation: runCleaner,
                variables: {
                    nbVersionsToKeep: 2,
                    deleteOrphanedVersions: false,
                    checkIntegrity: false,
                    reindexDefaultWorkspace: false,
                    maxExecutionTimeInMs: 10000,
                    pauseDuration: 200,
                    forceRestartFromBeginning: true
                }
            });
            cy.apollo({
                mutation: runCleaner,
                variables: {
                    nbVersionsToKeep: 2,
                    deleteOrphanedVersions: false,
                    checkIntegrity: false,
                    reindexDefaultWorkspace: false,
                    maxExecutionTimeInMs: 10000,
                    pauseDuration: 200,
                    forceRestartFromBeginning: true
                }
            })
                .its('data.versionsCleaner.run')
                .should('eq', false);
        });
    });
});
