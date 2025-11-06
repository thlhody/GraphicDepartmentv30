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

// Import time-management modules for embedded fragment support
import { TimeOffManagement } from '../time-management/TimeOffManagement.js';
import { HolidayRequestModal } from '../time-management/HolidayRequestModal.js';
import { InlineEditing } from '../time-management/InlineEditing.js';

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

    // Expose time-management modules globally for fragment reinitialization
    window.TimeOffManagementModule = TimeOffManagement;
    window.HolidayRequestModalModule = HolidayRequestModal;
    window.InlineEditingModule = InlineEditing;

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
 * Only shows if server indicated via URL parameter
 */
function initResumeModal() {
    // Check if server wants to show resume modal
    const sessionDataElement = document.getElementById('sessionPageData');
    if (!sessionDataElement) return;

    try {
        const sessionData = JSON.parse(sessionDataElement.textContent);

        // Only show modal if showResumeConfirmation is explicitly true
        if (sessionData.urlParams && sessionData.urlParams.showResumeConfirmation === 'true') {
            const modalElement = document.getElementById('resumeConfirmationModal');
            if (modalElement) {
                console.log('📋 Showing resume confirmation modal (user clicked resume button)');
                SessionUI.showResumeModal();
            }
        }
    } catch (error) {
        console.error('Error checking resume modal state:', error);
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
