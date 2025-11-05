/**
 * WorktimeValidator.js
 *
 * Validates worktime values for admin worktime management.
 * Handles validation for regular hours, special day work time (SN:5, CO:6, etc.),
 * ZS format (short days), and time-off types.
 *
 * @module features/worktime/admin/WorktimeValidator
 */

import { TimeOffService } from '../../../services/timeOffService.js';

/**
 * WorktimeValidator class
 * Validates worktime input values
 */
export class WorktimeValidator {
    /**
     * Validate worktime value
     * @param {string} value - Value to validate
     * @returns {boolean} True if valid
     */
    validateWorktimeValue(value) {
        // Handle special day work time format (SN:5, CO:6, CM:4, W:8, CE:6)
        if (value.includes(':')) {
            return this.validateSpecialDayWorktime(value);
        }

        // Handle ZS format (ZS-5 means missing 5 hours)
        if (value.toUpperCase().startsWith('ZS-')) {
            return this.validateZSFormat(value);
        }

        // Handle time off types using TimeOffService
        if (TimeOffService.isTimeOffType(value)) {
            return true; // Valid time off type
        }

        // Handle regular work hours
        if (/^\d+(\.\d+)?h?$/.test(value)) {
            const hours = parseFloat(value.replace('h', ''));
            if (hours < 1 || hours > 24) {
                alert('Work hours must be between 1 and 24');
                return false;
            }
            return true;
        }

        alert('Invalid format. Use:\n- Hours: 8 or 8h\n- Special work: SN:7.5, CO:6, CM:4, W:8, CE:6\n- Time off: CO, CM, SN, W, CR, CN, D, CE\n- Short day: ZS-5 (missing 5 hours)');
        return false;
    }

    /**
     * Validate ZS format (short day)
     * Format: ZS-5 means user is missing 5 hours (worked less than schedule)
     * @param {string} value - ZS format value
     * @returns {boolean} True if valid
     */
    validateZSFormat(value) {
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
     * Validate special day work time format for all types
     * Supports: SN:5, CO:6, CM:4, W:8, CE:6
     * @param {string} value - Special day format value
     * @returns {boolean} True if valid
     */
    validateSpecialDayWorktime(value) {
        const parts = value.split(':');

        // Check format: must be exactly "TYPE:number"
        if (parts.length !== 2) {
            alert('Invalid format. Use TYPE:hours (e.g., SN:7.5, CO:6, CM:4, W:8, CE:6)');
            return false;
        }

        const type = parts[0].toUpperCase();
        const hoursStr = parts[1];

        // Validate type - only types that support work hours
        const validTypes = ['SN', 'CO', 'CM', 'W', 'CE'];
        if (!validTypes.includes(type)) {
            alert(`Invalid type. Use ${validTypes.join(', ')} (e.g., SN:7.5, CE:6)`);
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

            const typeLabels = {
                'SN': 'National Holiday',
                'CO': 'Time Off',
                'CM': 'Medical Leave',
                'W': 'Weekend',
                'CE': 'Event Leave'
            };

            const confirmed = confirm(
                `${typeLabels[type]} Work Time Processing:\n\n` +
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
}
