import {registry} from '@jahia/ui-extender';
import register from './VersionsCleaner/register';
import i18next from 'i18next';

export default function () {
    registry.add('callback', 'versions-cleaner', {
        targets: ['jahiaApp-init:50'],
        callback: async () => {
            await i18next.loadNamespaces('versions-cleaner', () => {
                console.debug('%c versions-cleaner: i18n namespace loaded', 'color: #463CBA');
            });
            register();
            console.debug('%c versions-cleaner: activation completed', 'color: #463CBA');
        }
    });
}
