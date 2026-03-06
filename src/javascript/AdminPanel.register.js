import {registerRoutes} from './AdminPanel/AdminPanel.routes';

export default function () {
    window.jahia.i18n.loadNamespaces('search-and-replace');

    try {
        registerRoutes();
    } catch (error) {
        // Keep callback failures from impacting other module registrations.
        console.error('Failed to register Search and Replace admin route', error);
    }
}
