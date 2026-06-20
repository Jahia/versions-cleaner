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
    const [completionMsg, setCompletionMsg] = useState('');
    const prevRunning = useRef(false);

    React.useEffect(() => {
        document.title = `${t('label.title')} — Jahia Administration`;
    }, [t]);

    const {data, startPolling, stopPolling} = useQuery(IS_RUNNING, {
        fetchPolicy: 'network-only'
    });

    const isRunning = data?.versionsCleaner?.isRunning === true;

    React.useEffect(() => {
        if (isRunning) {
            startPolling(POLL_INTERVAL_MS);
        } else {
            stopPolling();
        }

        return () => stopPolling();
    }, [isRunning, startPolling, stopPolling]);

    // Announce completion when isRunning transitions from true → false (N-1)
    React.useEffect(() => {
        if (prevRunning.current && !isRunning) {
            setCompletionMsg(t('label.completed'));
        }

        prevRunning.current = isRunning;
    }, [isRunning, t]);

    const [runCleaner, {loading: submitting}] = useMutation(RUN_CLEANER);

    const handleChange = (field, value) => {
        setForm(prev => ({...prev, [field]: value}));
    };

    const handleRun = async () => {
        setCompletionMsg('');
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
            if (result.data?.versionsCleaner?.run === true) {
                setRunStatus('started');
                startPolling(POLL_INTERVAL_MS);
            } else {
                setRunStatus('already_running');
            }
        } catch (_err) {
            setRunStatus('error');
        }
    };

    const busy = submitting || isRunning;

    const statusMessage = runStatus === 'started' ? t('label.started') :
        runStatus === 'already_running' ? t('label.alreadyRunning') :
        runStatus === 'error' ? t('label.error') : '';

    return (
        <div className={styles.vc_container}>
            {/* Two fixed-role live regions — AT registers role at first render; mutating role has no effect */}
            <div role="status" aria-live="polite" aria-atomic="true" className={styles.vc_sr_only}>
                {runStatus && runStatus !== 'error' ? statusMessage : ''}
            </div>
            <div role="alert" aria-live="assertive" aria-atomic="true" className={styles.vc_sr_only}>
                {runStatus === 'error' ? statusMessage : ''}
            </div>

            {/* Running state + completion announcement */}
            <div role="status" aria-live="polite" aria-atomic="true" className={styles.vc_sr_only}>
                {isRunning ? t('label.running') : completionMsg}
            </div>

            <div className={styles.vc_header}>
                <h2>{t('label.title')}</h2>
            </div>

            <div className={styles.vc_description}>
                <Typography>{t('label.description')}</Typography>
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
                        required
                        aria-required="true"
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
                        required
                        aria-required="true"
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
                        autoComplete="off"
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
