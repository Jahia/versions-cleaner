import React, {useRef, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, CheckboxItem, Input, Loader, NumberInput, Tooltip, Typography} from '@jahia/moonstone';
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
    const statusRef = useRef(null);

    const {data, startPolling, stopPolling} = useQuery(IS_RUNNING, {
        fetchPolicy: 'network-only'
    });

    const isRunning = data?.versionsCleanerIsRunning === true;

    React.useEffect(() => {
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

        setTimeout(() => statusRef.current?.focus(), 50);
    };

    const busy = submitting || isRunning;

    const statusMessage = runStatus === 'started' ? t('label.started') :
        runStatus === 'already_running' ? t('label.alreadyRunning') :
        runStatus === 'error' ? t('label.error') : '';
    const isErrorStatus = runStatus === 'error';

    return (
        <div className={styles.vc_container}>
            <div className={styles.vc_header}>
                <h2>{t('label.title')}</h2>
            </div>

            <div className={styles.vc_description}>
                <Typography>{t('label.description')}</Typography>
            </div>

            {/* Persistent live regions — always in DOM so AT registers them before content appears */}
            <div
                ref={statusRef}
                tabIndex={-1}
                role={isErrorStatus ? 'alert' : 'status'}
                aria-live={isErrorStatus ? 'assertive' : 'polite'}
                aria-atomic="true"
                className={styles.vc_sr_only}
            >
                {statusMessage}
            </div>

            {/* Persistent live region for running state */}
            <div
                role="status"
                aria-live="polite"
                aria-atomic="true"
                className={styles.vc_sr_only}
            >
                {isRunning ? t('label.running') : ''}
            </div>

            {runStatus === 'started' && (
                <div aria-hidden="true" className={`${styles.vc_alert} ${styles['vc_alert--success']}`}>
                    {t('label.started')}
                </div>
            )}
            {runStatus === 'already_running' && (
                <div aria-hidden="true" className={`${styles.vc_alert} ${styles['vc_alert--warning']}`}>
                    {t('label.alreadyRunning')}
                </div>
            )}
            {runStatus === 'error' && (
                <div aria-hidden="true" className={`${styles.vc_alert} ${styles['vc_alert--error']}`}>
                    {t('label.error')}
                </div>
            )}

            {isRunning && (
                <div aria-hidden="true" className={styles.vc_running}>
                    <Loader size="big"/>
                    <Typography className={styles.vc_running_text}>{t('label.running')}</Typography>
                </div>
            )}

            <div className={styles.vc_form}>
                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label} htmlFor="vc-nb-versions">
                        {t('label.nbVersionsToKeep')}
                        <Tooltip label={t('label.nbVersionsToKeepTooltip')}>
                            <span aria-hidden="true" className={styles.vc_tooltip}>ⓘ</span>
                        </Tooltip>
                    </label>
                    <NumberInput
                        id="vc-nb-versions"
                        className={styles.vc_input}
                        value={String(form.nbVersionsToKeep)}
                        allowNegative
                        aria-describedby="vc-nb-versions-hint"
                        onChange={e => handleChange('nbVersionsToKeep', Number.parseInt(e.target.value, 10))}
                    />
                    <span id="vc-nb-versions-hint" className={styles.vc_sr_only}>
                        {t('label.nbVersionsToKeepTooltip')}
                    </span>
                </div>

                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label} htmlFor="vc-max-time">
                        {t('label.maxExecutionTimeInMs')}
                        <Tooltip label={t('label.maxExecutionTimeTooltip')}>
                            <span aria-hidden="true" className={styles.vc_tooltip}>ⓘ</span>
                        </Tooltip>
                    </label>
                    <NumberInput
                        id="vc-max-time"
                        className={styles.vc_input}
                        value={String(form.maxExecutionTimeInMs)}
                        min={0}
                        aria-describedby="vc-max-time-hint"
                        onChange={e => handleChange('maxExecutionTimeInMs', Number.parseInt(e.target.value, 10) || 0)}
                    />
                    <span id="vc-max-time-hint" className={styles.vc_sr_only}>
                        {t('label.maxExecutionTimeTooltip')}
                    </span>
                </div>

                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label} htmlFor="vc-pause">
                        {t('label.pauseDuration')}
                        <Tooltip label={t('label.pauseDurationTooltip')}>
                            <span aria-hidden="true" className={styles.vc_tooltip}>ⓘ</span>
                        </Tooltip>
                    </label>
                    <NumberInput
                        id="vc-pause"
                        className={styles.vc_input}
                        value={String(form.pauseDuration)}
                        min={0}
                        aria-describedby="vc-pause-hint"
                        onChange={e => handleChange('pauseDuration', Number.parseInt(e.target.value, 10) || 0)}
                    />
                    <span id="vc-pause-hint" className={styles.vc_sr_only}>
                        {t('label.pauseDurationTooltip')}
                    </span>
                </div>

                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label} htmlFor="vc-subtree">
                        {t('label.subtreePath')}
                        <Tooltip label={t('label.subtreePathTooltip')}>
                            <span aria-hidden="true" className={styles.vc_tooltip}>ⓘ</span>
                        </Tooltip>
                    </label>
                    <Input
                        id="vc-subtree"
                        className={styles.vc_inputWide}
                        placeholder={t('label.subtreePathPlaceholder')}
                        value={form.subtreePath}
                        aria-describedby="vc-subtree-hint"
                        onChange={e => handleChange('subtreePath', e.target.value)}
                    />
                    <span id="vc-subtree-hint" className={styles.vc_sr_only}>
                        {t('label.subtreePathTooltip')}
                    </span>
                </div>

                <fieldset className={styles.vc_checkboxGroup}>
                    <legend className={styles.vc_checkboxLegend}>{t('label.runOptions')}</legend>
                    <CheckboxItem
                        id="vc-delete-orphaned"
                        label={t('label.deleteOrphanedVersions')}
                        checked={form.deleteOrphanedVersions}
                        onChange={(e, v, checked) => handleChange('deleteOrphanedVersions', checked)}
                    />
                    <CheckboxItem
                        id="vc-check-integrity"
                        label={t('label.checkIntegrity')}
                        checked={form.checkIntegrity}
                        onChange={(e, v, checked) => handleChange('checkIntegrity', checked)}
                    />
                    <CheckboxItem
                        id="vc-reindex"
                        label={t('label.reindexDefaultWorkspace')}
                        checked={form.reindexDefaultWorkspace}
                        onChange={(e, v, checked) => handleChange('reindexDefaultWorkspace', checked)}
                    />
                    <CheckboxItem
                        id="vc-force-restart"
                        label={t('label.forceRestartFromBeginning')}
                        checked={form.forceRestartFromBeginning}
                        onChange={(e, v, checked) => handleChange('forceRestartFromBeginning', checked)}
                    />
                </fieldset>
            </div>

            <div className={styles.vc_actions}>
                <Button
                    type="button"
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
