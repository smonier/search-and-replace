import {registry} from '@jahia/ui-extender';
import register from './AdminPanel.register';

export default function () {
    const existingCallback = registry.get('callback', 'search-and-replace');
    if (existingCallback) {
        return;
    }

    registry.add('callback', 'search-and-replace', {
        targets: ['jahiaApp-init:99'],
        callback: register
    });
}
