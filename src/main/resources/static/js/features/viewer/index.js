/**
 * Log Viewer - Entry Point
 *
 * Initializes log viewer interface with user selection, log loading,
 * searching, filtering, auto-scroll, text wrap, and export.
 *
 * @module features/viewer
 */

import { LogViewerManager } from './LogViewerManager.js';

/**
 * Initialize log viewer manager
 */
function init() {
    console.log('🚀 Initializing Log Viewer System...');

    try {
        // Create log viewer manager instance
        const logViewerManager = new LogViewerManager();
        logViewerManager.initialize();

        // Make globally accessible for debugging
        window.logViewerManager = logViewerManager;

        console.log('✅ Log Viewer System initialized successfully');

    } catch (error) {
        console.error('❌ Error initializing Log Viewer System:', error);
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
    LogViewerManager,
    init
};

// Make available globally for backward compatibility
window.LogViewerManager = LogViewerManager;
