import React, {useEffect, useState} from 'react';
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

    useEffect(() => {
        document.title = `${t('label.menu_configuration')} — Jahia Administration`;
    }, [t]);

    const {data, loading} = useQuery(GET_CONFIG, {fetchPolicy: 'network-only'});

    useEffect(() => {
        if (data?.versionsCleaner?.config) {
            const c = data.versionsCleaner.config;
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
            setSaveStatus(result.data?.versionsCleaner?.saveConfig === true ? 'success' : 'error');
        } catch {
            setSaveStatus('error');
        }
    };

    const saveSuccessMsg = saveStatus === 'success' ? t('label.scheduler.saved') : '';
    const saveErrorMsg = saveStatus === 'error' ? t('label.error') : '';

    return (
        <div className={styles.vc_container}>
            {/* Two fixed-role live regions — AT caches role at registration; dynamic role mutation is ignored */}
            <div role="status" aria-live="polite" aria-atomic="true" className={styles.vc_sr_only}>
                {saveSuccessMsg}
            </div>
            <div role="alert" aria-live="assertive" aria-atomic="true" className={styles.vc_sr_only}>
                {saveErrorMsg}
            </div>

            <div className={styles.vc_header}>
                <h2>{t('label.menu_configuration')}</h2>
            </div>

            <div className={styles.vc_description}>
                <Typography>{t('label.scheduler.description')}</Typography>
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
                {/* C-2: Switch labeled via aria-labelledby (ARIA16). <label> removed — it had no htmlFor
                    and would confuse AT. The span id is the label anchor for aria-labelledby. */}
                <div className={styles.vc_fieldGroup}>
                    <div className={styles.vc_label}>
                        <span id="vc-cfg-enabled-label">{t('label.scheduler.enabled')}</span>
                        <Tooltip label={t('label.scheduler.enabledTooltip')}>
                            <span aria-hidden="true" className={styles.vc_tooltip}>ⓘ</span>
                        </Tooltip>
                    </div>
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
                        required
                        aria-required="true"
                        aria-invalid={saveStatus === 'error' ? 'true' : undefined}
                        aria-describedby="vc-cron-hint vc-cron-error"
                        autoComplete="off"
                        onChange={e => handleChange('cronExpression', e.target.value)}
                    />
                    <span id="vc-cron-hint" className={styles.vc_sr_only}>
                        {t('label.scheduler.cronExpressionTooltip')}
                    </span>
                    <span id="vc-cron-error" className={styles.vc_sr_only}>{saveErrorMsg}</span>
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
                        required
                        aria-required="true"
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
                        required
                        aria-required="true"
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
