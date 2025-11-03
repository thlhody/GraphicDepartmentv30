/**
 * Utilities Module - Common helper functions and utilities
 * Provides shared functionality across all time management modules
 */

const UtilitiesModule = {

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize utilities module (placeholder for consistency)
     */
    initialize() {
        console.log('Initializing Utilities Module...');
        // No specific initialization needed for utilities
        console.log('✅ Utilities Module initialized');
    },

    // ========================================================================
    // TIME FORMATTING UTILITIES
    // ========================================================================

    /**
     * Convert various time formats to 24-hour format
     * @param {string} timeString - Input time string
     * @returns {string} 24-hour formatted time (HH:MM)
     */
    convertTo24Hour(timeString) {
        // Delegate to TimeInputModule if available
        if (window.TimeInputModule && typeof window.TimeInputModule.convertTo24Hour === 'function') {
            return window.TimeInputModule.convertTo24Hour(timeString);
        }

        // Fallback implementation
        if (!timeString || timeString.trim() === '') {
            return '';
        }

        const trimmed = timeString.trim();

        // Already in 24-hour format
        if (/^([01]?[0-9]|2[0-3]):[0-5][0-9]$/.test(trimmed)) {
            return this._ensure24HourFormat(trimmed);
        }

        return trimmed;
    },

    /**
     * Convert 24-hour to 12-hour format
     * @param {string} timeString - 24-hour time string
     * @returns {string} 12-hour formatted time
     */
    convertTo12Hour(timeString) {
        if (!timeString) return '';

        const timeParts = timeString.split(':');
        if (timeParts.length !== 2) return timeString;

        let hours = parseInt(timeParts[0]);
        const minutes = timeParts[1];

        const period = hours >= 12 ? 'PM' : 'AM';
        hours = hours % 12 || 12;

        return `${hours}:${minutes} ${period}`;
    },

    /**
     * Ensure proper 24-hour format (pad with zeros)
     * @private
     */
    _ensure24HourFormat(timeString) {
        const [hours, minutes] = timeString.split(':');
        return `${hours.padStart(2, '0')}:${minutes.padStart(2, '0')}`;
    },

    /**
     * Format minutes to HH:MM format
     * @param {number} minutes - Minutes to format
     * @returns {string} Formatted time string
     */
    formatMinutesToHours(minutes) {
        if (!minutes || minutes === 0) return '0:00';

        const hours = Math.floor(minutes / 60);
        const mins = minutes % 60;

        return `${hours}:${mins.toString().padStart(2, '0')}`;
    },

    /**
     * Format minutes to readable format (e.g., "2h 30m")
     * @param {number} minutes - Minutes to format
     * @returns {string} Human readable time string
     */
    formatMinutesToReadable(minutes) {
        if (!minutes || minutes === 0) return '0h';

        const hours = Math.floor(minutes / 60);
        const mins = minutes % 60;

        if (mins === 0) {
            return `${hours}h`;
        }
        return `${hours}h ${mins}m`;
    },

    // ========================================================================
    // VALIDATION UTILITIES
    // ========================================================================

    /**
     * Validate field values based on field type
     * @param {string} field - Field name
     * @param {string} value - Value to validate
     * @returns {string|null} Error message or null if valid
     */
    validateFieldValue(field, value) {
        if (!value || value.trim() === '') {
            return null; // Empty values are allowed (clears the field)
        }

        switch (field) {
            case 'startTime':
            case 'endTime':
                // Use TimeInputModule validation if available
                if (window.TimeInputModule && typeof window.TimeInputModule.getValidationError === 'function') {
                    return window.TimeInputModule.getValidationError(value);
                }
                // Fallback validation
                return this._validateTimeFormat(value);

            case 'timeOff':
                if (value !== 'CO' && value !== 'CM') {
                    return 'Invalid time off type. Use CO for vacation or CM for medical';
                }
                break;

            case 'tempStop':
                const minutes = parseInt(value);
                if (isNaN(minutes) || minutes < 0) {
                    return 'Temporary stop must be a positive number';
                }
                if (minutes > 720) {
                    return 'Temporary stop cannot exceed 12 hours (720 minutes)';
                }
                break;

            default:
                return null;
        }

        return null;
    },

    /**
     * Basic time format validation
     * @private
     */
    _validateTimeFormat(timeString) {
        const pattern = /^([01]?[0-9]|2[0-3]):[0-5][0-9]$/;
        if (!pattern.test(timeString)) {
            return 'Invalid time format. Use 24-hour format (e.g., 08:30, 13:45)';
        }
        return null;
    },

    // ========================================================================
    // DOM UTILITIES
    // ========================================================================

    /**
     * Show loading overlay
     */
    showLoadingOverlay() {
        const overlay = document.getElementById('loadingOverlay');
        if (overlay) {
            overlay.classList.remove('d-none');
        }
    },

    /**
     * Hide loading overlay
     */
    hideLoadingOverlay() {
        const overlay = document.getElementById('loadingOverlay');
        if (overlay) {
            overlay.classList.add('d-none');
        }
    },

    /**
     * Get cell content container (handles both cell-value and cell-content structures)
     * @param {HTMLElement} cell - Cell element
     * @returns {HTMLElement} Content container element
     */
    getCellContentContainer(cell) {
        let cellValue = cell.querySelector('.cell-value');
        if (!cellValue) {
            cellValue = cell.querySelector('.cell-content');
        }
        if (!cellValue) {
            cellValue = cell; // Fallback to the cell itself
        }
        return cellValue;
    },

    /**
     * Extract overtime minutes from text
     * @param {string} text - Text containing time information
     * @returns {number} Minutes extracted from text
     */
    extractOvertimeMinutes(text) {
        if (!text) return 0;

        // Match patterns like "2:30", "02:30", etc.
        const timeMatch = text.match(/(\d{1,2}):(\d{2})/);
        if (timeMatch) {
            const hours = parseInt(timeMatch[1]);
            const minutes = parseInt(timeMatch[2]);
            return (hours * 60) + minutes;
        }

        return 0;
    },

    // ========================================================================
    // DATE UTILITIES
    // ========================================================================

    /**
     * Get month name from number
     * @param {number} monthNumber - Month number (1-12)
     * @returns {string} Month name
     */
    getMonthName(monthNumber) {
        const months = [
            'January', 'February', 'March', 'April', 'May', 'June',
            'July', 'August', 'September', 'October', 'November', 'December'
        ];
        return months[monthNumber - 1] || 'Unknown';
    },

    /**
     * Validate date format (YYYY-MM-DD)
     * @param {string} dateString - Date string to validate
     * @returns {boolean} True if valid date format
     */
    validateDateFormat(dateString) {
        return /^\d{4}-\d{2}-\d{2}$/.test(dateString);
    },

    // ========================================================================
    // SPECIAL DAY UTILITIES
    // ========================================================================

    /**
     * Check if this is a special day with work
     * @param {Object} rowData - Row data object
     * @returns {boolean} True if special day with work
     */
    isSpecialDayWithWork(rowData) {
        return rowData &&
        rowData.isSpecialDay &&
        rowData.hasWork &&
        rowData.totalOvertimeMinutes > 0;
    },

    /**
     * Get time off CSS class
     * @param {string} timeOffType - Time off type
     * @returns {string} CSS class name
     */
    getTimeOffClass(timeOffType) {
        switch (timeOffType) {
            case 'SN': return 'holiday';
            case 'CO': return 'vacation';
            case 'CM': return 'medical';
            case 'W': return 'weekend';
            default: return 'time-off-display';
        }
    },

    /**
     * Get time off label
     * @param {string} timeOffType - Time off type
     * @returns {string} Human readable label
     */
    getTimeOffLabel(timeOffType) {
        switch (timeOffType) {
            case 'SN': return 'National Holiday';
            case 'CO': return 'Vacation Day';
            case 'CM': return 'Medical Leave';
            case 'W': return 'Weekend';
            default: return timeOffType;
        }
    },

    /**
     * Get special day type from CSS class
     * @param {string} className - CSS class string
     * @returns {string} Special day type
     */
    getSpecialDayTypeFromClass(className) {
        if (className.includes('sn-work-display')) return 'SN';
        if (className.includes('co-work-display')) return 'CO';
        if (className.includes('cm-work-display')) return 'CM';
        if (className.includes('w-work-display')) return 'W';
        return 'Special Day';
    },

    /**
     * Extract hours from display text
     * @param {string} displayText - Display text
     * @returns {number} Hours extracted
     */
    extractHoursFromDisplay(displayText) {
        const match = displayText.match(/(\d+)$/);
        return match ? parseInt(match[1]) : 0;
    },

    /**
     * Generate tooltip for special day
     * @param {string} type - Special day type
     * @param {number} hours - Number of hours
     * @returns {string} Tooltip text
     */
    generateSpecialDayTooltip(type, hours) {
        const typeNames = {
            'SN': 'National Holiday',
            'CO': 'Time Off Day',
            'CM': 'Medical Leave',
            'W': 'Weekend'
        };

        const typeName = typeNames[type] || 'Special Day';
        return hours > 0 ?
        `${typeName} with ${hours} hour${hours !== 1 ? 's' : ''} overtime work` :
        `${typeName}`;
    },

    // ========================================================================
    // BOOTSTRAP UTILITIES
    // ========================================================================

    /**
     * Initialize Bootstrap tooltips
     */
    initializeTooltips() {
        if (typeof bootstrap !== 'undefined' && bootstrap.Tooltip) {
            const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
            tooltipTriggerList.map(function (tooltipTriggerEl) {
                return new bootstrap.Tooltip(tooltipTriggerEl);
            });
        }
    },

    /**
     * Safely dispose Bootstrap modal
     * @param {HTMLElement} modalElement - Modal element
     */
    disposeModal(modalElement) {
        if (!modalElement) return;

        const existingModalInstance = bootstrap.Modal.getInstance(modalElement);
        if (existingModalInstance) {
            console.log('Disposing existing modal instance');
            existingModalInstance.dispose();
        }
    },

    // ========================================================================
    // DEBUGGING UTILITIES
    // ========================================================================

    /**
     * Log debug information
     * @param {string} module - Module name
     * @param {string} action - Action being performed
     * @param {Object} data - Data to log
     */
    debugLog(module, action, data = {}) {
        if (window.DEBUG_MODE || localStorage.getItem('timeManagementDebug')) {
            console.log(`[${module}] ${action}:`, data);
        }
    },

    /**
     * Performance timing helper
     * @param {string} label - Performance label
     * @returns {Function} Function to end timing
     */
    startPerformanceTimer(label) {
        const start = performance.now();
        return () => {
            const end = performance.now();
            console.log(`⏱️ ${label} took ${(end - start).toFixed(2)}ms`);
        };
    },

    // ========================================================================
    // ERROR HANDLING
    // ========================================================================

    /**
     * Handle and log errors consistently
     * @param {Error} error - Error object
     * @param {string} context - Context where error occurred
     * @param {Object} additionalData - Additional debug data
     */
    handleError(error, context, additionalData = {}) {
        console.error(`❌ Error in ${context}:`, error);

        if (additionalData && Object.keys(additionalData).length > 0) {
            console.error('Additional context:', additionalData);
        }

        // Use toast system if available
        if (window.showToast) {
            const userMessage = this._getUserFriendlyErrorMessage(error, context);
            window.showToast('Error', userMessage, 'error', { duration: 4000 });
        }

        // Hide loading overlay on error
        this.hideLoadingOverlay();
    },

    /**
     * Convert technical errors to user-friendly messages
     * @private
     */
    _getUserFriendlyErrorMessage(error, context) {
        if (error.message.includes('date format')) {
            return 'Invalid date format. Please try refreshing the page.';
        } else if (error.message.includes('Server error')) {
            return 'Server error. Please try again.';
        } else if (error.message.includes('data-date')) {
            return 'Page data error. Please refresh the page.';
        } else if (context.includes('network') || error.message.includes('fetch')) {
            return 'Network error. Please check your connection.';
        } else {
            return error.message || 'An unexpected error occurred.';
        }
    }
};

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = UtilitiesModule;
}

// Make available globally
window.UtilitiesModule = UtilitiesModule;