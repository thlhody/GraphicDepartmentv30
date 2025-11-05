/**
 * Session - Entry Point
 *
 * Initializes all modules for the session page.
 * Handles session UI, end time scheduling, and time management integration.
 *
 * @module features/session
 */

import { SessionUI } from './SessionUI.js';
import { SessionEndTime } from './SessionEndTime.js';
import { SessionTimeManagement } from './SessionTimeManagement.js';
import { formatMinutesToHours } from '../../core/utils.js';

/**
 * Initialize session page
 */
function init() {
    console.log("Initializing Session Page...");

    // Initialize session UI (tooltips, clock, floating card, etc.)
    const sessionUI = new SessionUI();

    // Initialize end time scheduler and calculator
    const sessionEndTime = new SessionEndTime();

    // Initialize time management integration
    const sessionTimeManagement = new SessionTimeManagement();

    // Make instances globally available for debugging
    window.Session = {
        ui: sessionUI,
        endTime: sessionEndTime,
        timeManagement: sessionTimeManagement
    };

    // Make instance available for error handler
    window.SessionTimeManagementInstance = sessionTimeManagement;

    // Make formatMinutes helper available globally (used by legacy code)
    window.formatMinutes = formatMinutesToHours;

    // Expose debug functions
    window.timeManagementDebug = {
        getState: () => sessionTimeManagement.getState(),
        reload: () => sessionTimeManagement.reload(),
        navigate: (monthDelta, yearDelta) => sessionTimeManagement.navigatePeriod(monthDelta, yearDelta)
    };

    console.log("Session page initialized successfully");
}

/**
 * Initialize resume modal (simple standalone functionality)
 */
function initResumeModal() {
    // Show resume confirmation modal if it exists
    const modalElement = document.getElementById('resumeConfirmationModal');
    if (modalElement) {
        SessionUI.showResumeModal();
    }
}

// Auto-initialize on DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        init();
        initResumeModal();
    });
} else {
    init();
    initResumeModal();
}

// Export for manual initialization if needed
export { init, initResumeModal };
