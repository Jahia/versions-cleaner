import {registry} from '@jahia/ui-extender';
import {VersionsCleanerAdmin} from './VersionsCleaner';
import React from 'react';

export default () => {
    console.debug('%c versions-cleaner: activation in progress', 'color: #463CBA');
    registry.add('adminRoute', 'versionsCleaner', {
        targets: ['administration-server-systemComponents:999'],
        requiredPermission: 'admin',
        label: 'versions-cleaner:label.menu_entry',
        isSelectable: true,
        render: () => React.createElement(VersionsCleanerAdmin)
    });
};
