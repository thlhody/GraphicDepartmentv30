/**
 * Time Management - Entry Point
 *
 * Initializes all time management modules for the user time management page.
 * Coordinates initialization of inline editing, status display, period navigation,
 * time off management, and other time management features.
 *
 * @module features/time-management
 */

import { TimeManagementUtilities } from './TimeManagementUtilities.js';
import { StatusDisplay } from './StatusDisplay.js';
import { TimeInput } from './TimeInput.js';
import { WorkTimeDisplay } from './WorkTimeDisplay.js';
import { InlineEditing } from './InlineEditing.js';
import { TimeOffManagement } from './TimeOffManagement.js';
import { PeriodNavigation } from './PeriodNavigation.js';

/**
 * Initialize time management interface
 */
function init() {
    console.log('🚀 Initializing Time Management Interface...');

    try {
        // Initialize core utilities first
        TimeManagementUtilities.initialize();

        // Initialize display modules
        StatusDisplay.initialize();
        WorkTimeDisplay.initialize();

        // Initialize editing functionality
        InlineEditing.initialize();

        // Initialize time off management
        TimeOffManagement.initialize();

        // Initialize period navigation (must be after inline editing)
        PeriodNavigation.initialize();

        console.log('✅ Time Management Interface initialized successfully');

        // Restore scroll position if available
        restoreScrollPosition();

    } catch (error) {
        console.error('❌ Error initializing Time Management:', error);
    }
}

/**
 * Restore scroll position from session storage
 */
function restoreScrollPosition() {
    const savedPosition = sessionStorage.getItem('timeManagementScrollPosition');
    if (savedPosition) {
        window.scrollTo(0, parseInt(savedPosition));
        sessionStorage.removeItem('timeManagementScrollPosition');
        console.log('📜 Restored scroll position:', savedPosition);
    }
}

// Auto-initialize on DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

// Export modules for backward compatibility and external access
export {
    TimeManagementUtilities,
    StatusDisplay,
    TimeInput,
    WorkTimeDisplay,
    InlineEditing,
    TimeOffManagement,
    PeriodNavigation,
    init
};

// Make modules available globally for backward compatibility
window.TimeManagementUtilities = TimeManagementUtilities;
window.StatusDisplayModule = StatusDisplay;
window.TimeInputModule = TimeInput;
window.WorkTimeDisplayModule = WorkTimeDisplay;
window.InlineEditingModule = InlineEditing;
window.TimeOffManagementModule = TimeOffManagement;
window.PeriodNavigationModule = PeriodNavigation;

// Legacy compatibility - some inline HTML might still use these
window.handleCellDoubleClick = (cell) => InlineEditing.handleCellDoubleClick(cell);
window.showStatusDetails = (el, e) => StatusDisplay.showStatusDetails(el, e);
