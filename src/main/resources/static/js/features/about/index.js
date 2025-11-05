/**
 * About - Entry Point
 *
 * Initializes about modal with auto-show, logo easter egg,
 * and notification preview functionality.
 *
 * @module features/about
 */

import { AboutManager } from './AboutManager.js';

/**
 * Initialize about manager
 */
function init() {
    console.log('🚀 Initializing About System...');

    try {
        // Check if we're on the about page (has notification testing card)
        const notificationCard = document.querySelector('.notification-testing-card');
        const aboutModal = document.getElementById('aboutModal');

        if (!notificationCard && !aboutModal) {
            console.log('About page/modal not found, skipping initialization');
            return;
        }

        // Create about manager instance
        const aboutManager = new AboutManager();
        aboutManager.initialize();

        // Make globally accessible for debugging
        window.aboutManager = aboutManager;

        // Expose utility methods for external access
        window.aboutPageUtils = {
            showModal: () => aboutManager.showModal(),
            hideModal: () => aboutManager.hideModal(),
            isModalVisible: () => aboutManager.isModalVisible()
        };

        console.log('✅ About System initialized successfully');

    } catch (error) {
        console.error('❌ Error initializing About System:', error);
    }
}

// Auto-initialize on DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

// Export for external access
export {
    AboutManager,
    init
};

// Make available globally for backward compatibility
window.AboutManager = AboutManager;
