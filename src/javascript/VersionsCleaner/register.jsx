import {registry} from '@jahia/ui-extender';
import {VersionsCleanerAdmin} from './VersionsCleaner';
import {SchedulerConfig} from './SchedulerConfig';
import React from 'react';

export default () => {
    console.debug('%c versions-cleaner: activation in progress', 'color: #463CBA');
    registry.add('adminRoute', 'versionsCleaner', {
        targets: ['administration-server-systemHealth:999'],
        requiredPermission: 'admin',
        label: 'versions-cleaner:label.menu_entry',
        isSelectable: false
    });
    registry.add('adminRoute', 'versionsCleanerExecution', {
        targets: ['administration-server-versionsCleaner:1'],
        requiredPermission: 'admin',
        label: 'versions-cleaner:label.menu_execution',
        isSelectable: true,
        render: () => React.createElement(VersionsCleanerAdmin)
    });
    registry.add('adminRoute', 'versionsCleanerConfiguration', {
        targets: ['administration-server-versionsCleaner:2'],
        requiredPermission: 'admin',
        label: 'versions-cleaner:label.menu_configuration',
        isSelectable: true,
        render: () => React.createElement(SchedulerConfig)
    });
};
