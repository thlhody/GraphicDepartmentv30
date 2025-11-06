/**
 * Time Management - Entry Point & Core Coordinator
 *
 * Main coordinator for the time management system.
 * Handles module initialization, server messages, error handling, and performance monitoring.
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

// ============================================================================
// STATE MANAGEMENT
// ============================================================================

/**
 * Core state for time management system
 */
const state = {
    isInitialized: false,
    modules: [],
    perfStart: performance.now(),
    holidayModal: null
};

// ============================================================================
// INITIALIZATION
// ============================================================================

/**
 * Initialize complete time management system
 */
function init() {
    console.log('🔍 DEBUG: Current URL when page loads:', window.location.href);
    console.log('🔍 DEBUG: URL parameters:', new URLSearchParams(window.location.search).toString());

    // Prevent multiple initializations
    if (state.isInitialized) {
        console.warn('⚠️ Time Management already initialized, skipping...');
        return;
    }

    console.log('🚀 Initializing Time Management System...');

    try {
        // Initialize time input features first
        initializeTimeInputFeatures();

        // Initialize all modules in dependency order
        initializeModules();

        // Set up global error handling
        setupGlobalErrorHandling();

        // Handle server messages with toast system
        handleServerMessages();

        // Show keyboard shortcut help
        showKeyboardShortcuts();

        // Initialize holiday request modal if present on page
        initializeHolidayModal();

        // Mark as initialized
        state.isInitialized = true;

        // Log performance
        logInitializationPerformance();

        console.log('✅ Time Management System fully initialized');

        // Restore scroll position if available
        restoreScrollPosition();

    } catch (error) {
        console.error('❌ Error initializing Time Management:', error);
    }
}

/**
 * Initialize time input features
 */
function initializeTimeInputFeatures() {
    // Add time input validation listeners
    document.addEventListener('input', function(e) {
        if (e.target.hasAttribute('data-time-input')) {
            const value = e.target.value;
            const isValid = TimeInput.validateTime(value);

            // Update validation classes
            if (value.length === 5) {
                e.target.classList.toggle('is-valid', isValid);
                e.target.classList.toggle('is-invalid', !isValid);
            } else {
                e.target.classList.remove('is-valid', 'is-invalid');
            }
        }
    });

    console.log('✅ Time input validation listeners configured');
}

/**
 * Initialize all modules in the correct order
 */
function initializeModules() {
    const moduleInitializers = [
        // Utility modules first
        { name: 'TimeManagementUtilities', module: TimeManagementUtilities },

        // Display and status modules
        { name: 'StatusDisplay', module: StatusDisplay },
        { name: 'WorkTimeDisplay', module: WorkTimeDisplay },

        // Feature modules that depend on display modules
        { name: 'InlineEditing', module: InlineEditing },
        { name: 'TimeOffManagement', module: TimeOffManagement },
        { name: 'PeriodNavigation', module: PeriodNavigation }
    ];

    moduleInitializers.forEach(({ name, module }) => {
        if (module && typeof module.initialize === 'function') {
            try {
                module.initialize();
                state.modules.push(name);
                console.log(`✅ ${name} initialized successfully`);
            } catch (error) {
                console.error(`❌ Failed to initialize ${name}:`, error);
            }
        } else {
            console.warn(`⚠️ ${name} not found or missing initialize method`);
        }
    });

    console.log(`📦 Initialized ${state.modules.length} modules:`, state.modules.join(', '));
}

// ============================================================================
// SERVER MESSAGE HANDLING
// ============================================================================

/**
 * Handle server-side messages with toast system and holiday modal integration
 */
function handleServerMessages() {
    console.log('🔍 Checking server messages and holiday modal triggers...');

    // Check for success messages and provide additional feedback
    const successAlert = document.querySelector('.alert-success');
    if (successAlert) {
        // Hide the original alert since toast system handles it
        successAlert.style.display = 'none';

        // Show success toast
        if (window.showToast) {
            const message = successAlert.textContent.trim();
            window.showToast('Success', message, 'success');
        }

        // Auto-refresh page after successful operation
        window.addEventListener('beforeunload', function(e) {
            console.log('🔍 DEBUG: Page is being unloaded/refreshed. Current URL:', window.location.href);
        });

        setTimeout(() => {
            console.log('🔄 Auto-refreshing page after successful operation...');
            window.location.reload();
        }, 3000); // 3 second delay
    }

    // Check for error messages
    const errorAlert = document.querySelector('.alert-danger');
    if (errorAlert) {
        errorAlert.style.display = 'none';

        if (window.showToast) {
            const message = errorAlert.textContent.trim();
            window.showToast('Error', message, 'error');
        }
    }

    // Check for holiday modal trigger from server
    checkForHolidayModalTrigger();
}

/**
 * Check if server indicated we should open holiday modal after time-off submission
 */
function checkForHolidayModalTrigger() {
    const holidayDataElement = document.getElementById('timeOffResultData');
    if (!holidayDataElement) {
        console.log('📄 No holiday modal trigger data found');
        return;
    }

    try {
        const holidayData = JSON.parse(holidayDataElement.textContent);
        console.log('📊 Holiday modal data:', holidayData);

        // Check if we should auto-open holiday modal
        if (holidayData.openHolidayModal === 'true') {
            console.log('🎯 Holiday modal trigger detected!');

            // Ensure holiday modal functions are available
            if (typeof window.openHolidayRequestModal !== 'function') {
                console.error('❌ Holiday modal function not available');
                return;
            }

            // Show success toast first (if we have a message)
            if (holidayData.successMessage && window.showToast) {
                window.showToast('Time Off Added', holidayData.successMessage, 'success', {
                    duration: 2000
                });
            }

            // Schedule holiday modal opening after success message
            setTimeout(() => {
                console.log('🚀 Opening holiday modal with dates:', {
                    start: holidayData.holidayStartDate,
                    end: holidayData.holidayEndDate,
                    timeOffType: holidayData.holidayTimeOffType
                });

                // Extract user data
                const userData = extractCurrentUserData();

                // Open the holiday modal with timeOffType for auto-selection
                window.openHolidayRequestModal(
                    holidayData.holidayStartDate,
                    holidayData.holidayEndDate,
                    userData,
                    holidayData.holidayTimeOffType  // Pass timeOffType for auto-selection
                );

                console.log('✅ Holiday modal opened successfully');

            }, 1500); // 1.5 second delay to let user see the success message
        }

    } catch (error) {
        console.error('❌ Error processing holiday modal trigger data:', error);
    }
}

/**
 * Extract current user data for holiday modal
 * @returns {Object} User data object
 */
function extractCurrentUserData() {
    const userData = {};

    // Method 1: Try to get name from user badge (most reliable)
    const userBadgeSpan = document.querySelector('.badge .bi-person + span');
    if (userBadgeSpan && userBadgeSpan.textContent.trim()) {
        userData.name = userBadgeSpan.textContent.trim();
        console.log('👤 Found username from badge:', userData.name);
    }

    // Method 2: Try page title or header if badge method failed
    if (!userData.name) {
        const pageHeaders = document.querySelectorAll('h1, h2, h3, .header-title');
        pageHeaders.forEach(header => {
            const text = header.textContent;
            if (text.includes('Time Management') && text.includes('-')) {
                const parts = text.split('-');
                if (parts.length > 1) {
                    userData.name = parts[1].trim();
                    console.log('👤 Found username from header:', userData.name);
                }
            }
        });
    }

    // Fallback name for safety
    if (!userData.name) {
        userData.name = 'User';
        console.log('👤 Using fallback username');
    }

    return userData;
}

// ============================================================================
// GLOBAL ERROR HANDLING
// ============================================================================

/**
 * Set up global error handling
 */
function setupGlobalErrorHandling() {
    // Handle JavaScript errors
    window.addEventListener('error', (e) => {
        console.error('❌ JavaScript error:', e.error);

        if (TimeManagementUtilities && TimeManagementUtilities.hideLoadingOverlay) {
            TimeManagementUtilities.hideLoadingOverlay();
        }

        // Use toast system for error reporting
        if (window.showToast) {
            window.showToast('System Error',
                'An unexpected error occurred. Please refresh the page.',
                'error',
                { persistent: true });
        }
    });

    // Handle unhandled promise rejections
    window.addEventListener('unhandledrejection', (e) => {
        console.error('❌ Unhandled promise rejection:', e.reason);

        if (TimeManagementUtilities && TimeManagementUtilities.hideLoadingOverlay) {
            TimeManagementUtilities.hideLoadingOverlay();
        }

        if (window.showToast) {
            window.showToast('Network Error',
                'A network error occurred. Please check your connection.',
                'error',
                { persistent: true });
        }
    });

    console.log('✅ Global error handling configured');
}

// ============================================================================
// USER GUIDANCE
// ============================================================================

/**
 * Show keyboard shortcuts help
 */
function showKeyboardShortcuts() {
    console.log('⌨️ Keyboard shortcuts available:');
    console.log('  📅 Ctrl+← (previous month), Ctrl+→ (next month)');
    console.log('  ✏️ Double-click cells to edit');
    console.log('  ⏎ Enter to save, Escape to cancel editing');
}

// ============================================================================
// HOLIDAY MODAL INITIALIZATION
// ============================================================================

/**
 * Initialize holiday request modal if modal element exists on page
 */
function initializeHolidayModal() {
    const holidayModalElement = document.getElementById('holidayModal');
    if (holidayModalElement) {
        console.log('📋 Initializing Holiday Request Modal...');
        state.holidayModal = new HolidayRequestModal();
        state.holidayModal.init();

        // Make available globally for backward compatibility
        window.holidayRequestModal = state.holidayModal;
        window.openHolidayRequestModal = (startDate, endDate, userData, timeOffType) => {
            return state.holidayModal.open(startDate, endDate, userData, timeOffType);
        };
        window.closeHolidayModal = () => state.holidayModal.close();
        window.exportHolidayToImage = (format) => state.holidayModal.exportToImage(format);

        // Add openHolidayRequestFromForm for inline onclick compatibility
        window.openHolidayRequestFromForm = () => {
            try {
                console.log('📋 Opening holiday request modal from form...');

                // Extract user data from current page
                const userData = extractCurrentUserData();

                // Get dates from form inputs
                const startDateField = document.querySelector('input[name="startDate"]');
                const endDateField = document.querySelector('input[name="endDate"]');

                const startDate = startDateField ? startDateField.value : '';
                const endDate = endDateField ? endDateField.value : '';

                // Get selected time off type from dropdown
                const timeOffTypeSelect = document.getElementById('timeOffType');
                const selectedType = timeOffTypeSelect ? timeOffTypeSelect.value : null;

                console.log('📊 Form data:', { startDate, endDate, userData, selectedType });

                // Check if modal opener is available
                if (typeof window.openHolidayRequestModal !== 'function') {
                    console.error('❌ Holiday request modal function not available!');
                    if (window.showToast) {
                        window.showToast('Error', 'Holiday request modal is not initialized. Please refresh the page.', 'error');
                    }
                    return false;
                }

                // Open the modal
                window.openHolidayRequestModal(startDate, endDate, userData, selectedType);
                return false; // Prevent any default action
            } catch (error) {
                console.error('❌ Error opening holiday request modal:', error);
                if (window.showToast) {
                    window.showToast('Error', 'Error opening holiday request modal. Please try again.', 'error');
                }
                return false; // Prevent any default action
            }
        };

        console.log('✅ Holiday Request Modal initialized');
    }
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Restore scroll position from session storage
 */
function restoreScrollPosition() {
    const savedPosition = sessionStorage.getItem('timeManagementScrollPosition');
    if (savedPosition) {
        setTimeout(() => {
            window.scrollTo(0, parseInt(savedPosition));
            sessionStorage.removeItem('timeManagementScrollPosition');
            console.log('📜 Restored scroll position:', savedPosition);
        }, 100);
    }
}

/**
 * Toggle the temporary stops details row for a work day
 * @param {HTMLElement} toggleIcon - The chevron icon that was clicked
 */
function toggleTempStopsDetails(toggleIcon) {
    const date = toggleIcon.getAttribute('data-date');
    const detailRow = document.querySelector(`.temp-stops-detail-row[data-date="${date}"]`);

    if (!detailRow) {
        console.warn('⚠️ No detail row found for date:', date);
        return;
    }

    // Toggle visibility
    if (detailRow.style.display === 'none' || detailRow.style.display === '') {
        detailRow.style.display = 'table-row';
        toggleIcon.classList.remove('bi-chevron-down');
        toggleIcon.classList.add('bi-chevron-up');
    } else {
        detailRow.style.display = 'none';
        toggleIcon.classList.remove('bi-chevron-up');
        toggleIcon.classList.add('bi-chevron-down');
    }
}

// ============================================================================
// PERFORMANCE MONITORING
// ============================================================================

/**
 * Log initialization performance
 */
function logInitializationPerformance() {
    const loadTime = performance.now() - state.perfStart;
    console.log(`⏱️ Time Management System loaded in ${loadTime.toFixed(2)}ms`);

    // Store performance data for debugging
    window.timeManagementPerformance = {
        initializationTime: loadTime,
        modulesLoaded: state.modules.length,
        timestamp: new Date().toISOString()
    };
}

// ============================================================================
// PUBLIC API
// ============================================================================

/**
 * Get system status
 * @returns {Object} Current system status
 */
function getSystemStatus() {
    return {
        isInitialized: state.isInitialized,
        modulesLoaded: state.modules,
        moduleCount: state.modules.length,
        performance: window.timeManagementPerformance
    };
}

/**
 * Reinitialize the system
 */
function reinitialize() {
    console.log('🔄 Reinitializing Time Management System...');
    state.isInitialized = false;
    state.modules = [];
    init();
}

/**
 * Get module instance
 * @param {string} moduleName - Name of the module
 * @returns {Object|null} Module instance
 */
function getModule(moduleName) {
    const moduleMap = {
        'TimeManagementUtilities': TimeManagementUtilities,
        'StatusDisplay': StatusDisplay,
        'TimeInput': TimeInput,
        'WorkTimeDisplay': WorkTimeDisplay,
        'InlineEditing': InlineEditing,
        'TimeOffManagement': TimeOffManagement,
        'PeriodNavigation': PeriodNavigation,
        'HolidayRequestModal': HolidayRequestModal,
        'HolidayExportService': HolidayExportService
    };
    return moduleMap[moduleName] || null;
}

/**
 * Check if module is loaded
 * @param {string} moduleName - Name of the module
 * @returns {boolean} True if module is loaded
 */
function isModuleLoaded(moduleName) {
    return state.modules.includes(moduleName);
}

// ============================================================================
// DEBUG UTILITIES
// ============================================================================

/**
 * Enable debug mode
 */
function enableDebugMode() {
    window.DEBUG_MODE = true;
    localStorage.setItem('timeManagementDebug', 'true');
    console.log('🐛 Debug mode enabled');
}

/**
 * Disable debug mode
 */
function disableDebugMode() {
    window.DEBUG_MODE = false;
    localStorage.removeItem('timeManagementDebug');
    console.log('🐛 Debug mode disabled');
}

/**
 * Get debug information
 * @returns {Object} Debug information
 */
function getDebugInfo() {
    return {
        system: getSystemStatus(),
        modules: {
            TimeManagementUtilities: !!TimeManagementUtilities,
            TimeInput: !!TimeInput,
            StatusDisplay: !!StatusDisplay,
            WorkTimeDisplay: !!WorkTimeDisplay,
            InlineEditing: !!InlineEditing,
            TimeOffManagement: !!TimeOffManagement,
            PeriodNavigation: !!PeriodNavigation
        },
        editing: InlineEditing?.getCurrentState() || null,
        url: window.location.href,
        userAgent: navigator.userAgent,
        timestamp: new Date().toISOString()
    };
}

/**
 * Log debug information to console
 */
function logDebugInfo() {
    console.log('🐛 Time Management Debug Info:', getDebugInfo());
}

// ============================================================================
// DOM READY & PAGE LOAD
// ============================================================================

// Auto-initialize on DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

// Page load performance tracking
window.addEventListener('load', function() {
    const perfData = window.timeManagementPerformance;
    if (perfData) {
        console.log(`📊 Complete page load performance:`);
        console.log(`  - System init: ${perfData.initializationTime.toFixed(2)}ms`);
        console.log(`  - Modules loaded: ${perfData.modulesLoaded}`);
        console.log(`  - Total time: ${(performance.now() - state.perfStart).toFixed(2)}ms`);
    }
});

// ============================================================================
// EXPORTS
// ============================================================================

// Export modules and functions
export {
    // Core modules
    TimeManagementUtilities,
    StatusDisplay,
    TimeInput,
    WorkTimeDisplay,
    InlineEditing,
    TimeOffManagement,
    PeriodNavigation,
    HolidayRequestModal,
    HolidayExportService,

    // Main entry point
    init,

    // API functions
    getSystemStatus,
    reinitialize,
    getModule,
    isModuleLoaded,
    enableDebugMode,
    disableDebugMode,
    getDebugInfo,
    logDebugInfo,
    toggleTempStopsDetails
};

// ============================================================================
// GLOBAL COMPATIBILITY LAYER
// ============================================================================

// Make modules available globally for backward compatibility
window.TimeManagementUtilities = TimeManagementUtilities;
window.UtilitiesModule = TimeManagementUtilities; // Legacy alias
window.StatusDisplayModule = StatusDisplay;
window.TimeInputModule = TimeInput;
window.WorkTimeDisplayModule = WorkTimeDisplay;
window.InlineEditingModule = InlineEditing;
window.TimeOffManagementModule = TimeOffManagement;
window.PeriodNavigationModule = PeriodNavigation;

// Make core coordinator available
window.TimeManagementCore = {
    initialize: init,
    getSystemStatus,
    reinitialize,
    getModule,
    isModuleLoaded,
    enableDebugMode,
    disableDebugMode,
    getDebugInfo,
    logDebugInfo,
    state
};

// Legacy global functions - some inline HTML might still use these
window.handleCellDoubleClick = (cell) => InlineEditing.handleCellDoubleClick(cell);
window.showStatusDetails = (el, e) => StatusDisplay.showStatusDetails(el, e);
window.toggleTempStopsDetails = toggleTempStopsDetails;
