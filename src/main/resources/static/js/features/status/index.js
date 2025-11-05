/**
 * Status - Entry Point
 *
 * Initializes user status page with auto-refresh, date formatting,
 * and online user counting.
 *
 * @module features/status
 */

import { StatusManager } from './StatusManager.js';

/**
 * Initialize status manager
 */
function init() {
    console.log('🚀 Initializing Status System...');

    try {
        // Create status manager instance
        const statusManager = new StatusManager();
        statusManager.initialize();

        // Make globally accessible for debugging and cleanup
        window.statusManager = statusManager;

        console.log('✅ Status System initialized successfully');

    } catch (error) {
        console.error('❌ Error initializing Status System:', error);
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
    StatusManager,
    init
};

// Make available globally for backward compatibility
window.StatusManager = StatusManager;
