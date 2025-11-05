/**
 * Dashboard - Entry Point
 *
 * Initializes dashboard auto-refresh system and monitoring.
 * Coordinates cache status monitoring and automatic metric updates.
 *
 * @module features/dashboard
 */

import { DashboardAutoRefresh } from './DashboardAutoRefresh.js';

/**
 * Initialize dashboard auto-refresh system
 */
function init() {
    console.log('🚀 Initializing Dashboard Auto-Refresh System...');

    try {
        // Create dashboard refresh instance
        const dashboardRefresh = new DashboardAutoRefresh();
        dashboardRefresh.init();

        // Enable legacy auto-reload if data-refresh attribute present
        DashboardAutoRefresh.enableLegacyAutoReload();

        // Make globally accessible for inline HTML handlers
        window.dashboardRefresh = dashboardRefresh;

        console.log('✅ Dashboard Auto-Refresh initialized successfully');

    } catch (error) {
        console.error('❌ Error initializing Dashboard Auto-Refresh:', error);
    }
}

/**
 * Add CSS styles for dashboard animations
 */
function addStyles() {
    const style = document.createElement('style');
    style.textContent = `
        /* Spinning animation for refresh indicator */
        .spinning {
            animation: spin 1s linear infinite;
        }
        @keyframes spin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
        }

        /* Refresh indicator styling */
        #refresh-indicator .alert {
            margin-bottom: 0;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
        }
    `;
    document.head.appendChild(style);
}

// Auto-initialize on DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        addStyles();
        init();
    });
} else {
    addStyles();
    init();
}

// Export for external access
export {
    DashboardAutoRefresh,
    init
};

// Make available globally for backward compatibility
window.DashboardAutoRefresh = DashboardAutoRefresh;
