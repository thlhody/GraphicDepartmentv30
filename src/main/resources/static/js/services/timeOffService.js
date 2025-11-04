/**
 * Time-Off Service - Consolidated time-off type management
 *
 * This service consolidates all time-off related logic that was previously
 * duplicated across 4+ files (worktime-admin.js, constants.js, register-admin.js, etc.)
 *
 * @module services/timeOffService
 * @version 1.0.0
 * @since 2025-11-04
 *
 * ⚠️ IMPORTANT: This is the ONLY place for time-off logic.
 * Do NOT duplicate these functions in other files.
 *
 * Supported Formats:
 * - Plain types: SN, CO, CM, W, CR, CN, D, CE
 * - Special day work: SN:7.5, CO:6, CM:4, W:8, CE:6
 * - Short day: ZS-5 (missing hours)
 * - Regular work: 8h, 7.5h, or just 8
 *
 * Usage:
 *   import { TimeOffService } from './services/timeOffService.js';
 *
 *   const label = TimeOffService.getLabel('SN');
 *   const icon = TimeOffService.getIcon('CO');
 *   const isValid = TimeOffService.validate('SN:7.5');
 */

import { TIME_OFF_TYPES, SPECIAL_DAY_TYPES, ALL_TIME_OFF_TYPES } from '../core/constants.js';

/**
 * Time-Off Service Class
 * All methods are static - no instantiation needed
 */
export class TimeOffService {

    // =========================================================================
    // DISPLAY HELPERS
    // =========================================================================

    /**
     * Get display label for time-off type
     * Handles all formats: SN, SN:5, ZS-5
     *
     * @param {string} timeOffType - Time-off type code
     * @returns {string} Display label
     *
     * @example
     *   TimeOffService.getLabel('SN') // 'National Holiday'
     *   TimeOffService.getLabel('SN:5') // 'National Holiday'
     *   TimeOffService.getLabel('ZS-5') // 'Short Day (missing 5h)'
     */
    static getLabel(timeOffType) {
        if (!timeOffType) return '';

        // Handle ZS format (ZS-5 means missing 5 hours)
        if (timeOffType.startsWith('ZS-')) {
            const missingHours = timeOffType.split('-')[1];
            return `Short Day (missing ${missingHours}h)`;
        }

        // Extract base type (handle SN:5 format)
        const baseType = timeOffType.split(':')[0].toUpperCase();
        const metadata = TIME_OFF_TYPES.get(baseType);

        return metadata?.label || timeOffType;
    }

    /**
     * Get Bootstrap icon class for time-off type
     *
     * @param {string} timeOffType - Time-off type code
     * @returns {string} Bootstrap icon class
     *
     * @example
     *   TimeOffService.getIcon('SN') // 'bi bi-calendar-event text-success'
     *   TimeOffService.getIcon('ZS-5') // 'bi bi-hourglass-split text-warning'
     */
    static getIcon(timeOffType) {
        if (!timeOffType) return 'bi bi-calendar-x';

        // Handle ZS format
        if (timeOffType.startsWith('ZS-')) {
            return 'bi bi-hourglass-split text-warning';
        }

        // Extract base type
        const baseType = timeOffType.split(':')[0].toUpperCase();
        const metadata = TIME_OFF_TYPES.get(baseType);

        return metadata?.icon || 'bi bi-calendar-x';
    }

    /**
     * Get detailed description for time-off type
     *
     * @param {string} timeOffType - Time-off type code
     * @returns {string} Detailed description
     *
     * @example
     *   TimeOffService.getDescription('CR')
     *   // 'Recovery Leave - Paid day off using overtime balance...'
     */
    static getDescription(timeOffType) {
        if (!timeOffType) return '';

        // Handle ZS format
        if (timeOffType.startsWith('ZS-')) {
            const missingHours = timeOffType.split('-')[1];
            return `User worked less than schedule. Missing ${missingHours} hours will be deducted from overtime.`;
        }

        // Extract base type
        const baseType = timeOffType.split(':')[0].toUpperCase();
        const metadata = TIME_OFF_TYPES.get(baseType);

        return metadata?.description || '';
    }

    /**
     * Determine overtime type label based on date and time-off type
     * - Monday-Friday normal work: "Overtime"
     * - Saturday-Sunday (W): "Weekend Overtime"
     * - SN/CO/CE/CM with work: "Holiday Overtime"
     *
     * @param {string} dateString - Date in YYYY-MM-DD format
     * @param {string} timeOffType - Time-off type code
     * @returns {string} Overtime type label
     *
     * @example
     *   TimeOffService.getOvertimeTypeLabel('2025-01-01', 'SN')
     *   // 'Holiday Overtime'
     *
     *   TimeOffService.getOvertimeTypeLabel('2025-01-05', 'W')
     *   // 'Weekend Overtime'
     *
     *   TimeOffService.getOvertimeTypeLabel('2025-01-06', null)
     *   // 'Weekend Overtime' (Sunday)
     */
    static getOvertimeTypeLabel(dateString, timeOffType) {
        // Parse the date to determine day of week
        const date = new Date(dateString);
        const dayOfWeek = date.getDay(); // 0 = Sunday, 6 = Saturday

        // Check if it's a holiday/vacation/event with work (SN:5, CO:5, CE:5, CM:5)
        if (timeOffType) {
            const upperType = timeOffType.toUpperCase();
            if (upperType === 'SN' || upperType.startsWith('SN:') ||
                upperType === 'CO' || upperType.startsWith('CO:') ||
                upperType === 'CE' || upperType.startsWith('CE:') ||
                upperType === 'CM' || upperType.startsWith('CM:')) {
                return 'Holiday Overtime';
            }

            // Weekend work (W, W:5)
            if (upperType === 'W' || upperType.startsWith('W:')) {
                return 'Weekend Overtime';
            }
        }

        // Check if it's a weekend day (Saturday or Sunday)
        if (dayOfWeek === 0 || dayOfWeek === 6) {
            return 'Weekend Overtime';
        }

        // Normal weekday overtime
        return 'Overtime';
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    /**
     * Validate worktime value (main validation entry point)
     * Handles all formats and delegates to specific validators
     *
     * @param {string} value - Worktime value to validate
     * @returns {boolean} True if valid
     *
     * @example
     *   TimeOffService.validate('8h') // true
     *   TimeOffService.validate('SN:7.5') // true
     *   TimeOffService.validate('ZS-5') // true
     *   TimeOffService.validate('invalid') // false (shows alert)
     */
    static validate(value) {
        if (!value || typeof value !== 'string') {
            return false;
        }

        const trimmedValue = value.trim();

        // Handle special day work time format (SN:5, CO:6, CM:4, W:8, CE:6)
        if (trimmedValue.includes(':')) {
            return this.validateSpecialDayWorktime(trimmedValue);
        }

        // Handle ZS format (ZS-5 means missing 5 hours)
        if (trimmedValue.toUpperCase().startsWith('ZS-')) {
            return this.validateZSFormat(trimmedValue);
        }

        // Handle time off types (plain types without hours)
        if (ALL_TIME_OFF_TYPES.includes(trimmedValue.toUpperCase())) {
            return true;
        }

        // Handle regular work hours
        if (/^\d+(\.\d+)?h?$/.test(trimmedValue)) {
            const hours = parseFloat(trimmedValue.replace('h', ''));
            if (hours < 1 || hours > 24) {
                alert('Work hours must be between 1 and 24');
                return false;
            }
            return true;
        }

        // Invalid format
        alert(
            'Invalid format. Use:\n' +
            '- Hours: 8 or 8h\n' +
            '- Special work: SN:7.5, CO:6, CM:4, W:8, CE:6\n' +
            '- Time off: CO, CM, SN, W, CR, CN, D, CE\n' +
            '- Short day: ZS-5 (missing 5 hours)'
        );
        return false;
    }

    /**
     * Validate ZS format (short day)
     * Format: ZS-5 means user is missing 5 hours (worked less than schedule)
     *
     * @param {string} value - Value to validate (e.g., 'ZS-5')
     * @returns {boolean} True if valid
     */
    static validateZSFormat(value) {
        const parts = value.toUpperCase().split('-');

        if (parts.length !== 2 || parts[0] !== 'ZS') {
            alert('Invalid ZS format. Use: ZS-X where X is missing hours (e.g., ZS-5)');
            return false;
        }

        const missingHours = parseInt(parts[1]);
        if (isNaN(missingHours) || missingHours < 1 || missingHours > 12) {
            alert('Missing hours must be between 1 and 12 (e.g., ZS-5)');
            return false;
        }

        return true;
    }

    /**
     * Validate special day work time format
     * Supports: SN:5, CO:6, CM:4, W:8, CE:6
     *
     * @param {string} value - Value to validate (e.g., 'SN:7.5')
     * @returns {boolean} True if valid
     */
    static validateSpecialDayWorktime(value) {
        const parts = value.split(':');

        // Check format: must be exactly "TYPE:number"
        if (parts.length !== 2) {
            alert('Invalid format. Use TYPE:hours (e.g., SN:7.5, CO:6, CM:4, W:8, CE:6)');
            return false;
        }

        const type = parts[0].toUpperCase();
        const hoursStr = parts[1];

        // Validate type - must be a special day type that allows work
        if (!SPECIAL_DAY_TYPES.includes(type)) {
            alert(`Invalid type. Use ${SPECIAL_DAY_TYPES.join(', ')} (e.g., SN:7.5, CE:6)`);
            return false;
        }

        // Parse and validate hours
        const hours = parseFloat(hoursStr);
        if (isNaN(hours) || hours < 1 || hours > 24) {
            alert(`${type} work hours must be between 1 and 24 (e.g., ${type}:7.5)`);
            return false;
        }

        // Warn about partial hour discarding if applicable
        if (hours % 1 !== 0) {
            const fullHours = Math.floor(hours);
            const discarded = hours - fullHours;

            const typeLabel = this.getLabel(type);

            const confirmed = confirm(
                `${typeLabel} Work Time Processing:\n\n` +
                `Input: ${hours} hours\n` +
                `Processed: ${fullHours} full hours\n` +
                `Discarded: ${discarded.toFixed(1)} hours\n\n` +
                `Only full hours count for special day work.\n\n` +
                `Continue with ${fullHours} hours?`
            );

            if (!confirmed) {
                return false;
            }
        }

        return true;
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Check if time-off type allows work hours
     *
     * @param {string} timeOffType - Time-off type code
     * @returns {boolean} True if work hours allowed
     *
     * @example
     *   TimeOffService.allowsWork('SN') // true
     *   TimeOffService.allowsWork('CR') // false
     */
    static allowsWork(timeOffType) {
        if (!timeOffType) return false;

        // ZS format allows work (it's a short day)
        if (timeOffType.startsWith('ZS-')) {
            return true;
        }

        // Extract base type
        const baseType = timeOffType.split(':')[0].toUpperCase();
        const metadata = TIME_OFF_TYPES.get(baseType);

        return metadata?.allowsWork || false;
    }

    /**
     * Parse time-off string into components
     *
     * @param {string} value - Time-off value to parse
     * @returns {Object} Parsed components
     *
     * @example
     *   TimeOffService.parse('SN:7.5')
     *   // { type: 'SN', hours: 7.5, format: 'special_day_work' }
     *
     *   TimeOffService.parse('ZS-5')
     *   // { type: 'ZS', missingHours: 5, format: 'short_day' }
     *
     *   TimeOffService.parse('CO')
     *   // { type: 'CO', format: 'plain_time_off' }
     */
    static parse(value) {
        if (!value) {
            return { type: null, format: 'empty' };
        }

        const trimmedValue = value.trim().toUpperCase();

        // ZS format (short day)
        if (trimmedValue.startsWith('ZS-')) {
            const parts = trimmedValue.split('-');
            return {
                type: 'ZS',
                missingHours: parseInt(parts[1]) || 0,
                format: 'short_day',
                raw: value
            };
        }

        // Special day work format (TYPE:hours)
        if (trimmedValue.includes(':')) {
            const parts = trimmedValue.split(':');
            return {
                type: parts[0],
                hours: parseFloat(parts[1]) || 0,
                format: 'special_day_work',
                raw: value
            };
        }

        // Plain time-off type
        if (ALL_TIME_OFF_TYPES.includes(trimmedValue)) {
            return {
                type: trimmedValue,
                format: 'plain_time_off',
                raw: value
            };
        }

        // Regular work hours
        if (/^\d+(\.\d+)?h?$/.test(trimmedValue)) {
            return {
                type: 'WORK',
                hours: parseFloat(trimmedValue.replace('H', '')) || 0,
                format: 'regular_work',
                raw: value
            };
        }

        // Unknown format
        return {
            type: null,
            format: 'unknown',
            raw: value
        };
    }

    /**
     * Format parsed time-off for display
     *
     * @param {Object} parsed - Result from parse()
     * @returns {string} Formatted display string
     *
     * @example
     *   const parsed = TimeOffService.parse('SN:7.5');
     *   TimeOffService.format(parsed)
     *   // 'National Holiday (7.5h)'
     */
    static format(parsed) {
        if (!parsed || !parsed.type) {
            return '-';
        }

        switch (parsed.format) {
            case 'short_day':
                return `Short Day (-${parsed.missingHours}h)`;

            case 'special_day_work':
                const label = this.getLabel(parsed.type);
                return `${label} (${parsed.hours}h)`;

            case 'plain_time_off':
                return this.getLabel(parsed.type);

            case 'regular_work':
                return `${parsed.hours}h`;

            default:
                return parsed.raw;
        }
    }

    /**
     * Get all valid time-off type codes
     *
     * @returns {string[]} Array of valid codes
     */
    static getAllTypes() {
        return [...ALL_TIME_OFF_TYPES];
    }

    /**
     * Get all special day types (that allow work hours)
     *
     * @returns {string[]} Array of special day codes
     */
    static getSpecialDayTypes() {
        return [...SPECIAL_DAY_TYPES];
    }

    /**
     * Check if a type code is valid
     *
     * @param {string} type - Type code to check
     * @returns {boolean} True if valid
     */
    static isValidType(type) {
        if (!type) return false;

        const upperType = type.toUpperCase();

        // Check ZS format
        if (upperType.startsWith('ZS-')) {
            return this.validateZSFormat(type);
        }

        // Check if it's a known type
        return ALL_TIME_OFF_TYPES.includes(upperType);
    }
}

// Export as default for convenience
export default TimeOffService;
