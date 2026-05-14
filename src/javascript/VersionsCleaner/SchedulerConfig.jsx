import React, {useEffect, useRef, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, CheckboxItem, Input, NumberInput, Switch, Tooltip, Typography} from '@jahia/moonstone';
import styles from './VersionsCleaner.scss';
import {GET_CONFIG, SAVE_CONFIG} from './VersionsCleaner.gql';

const DEFAULT_CONFIG = {
    disabled: true,
    cronExpression: '0 30 1 * * ?',
    nbVersionsToKeep: 2,
    deleteOrphanedVersions: false,
    checkIntegrity: false,
    reindexDefaultWorkspace: false,
    maxExecutionTimeInMs: 60000
};

export const SchedulerConfig = () => {
    const {t} = useTranslation('versions-cleaner');
    const [form, setForm] = useState(DEFAULT_CONFIG);
    const [saveStatus, setSaveStatus] = useState(null);
    const statusRef = useRef(null);

    const {data, loading} = useQuery(GET_CONFIG, {fetchPolicy: 'network-only'});

    useEffect(() => {
        if (data?.versionsCleanerConfig) {
            const c = data.versionsCleanerConfig;
            setForm({
                disabled: c.disabled,
                cronExpression: c.cronExpression,
                nbVersionsToKeep: c.nbVersionsToKeep,
                deleteOrphanedVersions: c.deleteOrphanedVersions,
                checkIntegrity: c.checkIntegrity,
                reindexDefaultWorkspace: c.reindexDefaultWorkspace,
                maxExecutionTimeInMs: c.maxExecutionTimeInMs
            });
        }
    }, [data]);

    const [saveConfig, {loading: saving}] = useMutation(SAVE_CONFIG);

    const handleChange = (field, value) => {
        setForm(prev => ({...prev, [field]: value}));
    };

    const handleSave = async () => {
        setSaveStatus(null);
        try {
            const result = await saveConfig({
                variables: {
                    disabled: form.disabled,
                    cronExpression: form.cronExpression,
                    nbVersionsToKeep: form.nbVersionsToKeep,
                    deleteOrphanedVersions: form.deleteOrphanedVersions,
                    checkIntegrity: form.checkIntegrity,
                    reindexDefaultWorkspace: form.reindexDefaultWorkspace,
                    maxExecutionTimeInMs: form.maxExecutionTimeInMs
                }
            });
            setSaveStatus(result.data?.versionsCleanerSaveConfig === true ? 'success' : 'error');
        } catch {
            setSaveStatus('error');
        }

        setTimeout(() => statusRef.current?.focus(), 50);
    };

    return (
        <div className={styles.vc_container}>
            <div className={styles.vc_header}>
                <h2>{t('label.menu_configuration')}</h2>
            </div>

            <div className={styles.vc_description}>
                <Typography>{t('label.scheduler.description')}</Typography>
            </div>

            {/* Persistent live regions — always in DOM so AT registers them before content appears */}
            <div
                ref={statusRef}
                tabIndex={-1}
                role={saveStatus === 'error' ? 'alert' : 'status'}
                aria-live={saveStatus === 'error' ? 'assertive' : 'polite'}
                aria-atomic="true"
                className={styles.vc_sr_only}
            >
                {saveStatus === 'success' ? t('label.scheduler.saved') :
                    saveStatus === 'error' ? t('label.error') :
                    saving ? t('label.scheduler.saving') : ''}
            </div>

            {saveStatus === 'success' && (
                <div aria-hidden="true" className={`${styles.vc_alert} ${styles['vc_alert--success']}`}>
                    {t('label.scheduler.saved')}
                </div>
            )}
            {saveStatus === 'error' && (
                <div aria-hidden="true" className={`${styles.vc_alert} ${styles['vc_alert--error']}`}>
                    {t('label.error')}
                </div>
            )}

            <div className={styles.vc_form}>
                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label}>
                        <span id="vc-cfg-enabled-label">{t('label.scheduler.enabled')}</span>
                        <Tooltip label={t('label.scheduler.enabledTooltip')}>
                            <span aria-hidden="true" className={styles.vc_tooltip}>ⓘ</span>
                        </Tooltip>
                    </label>
                    <Switch
                        aria-labelledby="vc-cfg-enabled-label"
                        aria-describedby="vc-cfg-enabled-hint"
                        checked={!form.disabled}
                        isDisabled={loading}
                        onChange={(e, v, checked) => handleChange('disabled', !checked)}
                    />
                    <span id="vc-cfg-enabled-hint" className={styles.vc_sr_only}>
                        {t('label.scheduler.enabledTooltip')}
                    </span>
                </div>

                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label} htmlFor="vc-cron">
                        {t('label.scheduler.cronExpression')}
                        <Tooltip label={t('label.scheduler.cronExpressionTooltip')}>
                            <span aria-hidden="true" className={styles.vc_tooltip}>ⓘ</span>
                        </Tooltip>
                    </label>
                    <Input
                        id="vc-cron"
                        className={styles.vc_inputWide}
                        value={loading ? '' : form.cronExpression}
                        isDisabled={loading || form.disabled}
                        aria-describedby="vc-cron-hint"
                        onChange={e => handleChange('cronExpression', e.target.value)}
                    />
                    <span id="vc-cron-hint" className={styles.vc_sr_only}>
                        {t('label.scheduler.cronExpressionTooltip')}
                    </span>
                </div>

                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label} htmlFor="vc-cfg-nb-versions">
                        {t('label.nbVersionsToKeep')}
                        <Tooltip label={t('label.nbVersionsToKeepTooltip')}>
                            <span aria-hidden="true" className={styles.vc_tooltip}>ⓘ</span>
                        </Tooltip>
                    </label>
                    <NumberInput
                        id="vc-cfg-nb-versions"
                        className={styles.vc_input}
                        value={String(form.nbVersionsToKeep)}
                        allowNegative
                        isDisabled={loading}
                        aria-describedby="vc-cfg-nb-versions-hint"
                        onChange={e => handleChange('nbVersionsToKeep', Number.parseInt(e.target.value, 10))}
                    />
                    <span id="vc-cfg-nb-versions-hint" className={styles.vc_sr_only}>
                        {t('label.nbVersionsToKeepTooltip')}
                    </span>
                </div>

                <div className={styles.vc_fieldGroup}>
                    <label className={styles.vc_label} htmlFor="vc-cfg-max-time">
                        {t('label.maxExecutionTimeInMs')}
                        <Tooltip label={t('label.maxExecutionTimeTooltip')}>
                            <span aria-hidden="true" className={styles.vc_tooltip}>ⓘ</span>
                        </Tooltip>
                    </label>
                    <NumberInput
                        id="vc-cfg-max-time"
                        className={styles.vc_input}
                        value={String(form.maxExecutionTimeInMs)}
                        min={0}
                        isDisabled={loading}
                        aria-describedby="vc-cfg-max-time-hint"
                        onChange={e => handleChange('maxExecutionTimeInMs', Number.parseInt(e.target.value, 10) || 0)}
                    />
                    <span id="vc-cfg-max-time-hint" className={styles.vc_sr_only}>
                        {t('label.maxExecutionTimeTooltip')}
                    </span>
                </div>

                <fieldset className={styles.vc_checkboxGroup}>
                    <legend className={styles.vc_checkboxLegend}>{t('label.schedulerOptions')}</legend>
                    <CheckboxItem
                        id="vc-cfg-delete-orphaned"
                        label={t('label.deleteOrphanedVersions')}
                        checked={form.deleteOrphanedVersions}
                        isDisabled={loading}
                        onChange={(e, v, checked) => handleChange('deleteOrphanedVersions', checked)}
                    />
                    <CheckboxItem
                        id="vc-cfg-check-integrity"
                        label={t('label.checkIntegrity')}
                        checked={form.checkIntegrity}
                        isDisabled={loading}
                        onChange={(e, v, checked) => handleChange('checkIntegrity', checked)}
                    />
                    <CheckboxItem
                        id="vc-cfg-reindex"
                        label={t('label.reindexDefaultWorkspace')}
                        checked={form.reindexDefaultWorkspace}
                        isDisabled={loading}
                        onChange={(e, v, checked) => handleChange('reindexDefaultWorkspace', checked)}
                    />
                </fieldset>
            </div>

            <div className={styles.vc_restartHint}>
                <Typography variant="caption">{t('label.scheduler.restartHint')}</Typography>
            </div>

            <div className={styles.vc_actions}>
                <Button
                    type="button"
                    label={saving ? t('label.scheduler.saving') : t('label.scheduler.save')}
                    variant="primary"
                    isDisabled={loading || saving}
                    onClick={handleSave}
                />
            </div>
        </div>
    );
};

export default SchedulerConfig;
