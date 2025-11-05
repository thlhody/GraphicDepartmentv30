/**
 * TimeManagementUtilities.js
 *
 * Common helper functions and utilities for time management modules.
 * Provides shared functionality with no duplications - leverages Phase 1 & 2 infrastructure.
 *
 * @module features/time-management/TimeManagementUtilities
 */

import { formatMinutesToHours } from '../../core/utils.js';
import { TimeOffService } from '../../services/timeOffService.js';

/**
 * TimeManagementUtilities class
 * Provides utility functions for time management
 */
export class TimeManagementUtilities {
    /**
     * Initialize utilities (for consistency with other modules)
     */
    static initialize() {
        console.log('Initializing Time Management Utilities...');
        console.log('✅ Time Management Utilities initialized');
    }

    // ========================================================================
    // TIME FORMATTING UTILITIES
    // ========================================================================

    /**
     * Convert various time formats to 24-hour format
     * @param {string} timeString - Input time string
     * @returns {string} 24-hour formatted time (HH:MM)
     */
    static convertTo24Hour(timeString) {
        if (!timeString || timeString.trim() === '') {
            return '';
        }

        const trimmed = timeString.trim();

        // Already in 24-hour format
        if (/^([01]?[0-9]|2[0-3]):[0-5][0-9]$/.test(trimmed)) {
            return this._ensure24HourFormat(trimmed);
        }

        return trimmed;
    }

    /**
     * Convert 24-hour to 12-hour format
     * @param {string} timeString - 24-hour time string
     * @returns {string} 12-hour formatted time
     */
    static convertTo12Hour(timeString) {
        if (!timeString) return '';

        const timeParts = timeString.split(':');
        if (timeParts.length !== 2) return timeString;

        let hours = parseInt(timeParts[0]);
        const minutes = timeParts[1];

        const period = hours >= 12 ? 'PM' : 'AM';
        hours = hours % 12 || 12;

        return `${hours}:${minutes} ${period}`;
    }

    /**
     * Ensure proper 24-hour format (pad with zeros)
     * @private
     */
    static _ensure24HourFormat(timeString) {
        const [hours, minutes] = timeString.split(':');
        return `${hours.padStart(2, '0')}:${minutes.padStart(2, '0')}`;
    }

    /**
     * Format minutes to HH:MM format
     * DELEGATES to core/utils.js to avoid duplication
     */
    static formatMinutesToHours(minutes) {
        return formatMinutesToHours(minutes);
    }

    /**
     * Format minutes to readable format (e.g., "2h 30m")
     * @param {number} minutes - Minutes to format
     * @returns {string} Human readable time string
     */
    static formatMinutesToReadable(minutes) {
        if (!minutes || minutes === 0) return '0h';

        const hours = Math.floor(minutes / 60);
        const mins = minutes % 60;

        if (mins === 0) {
            return `${hours}h`;
        }
        return `${hours}h ${mins}m`;
    }

    // ========================================================================
    // VALIDATION UTILITIES
    // ========================================================================

    /**
     * Validate field values based on field type
     * @param {string} field - Field name
     * @param {string} value - Value to validate
     * @returns {string|null} Error message or null if valid
     */
    static validateFieldValue(field, value) {
        if (!value || value.trim() === '') {
            return null; // Empty values are allowed (clears the field)
        }

        switch (field) {
            case 'startTime':
            case 'endTime':
                return this._validateTimeFormat(value);

            case 'timeOff':
                if (!TimeOffService.isTimeOffType(value)) {
                    return `Invalid time off type. Use ${Array.from(TimeOffService.getAllTypes()).join(', ')}`;
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
    }

    /**
     * Basic time format validation
     * @private
     */
    static _validateTimeFormat(timeString) {
        const pattern = /^([01]?[0-9]|2[0-3]):[0-5][0-9]$/;
        if (!pattern.test(timeString)) {
            return 'Invalid time format. Use 24-hour format (e.g., 08:30, 13:45)';
        }
        return null;
    }

    // ========================================================================
    // DOM UTILITIES
    // ========================================================================

    /**
     * Show loading overlay
     */
    static showLoadingOverlay() {
        const overlay = document.getElementById('loadingOverlay');
        if (overlay) {
            overlay.classList.remove('d-none');
        }
    }

    /**
     * Hide loading overlay
     */
    static hideLoadingOverlay() {
        const overlay = document.getElementById('loadingOverlay');
        if (overlay) {
            overlay.classList.add('d-none');
        }
    }

    /**
     * Get cell content container (handles both cell-value and cell-content structures)
     * @param {HTMLElement} cell - Cell element
     * @returns {HTMLElement} Content container element
     */
    static getCellContentContainer(cell) {
        let cellValue = cell.querySelector('.cell-value');
        if (!cellValue) {
            cellValue = cell.querySelector('.cell-content');
        }
        if (!cellValue) {
            cellValue = cell; // Fallback to the cell itself
        }
        return cellValue;
    }

    /**
     * Extract overtime minutes from text
     * @param {string} text - Text containing time information
     * @returns {number} Minutes extracted from text
     */
    static extractOvertimeMinutes(text) {
        if (!text) return 0;

        // Match patterns like "2:30", "02:30", etc.
        const timeMatch = text.match(/(\d{1,2}):(\d{2})/);
        if (timeMatch) {
            const hours = parseInt(timeMatch[1]);
            const minutes = parseInt(timeMatch[2]);
            return (hours * 60) + minutes;
        }

        return 0;
    }

    // ========================================================================
    // DATE UTILITIES
    // ========================================================================

    /**
     * Get month name from number
     * @param {number} monthNumber - Month number (1-12)
     * @returns {string} Month name
     */
    static getMonthName(monthNumber) {
        const months = [
            'January', 'February', 'March', 'April', 'May', 'June',
            'July', 'August', 'September', 'October', 'November', 'December'
        ];
        return months[monthNumber - 1] || 'Unknown';
    }

    /**
     * Validate date format (YYYY-MM-DD)
     * @param {string} dateString - Date string to validate
     * @returns {boolean} True if valid date format
     */
    static validateDateFormat(dateString) {
        return /^\d{4}-\d{2}-\d{2}$/.test(dateString);
    }

    // ========================================================================
    // SPECIAL DAY UTILITIES (uses TimeOffService)
    // ========================================================================

    /**
     * Check if this is a special day with work
     * @param {Object} rowData - Row data object
     * @returns {boolean} True if special day with work
     */
    static isSpecialDayWithWork(rowData) {
        return rowData &&
            rowData.isSpecialDay &&
            rowData.hasWork &&
            rowData.totalOvertimeMinutes > 0;
    }

    /**
     * Get time off CSS class
     * DELEGATES to TimeOffService
     */
    static getTimeOffClass(timeOffType) {
        // Map TimeOffService classes to legacy classes
        const iconClass = TimeOffService.getIcon(timeOffType);

        switch (timeOffType) {
            case 'SN': return 'holiday';
            case 'CO': return 'vacation';
            case 'CM': return 'medical';
            case 'W': return 'weekend';
            default: return 'time-off-display';
        }
    }

    /**
     * Get time off label
     * DELEGATES to TimeOffService
     */
    static getTimeOffLabel(timeOffType) {
        return TimeOffService.getLabel(timeOffType);
    }

    /**
     * Get special day type from CSS class
     * @param {string} className - CSS class string
     * @returns {string} Special day type
     */
    static getSpecialDayTypeFromClass(className) {
        if (className.includes('sn-work-display')) return 'SN';
        if (className.includes('co-work-display')) return 'CO';
        if (className.includes('cm-work-display')) return 'CM';
        if (className.includes('w-work-display')) return 'W';
        return 'Special Day';
    }

    /**
     * Extract hours from display text
     * @param {string} displayText - Display text
     * @returns {number} Hours extracted
     */
    static extractHoursFromDisplay(displayText) {
        const match = displayText.match(/(\d+)$/);
        return match ? parseInt(match[1]) : 0;
    }

    /**
     * Generate tooltip for special day
     * @param {string} type - Special day type
     * @param {number} hours - Number of hours
     * @returns {string} Tooltip text
     */
    static generateSpecialDayTooltip(type, hours) {
        const typeName = this.getTimeOffLabel(type);
        return hours > 0 ?
            `${typeName} with ${hours} hour${hours !== 1 ? 's' : ''} overtime work` :
            `${typeName}`;
    }

    // ========================================================================
    // BOOTSTRAP UTILITIES
    // ========================================================================

    /**
     * Initialize Bootstrap tooltips
     */
    static initializeTooltips() {
        if (typeof bootstrap !== 'undefined' && bootstrap.Tooltip) {
            const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
            tooltipTriggerList.map(function (tooltipTriggerEl) {
                return new bootstrap.Tooltip(tooltipTriggerEl);
            });
        }
    }

    /**
     * Safely dispose Bootstrap modal
     * @param {HTMLElement} modalElement - Modal element
     */
    static disposeModal(modalElement) {
        if (!modalElement) return;

        const existingModalInstance = bootstrap.Modal.getInstance(modalElement);
        if (existingModalInstance) {
            console.log('Disposing existing modal instance');
            existingModalInstance.dispose();
        }
    }

    // ========================================================================
    // DEBUGGING UTILITIES
    // ========================================================================

    /**
     * Log debug information
     * @param {string} module - Module name
     * @param {string} action - Action being performed
     * @param {Object} data - Data to log
     */
    static debugLog(module, action, data = {}) {
        if (window.DEBUG_MODE || localStorage.getItem('timeManagementDebug')) {
            console.log(`[${module}] ${action}:`, data);
        }
    }

    /**
     * Performance timing helper
     * @param {string} label - Performance label
     * @returns {Function} Function to end timing
     */
    static startPerformanceTimer(label) {
        const start = performance.now();
        return () => {
            const end = performance.now();
            console.log(`⏱️ ${label} took ${(end - start).toFixed(2)}ms`);
        };
    }

    // ========================================================================
    // ERROR HANDLING
    // ========================================================================

    /**
     * Handle and log errors consistently
     * @param {Error} error - Error object
     * @param {string} context - Context where error occurred
     * @param {Object} additionalData - Additional debug data
     */
    static handleError(error, context, additionalData = {}) {
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
    }

    /**
     * Convert technical errors to user-friendly messages
     * @private
     */
    static _getUserFriendlyErrorMessage(error, context) {
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
}
