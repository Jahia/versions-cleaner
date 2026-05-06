import React, {useEffect, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Loader, Typography} from '@jahia/moonstone';
import styles from './VersionsCleaner.scss';
import {IS_RUNNING, RUN_CLEANER} from './VersionsCleaner.gql';

const POLL_INTERVAL_MS = 3000;

const DEFAULT_FORM = {
    nbVersionsToKeep: 2,
    deleteOrphanedVersions: false,
    checkIntegrity: false,
    reindexDefaultWorkspace: false,
    maxExecutionTimeInMs: 0,
    pauseDuration: 0,
    subtreePath: '',
    forceRestartFromBeginning: false
};

export const VersionsCleanerAdmin = () => {
    const {t} = useTranslation('versions-cleaner');
    const [form, setForm] = useState(DEFAULT_FORM);
    const [runStatus, setRunStatus] = useState(null);

    const {data, startPolling, stopPolling} = useQuery(IS_RUNNING, {
        fetchPolicy: 'network-only'
    });

    const isRunning = data?.versionsCleanerIsRunning === true;

    useEffect(() => {
        if (isRunning) {
            startPolling(POLL_INTERVAL_MS);
        } else {
            stopPolling();
        }

        return () => stopPolling();
    }, [isRunning, startPolling, stopPolling]);

    const [runCleaner, {loading: submitting}] = useMutation(RUN_CLEANER);

    const handleChange = (field, value) => {
        setForm(prev => ({...prev, [field]: value}));
    };

    const handleRun = async () => {
        setRunStatus(null);
        try {
            const result = await runCleaner({
                variables: {
                    nbVersionsToKeep: form.nbVersionsToKeep,
                    deleteOrphanedVersions: form.deleteOrphanedVersions,
                    checkIntegrity: form.checkIntegrity,
                    reindexDefaultWorkspace: form.reindexDefaultWorkspace,
                    maxExecutionTimeInMs: form.maxExecutionTimeInMs,
                    pauseDuration: form.pauseDuration,
                    subtreePath: form.subtreePath || null,
                    forceRestartFromBeginning: form.forceRestartFromBeginning
                }
            });
            if (result.data?.versionsCleanerRun === true) {
                setRunStatus('started');
                startPolling(POLL_INTERVAL_MS);
            } else {
                setRunStatus('already_running');
            }
        } catch (err) {
            console.error('Failed to start versions cleaner:', err);
            setRunStatus('error');
        }
    };

    const busy = submitting || isRunning;

    return (
        <div className={styles.vc_container}>
            <div className={styles.vc_header}>
                <h2>{t('label.title')}</h2>
            </div>

            <div className={styles.vc_description}>
                <Typography>{t('label.description')}</Typography>
            </div>

            {runStatus === 'started' && (
                <div className={`${styles.vc_alert} ${styles['vc_alert--success']}`}>
                    {t('label.started')}
                </div>
            )}
            {runStatus === 'already_running' && (
                <div className={`${styles.vc_alert} ${styles['vc_alert--warning']}`}>
                    {t('label.alreadyRunning')}
                </div>
            )}
            {runStatus === 'error' && (
                <div className={`${styles.vc_alert} ${styles['vc_alert--error']}`}>
                    {t('label.error')}
                </div>
            )}

            {isRunning && (
                <div className={styles.vc_running}>
                    <Loader size="big"/>
                    <Typography className={styles.vc_running_text}>{t('label.running')}</Typography>
                </div>
            )}

            <div className={styles.vc_form}>
                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label} htmlFor="vc-nb-versions">
                        {t('label.nbVersionsToKeep')}
                        <span className={styles.vc_tooltip} title={t('label.nbVersionsToKeepTooltip')}>ⓘ</span>
                    </label>
                    <input
                        type="number"
                        id="vc-nb-versions"
                        className={styles.vc_input}
                        value={form.nbVersionsToKeep}
                        onChange={e => handleChange('nbVersionsToKeep', Number.parseInt(e.target.value, 10))}
                    />
                </div>

                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label} htmlFor="vc-max-time">
                        {t('label.maxExecutionTimeInMs')}
                        <span className={styles.vc_tooltip} title={t('label.maxExecutionTimeTooltip')}>ⓘ</span>
                    </label>
                    <input
                        type="number"
                        id="vc-max-time"
                        className={styles.vc_input}
                        min="0"
                        value={form.maxExecutionTimeInMs}
                        onChange={e => handleChange('maxExecutionTimeInMs', Number.parseInt(e.target.value, 10) || 0)}
                    />
                </div>

                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label} htmlFor="vc-pause">
                        {t('label.pauseDuration')}
                        <span className={styles.vc_tooltip} title={t('label.pauseDurationTooltip')}>ⓘ</span>
                    </label>
                    <input
                        type="number"
                        id="vc-pause"
                        className={styles.vc_input}
                        min="0"
                        value={form.pauseDuration}
                        onChange={e => handleChange('pauseDuration', Number.parseInt(e.target.value, 10) || 0)}
                    />
                </div>

                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label} htmlFor="vc-subtree">
                        {t('label.subtreePath')}
                        <span className={styles.vc_tooltip} title={t('label.subtreePathTooltip')}>ⓘ</span>
                    </label>
                    <input
                        type="text"
                        id="vc-subtree"
                        className={styles.vc_inputWide}
                        placeholder={t('label.subtreePathPlaceholder')}
                        value={form.subtreePath}
                        onChange={e => handleChange('subtreePath', e.target.value)}
                    />
                </div>

                <div className={styles.vc_checkboxGroup}>
                    <label className={styles.vc_checkboxLabel}>
                        <input
                            type="checkbox"
                            checked={form.deleteOrphanedVersions}
                            onChange={e => handleChange('deleteOrphanedVersions', e.target.checked)}
                        />
                        {t('label.deleteOrphanedVersions')}
                    </label>

                    <label className={styles.vc_checkboxLabel}>
                        <input
                            type="checkbox"
                            checked={form.checkIntegrity}
                            onChange={e => handleChange('checkIntegrity', e.target.checked)}
                        />
                        {t('label.checkIntegrity')}
                    </label>

                    <label className={styles.vc_checkboxLabel}>
                        <input
                            type="checkbox"
                            checked={form.reindexDefaultWorkspace}
                            onChange={e => handleChange('reindexDefaultWorkspace', e.target.checked)}
                        />
                        {t('label.reindexDefaultWorkspace')}
                    </label>

                    <label className={styles.vc_checkboxLabel}>
                        <input
                            type="checkbox"
                            checked={form.forceRestartFromBeginning}
                            onChange={e => handleChange('forceRestartFromBeginning', e.target.checked)}
                        />
                        {t('label.forceRestartFromBeginning')}
                    </label>
                </div>
            </div>

            <div className={styles.vc_actions}>
                <Button
                    label={busy ? t('label.running') : t('label.run')}
                    variant="primary"
                    isDisabled={busy}
                    onClick={handleRun}
                />
            </div>
        </div>
    );
};

export default VersionsCleanerAdmin;
