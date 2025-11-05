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
import { HolidayRequestModal } from './HolidayRequestModal.js';
import { HolidayExportService } from './HolidayExportService.js';

// Global holiday modal instance
let holidayModal = null;

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

        // Initialize holiday request modal if present on page
        initializeHolidayModal();

        console.log('✅ Time Management Interface initialized successfully');

        // Restore scroll position if available
        restoreScrollPosition();

    } catch (error) {
        console.error('❌ Error initializing Time Management:', error);
    }
}

/**
 * Initialize holiday request modal if modal element exists on page
 */
function initializeHolidayModal() {
    const holidayModalElement = document.getElementById('holidayModal');
    if (holidayModalElement) {
        console.log('📋 Initializing Holiday Request Modal...');
        holidayModal = new HolidayRequestModal();
        holidayModal.init();

        // Make available globally for backward compatibility
        window.holidayRequestModal = holidayModal;
        window.openHolidayRequestModal = (startDate, endDate, userData, timeOffType) => {
            return holidayModal.open(startDate, endDate, userData, timeOffType);
        };
        window.closeHolidayModal = () => holidayModal.close();
        window.exportHolidayToImage = (format) => holidayModal.exportToImage(format);

        console.log('✅ Holiday Request Modal initialized');
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
    HolidayRequestModal,
    HolidayExportService,
    holidayModal,
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
