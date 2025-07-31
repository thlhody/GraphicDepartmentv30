/**
 * Time Input Module - Handles all 24-hour time input functionality
 * Provides consistent 24-hour time input across the application
 *
 * Usage:
 * const timeInput = TimeInputModule.create24HourEditor(currentValue);
 * const isValid = TimeInputModule.validateTime(timeString);
 * const converted = TimeInputModule.convertTo24Hour(timeString);
 */

const TimeInputModule = {

    // Configuration
    config: {
        placeholder: 'HH:MM (24hr)',
        helpText: '24-hour format: 08:30, 13:45, etc. You can type 0830 or 1345',
        maxLength: 5,
        width: '90px',
        fontFamily: 'monospace',

        // Visual feedback colors
        colors: {
            valid: {
                border: '#28a745',
                background: '#f8fff8'
            },
            invalid: {
                border: '#dc3545',
                background: '#fff8f8'
            },
            default: {
                border: '#ced4da',
                background: '#fff'
            }
        },

        // Validation patterns
        patterns: {
            time24Hour: /^([01]?[0-9]|2[0-3]):[0-5][0-9]$/,
            time12Hour: /^(\d{1,2}):?(\d{2})?\s*(AM|PM)$/i,
            digitsOnly: /^\d{3,4}$/,
            partialHours: /^\d{1,2}$/
        }
    },

    /**
     * Create enhanced 24-hour time editor input
     * @param {string} currentValue - Initial value for the input
     * @param {object} options - Optional configuration overrides
     * @returns {HTMLInputElement} Configured time input element
     */
    create24HourEditor(currentValue = '', options = {}) {
        const config = { ...this.config, ...options };

        const editor = document.createElement('input');
        editor.className = 'inline-editor form-control form-control-sm time-24hr-input';
        editor.type = 'text';

        // Set up attributes
        this._setupAttributes(editor, config);

        // Set up event listeners
        this._setupEventListeners(editor, config);

        // Set up styling
        this._setupStyling(editor, config);

        // Set initial value
        if (currentValue) {
            const time24 = this.convertTo24Hour(currentValue);
            editor.value = time24;
        }

        return editor;
    },

    /**
     * Set up input attributes
     * @private
     */
    _setupAttributes(editor, config) {
        editor.setAttribute('pattern', '^([01]?[0-9]|2[0-3]):[0-5][0-9]$');
        editor.setAttribute('placeholder', config.placeholder);
        editor.setAttribute('title', config.helpText);
        editor.setAttribute('maxlength', config.maxLength);
        editor.setAttribute('autocomplete', 'off');
        editor.setAttribute('data-time-input', 'true');
    },

    /**
     * Set up event listeners for time input
     * @private
     */
    _setupEventListeners(editor, config) {
        // Real-time formatting and validation
        editor.addEventListener('input', (e) => {
            this._handleInput(e, config);
        });

        // Handle paste events
        editor.addEventListener('paste', (e) => {
            setTimeout(() => {
                this._handlePaste(e, config);
            }, 10);
        });

        // Handle focus for better UX
        editor.addEventListener('focus', (e) => {
            e.target.select(); // Select all text on focus
        });

        // Handle blur for final validation
        editor.addEventListener('blur', (e) => {
            this._handleBlur(e, config);
        });

        // Prevent invalid characters
        editor.addEventListener('keypress', (e) => {
            this._handleKeyPress(e);
        });
    },

    /**
     * Set up styling for the input
     * @private
     */
    _setupStyling(editor, config) {
        editor.style.width = config.width;
        editor.style.textAlign = 'center';
        editor.style.fontFamily = config.fontFamily;
        editor.style.letterSpacing = '1px';
    },

    /**
     * Handle input events - format and validate in real-time
     * @private
     */
    _handleInput(event, config) {
        let value = event.target.value.replace(/[^\d]/g, ''); // Remove non-digits

        // FIXED: Better logic for auto-formatting with colon
        if (value.length >= 3) {
            if (value.length === 3) {
                // For 3 digits like "830" -> "8:30"
                value = value.substring(0, 1) + ':' + value.substring(1, 3);
            } else if (value.length >= 4) {
                // For 4+ digits like "1330" -> "13:30"
                value = value.substring(0, 2) + ':' + value.substring(2, 4);
            }
        }

        event.target.value = value;

        // Visual feedback
        this._updateVisualFeedback(event.target, value, config);
    },

    /**
     * Handle paste events - convert various formats
     * @private
     */
    _handlePaste(event, config) {
        let value = event.target.value;
        const converted = this.convertTo24Hour(value);

        if (converted !== value && this.validateTime(converted)) {
            event.target.value = converted;
            this._updateVisualFeedback(event.target, converted, config);
        }
    },

    /**
     * Handle blur events - final validation and cleanup
     * @private
     */
    _handleBlur(event, config) {
        const value = event.target.value;

        if (value && !this.validateTime(value)) {
            // Try to auto-correct common mistakes
            const corrected = this._attemptAutoCorrection(value);
            if (corrected && this.validateTime(corrected)) {
                event.target.value = corrected;
                this._updateVisualFeedback(event.target, corrected, config);
            } else {
                // Reset to default styling if invalid
                this._updateVisualFeedback(event.target, '', config);
            }
        }
    },

    /**
     * Handle key press events - prevent invalid characters
     * @private
     */
    _handleKeyPress(event) {
        const char = String.fromCharCode(event.which);
        const currentValue = event.target.value;

        // Allow digits, colon, and control keys
        if (!/[0-9:]/.test(char) && event.which !== 8 && event.which !== 46) {
            event.preventDefault();
        }

        // Prevent more than one colon
        if (char === ':' && currentValue.includes(':')) {
            event.preventDefault();
        }
    },

    /**
     * Update visual feedback based on validation
     * @private
     */
    _updateVisualFeedback(input, value, config) {
        if (value.length === 5 && this.validateTime(value)) {
            // Valid time
            input.style.borderColor = config.colors.valid.border;
            input.style.backgroundColor = config.colors.valid.background;
            input.classList.remove('is-invalid');
            input.classList.add('is-valid');
        } else if (value.length === 5) {
            // Invalid time
            input.style.borderColor = config.colors.invalid.border;
            input.style.backgroundColor = config.colors.invalid.background;
            input.classList.remove('is-valid');
            input.classList.add('is-invalid');
        } else {
            // Incomplete or empty
            input.style.borderColor = config.colors.default.border;
            input.style.backgroundColor = config.colors.default.background;
            input.classList.remove('is-valid', 'is-invalid');
        }
    },

    /**
     * Attempt to auto-correct common time input mistakes
     * @private
     */
    _attemptAutoCorrection(value) {
        // Remove all non-digits and try to format
        const digits = value.replace(/[^\d]/g, '');

        if (digits.length === 3) {
            // "830" -> "08:30"
            return `0${digits.charAt(0)}:${digits.substring(1)}`;
        } else if (digits.length === 4) {
            // "1330" -> "13:30"
            return `${digits.substring(0, 2)}:${digits.substring(2)}`;
        } else if (digits.length === 2) {
            // "08" -> "08:00"
            return `${digits}:00`;
        } else if (digits.length === 1) {
            // "8" -> "08:00"
            return `0${digits}:00`;
        }

        return null;
    },

    /**
     * Validate 24-hour time format
     * @param {string} timeString - Time string to validate
     * @returns {boolean} True if valid 24-hour time
     */
    validateTime(timeString) {
        if (!timeString || typeof timeString !== 'string') {
            return false;
        }

        const trimmed = timeString.trim();

        // Check pattern
        if (!this.config.patterns.time24Hour.test(trimmed)) {
            return false;
        }

        // Validate actual time values
        const [hours, minutes] = trimmed.split(':').map(Number);
        return hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59;
    },

    /**
     * Convert various time formats to 24-hour format
     * @param {string} timeString - Input time string
     * @returns {string} 24-hour formatted time (HH:MM)
     */
    convertTo24Hour(timeString) {
        if (!timeString || timeString.trim() === '') {
            return '';
        }

        const trimmed = timeString.trim();

        // Already in 24-hour format
        if (this.config.patterns.time24Hour.test(trimmed)) {
            return this._ensure24HourFormat(trimmed);
        }

        // Handle 4-digit format without colon
        if (this.config.patterns.digitsOnly.test(trimmed)) {
            return this._convertDigitsToTime(trimmed);
        }

        // Handle 12-hour format
        const time12Match = trimmed.match(this.config.patterns.time12Hour);
        if (time12Match) {
            return this._convert12HourTo24Hour(time12Match);
        }

        // Handle partial input
        if (this.config.patterns.partialHours.test(trimmed)) {
            return this._handlePartialInput(trimmed);
        }

        return trimmed; // Return original if no conversion possible
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
     * Convert digit-only string to time format
     * @private
     */
    _convertDigitsToTime(digits) {
        if (digits.length === 3) {
            // "830" -> "08:30" (pad the hour with zero)
            return `0${digits.charAt(0)}:${digits.substring(1)}`;
        } else if (digits.length === 4) {
            // "1330" -> "13:30"
            return `${digits.substring(0, 2)}:${digits.substring(2)}`;
        } else if (digits.length === 2) {
            // "08" -> "08:" (partial input)
            return `${digits}:`;
        } else if (digits.length === 1) {
            // "8" -> "08:" (single digit hour)
            return `0${digits}:`;
        }
        return digits;
    },

    /**
     * Convert 12-hour format to 24-hour format
     * @private
     */
    _convert12HourTo24Hour(match) {
        let hours = parseInt(match[1]);
        const minutes = match[2] || '00';
        const period = match[3].toUpperCase();

        if (period === 'AM' && hours === 12) {
            hours = 0;
        } else if (period === 'PM' && hours !== 12) {
            hours += 12;
        }

        return `${hours.toString().padStart(2, '0')}:${minutes}`;
    },

    /**
     * Handle partial input (just hours)
     * @private
     */
    _handlePartialInput(hoursString) {
        const hours = parseInt(hoursString);
        if (hours >= 0 && hours <= 23) {
            return hoursString.padStart(2, '0') + ':';
        }
        return hoursString;
    },

    /**
     * Format time for display
     * @param {string} timeString - Time to format
     * @param {string} format - Format type ('24hour', '12hour')
     * @returns {string} Formatted time string
     */
    formatTime(timeString, format = '24hour') {
        if (!this.validateTime(timeString)) {
            return timeString;
        }

        if (format === '12hour') {
            return this._convertTo12Hour(timeString);
        }

        return this._ensure24HourFormat(timeString);
    },

    /**
     * Convert 24-hour to 12-hour format
     * @private
     */
    _convertTo12Hour(timeString) {
        const [hours, minutes] = timeString.split(':').map(Number);
        const period = hours >= 12 ? 'PM' : 'AM';
        const displayHours = hours % 12 || 12;

        return `${displayHours}:${minutes.toString().padStart(2, '0')} ${period}`;
    },

    /**
     * Get validation error message for invalid time
     * @param {string} timeString - Time string to check
     * @returns {string|null} Error message or null if valid
     */
    getValidationError(timeString) {
        if (!timeString || timeString.trim() === '') {
            return null; // Empty is allowed
        }

        if (!this.validateTime(timeString)) {
            return 'Invalid time format. Use 24-hour format (e.g., 08:30, 13:45, 17:00)';
        }

        return null;
    },

    /**
     * Create help text element for time input
     * @param {string} fieldType - Type of field ('startTime', 'endTime')
     * @returns {HTMLElement} Help text element
     */
    createHelpText(fieldType = 'time') {
        const helpText = document.createElement('div');
        helpText.className = 'editing-help time-input-help';

        const messages = {
            startTime: this.config.helpText,
            endTime: this.config.helpText,
            time: this.config.helpText
        };

        helpText.innerHTML = `
            ${messages[fieldType] || messages.time}
            <br><small>Press Enter to save, Escape to cancel</small>
        `;

        // Styling
        Object.assign(helpText.style, {
            position: 'absolute',
            bottom: '-50px',
            left: '50%',
            transform: 'translateX(-50%)',
            backgroundColor: '#212529',
            color: 'white',
            padding: '0.5rem',
            borderRadius: '0.375rem',
            fontSize: '0.75rem',
            whiteSpace: 'nowrap',
            zIndex: '1000',
            boxShadow: '0 2px 8px rgba(0,0,0,0.2)'
        });

        return helpText;
    }
};

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = TimeInputModule;
}

// Make available globally
window.TimeInputModule = TimeInputModule;