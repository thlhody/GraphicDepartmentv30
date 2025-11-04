/**
 * Core Constants - Single Source of Truth
 *
 * This module consolidates ALL constants used throughout the application.
 * Previously duplicated across 6+ files, now maintained in one place.
 *
 * @module core/constants
 * @version 1.0.0
 * @since 2025-11-04
 *
 * ⚠️ IMPORTANT: This is the ONLY place to define application constants.
 * Do NOT duplicate these in other files.
 *
 * Usage:
 *   // ES6 Module
 *   import { ACTION_TYPE_VALUES, TIME_OFF_TYPES } from './core/constants.js';
 *   const complexity = ACTION_TYPE_VALUES.get('ORDIN');
 *
 *   // Legacy (via window global)
 *   const complexity = window.Constants.ACTION_TYPE_VALUES.get('ORDIN');
 */

// =============================================================================
// ACTION TYPE CONSTANTS
// =============================================================================

/**
 * Action type identifiers used throughout the system
 * @constant {Object}
 */
export const ActionTypeConstants = Object.freeze({
    IMPOSTARE: 'IMPOSTARE',
    AT_SPIZED: 'SPIZED',
    REGULAR_NAME: 'regular',
    SPIZED_NAME: 'spized'
});

/**
 * Action type complexity values for calculation
 * Maps action type name to its complexity multiplier
 *
 * @constant {Map<string, number>}
 * @example
 *   ACTION_TYPE_VALUES.get('ORDIN') // returns 2.5
 *   ACTION_TYPE_VALUES.has('DESIGN') // returns true
 */
export const ACTION_TYPE_VALUES = new Map([
    ['ORDIN', 2.5],
    ['REORDIN', 1.0],
    ['CAMPION', 2.5],
    ['PROBA STAMPA', 2.5],
    ['ORDIN SPIZED', 2.0],
    ['CAMPION SPIZED', 2.0],
    ['PROBA S SPIZED', 2.0],
    ['PROBA CULOARE', 2.5],
    ['CARTELA CULORI', 2.5],
    ['CHECKING', 3.0],
    ['DESIGN', 2.5],
    ['DESIGN 3D', 3.0],
    ['PATTERN PREP', 2.5],
    ['IMPOSTARE', 0.0],
    ['OTHER', 2.5]
]);

// =============================================================================
// CHECK TYPE CONSTANTS
// =============================================================================

/**
 * Check type values for calculation
 * Maps check type name to its base value
 *
 * @constant {Map<string, number>}
 * @example
 *   CHECK_TYPE_VALUES.get('LAYOUT') // returns 1.0
 */
export const CHECK_TYPE_VALUES = new Map([
    ['LAYOUT', 1.0],
    ['KIPSTA LAYOUT', 0.25],
    ['LAYOUT CHANGES', 0.25],
    ['GPT', 0.1],          // For articles; also 0.1 for pieces
    ['PRODUCTION', 0.1],
    ['REORDER', 0.1],
    ['SAMPLE', 0.3],
    ['OMS PRODUCTION', 0.1],
    ['KIPSTA PRODUCTION', 0.1]
]);

/**
 * Check types that use article numbers for calculation
 * @constant {string[]}
 */
export const ARTICLE_BASED_TYPES = Object.freeze([
    'LAYOUT',
    'KIPSTA LAYOUT',
    'LAYOUT CHANGES',
    'GPT'
]);

/**
 * Check types that use file numbers for calculation
 * Note: GPT uses both articles and files
 * @constant {string[]}
 */
export const FILE_BASED_TYPES = Object.freeze([
    'PRODUCTION',
    'REORDER',
    'SAMPLE',
    'OMS PRODUCTION',
    'KIPSTA PRODUCTION',
    'GPT'
]);

// =============================================================================
// PRINT PREP COMPLEXITY CONSTANTS
// =============================================================================

/**
 * Print prep types that add complexity to calculations
 * Maps prep type name to complexity addition value
 *
 * @constant {Map<string, number>}
 */
export const COMPLEXITY_PRINT_PREPS = new Map([
    ['SBS', 0.5],
    ['NN', 0.5],
    ['NAME', 0.5],
    ['NUMBER', 0.5],
    ['FLEX', 0.5],
    ['BRODERIE', 0.5],
    ['OTHER', 0.5]
]);

/**
 * Print prep types that don't affect complexity
 * @constant {Map<string, number>}
 */
export const NEUTRAL_PRINT_PREPS = new Map([
    ['DIGITAL', 0.0],
    ['GPT', 0.0],
    ['LAYOUT', 0.0],
    ['FILM', 0.0]
]);

// =============================================================================
// TIME-OFF TYPE CONSTANTS
// =============================================================================

/**
 * Time-off type metadata
 * Includes label, icon, description, and whether work hours are allowed
 *
 * @typedef {Object} TimeOffTypeMetadata
 * @property {string} label - Display label
 * @property {string} icon - Bootstrap icon class
 * @property {boolean} allowsWork - Whether work hours can be recorded
 * @property {string} description - Detailed description
 *
 * @constant {Map<string, TimeOffTypeMetadata>}
 *
 * @example
 *   const snType = TIME_OFF_TYPES.get('SN');
 *   console.log(snType.label); // 'National Holiday'
 *   console.log(snType.allowsWork); // true
 */
export const TIME_OFF_TYPES = new Map([
    ['SN', {
        label: 'National Holiday',
        icon: 'bi bi-calendar-event text-success',
        allowsWork: true,
        description: 'Company holiday. If worked, all time counts as overtime.'
    }],
    ['CO', {
        label: 'Vacation',
        icon: 'bi bi-airplane text-info',
        allowsWork: true,
        description: 'Paid time off using vacation balance. Deducts from annual vacation days.'
    }],
    ['CM', {
        label: 'Medical Leave',
        icon: 'bi bi-heart-pulse text-warning',
        allowsWork: true,
        description: 'Sick day. Does not deduct from vacation balance.'
    }],
    ['W', {
        label: 'Weekend Work',
        icon: 'bi bi-calendar-week text-secondary',
        allowsWork: true,
        description: 'Work on weekend day. All time counts as overtime.'
    }],
    ['CR', {
        label: 'Recovery Leave',
        icon: 'bi bi-battery-charging text-success',
        allowsWork: false,
        description: 'Paid day off using overtime balance. Deducts full schedule hours (8h) from overtime → regular time.'
    }],
    ['CN', {
        label: 'Unpaid Leave',
        icon: 'bi bi-dash-circle text-secondary',
        allowsWork: false,
        description: 'Day off without payment. Does not count as work day or deduct from balances.'
    }],
    ['D', {
        label: 'Delegation',
        icon: 'bi bi-briefcase text-primary',
        allowsWork: false,
        description: 'Delegation / Business Trip - Normal work day with special documentation. Counts as regular work day.'
    }],
    ['CE', {
        label: 'Event Leave',
        icon: 'bi bi-gift text-danger',
        allowsWork: true,
        description: 'Special event (marriage, birth, death). Free days per company policy. Field 2 required in form.'
    }]
]);

/**
 * Special day types that support work hours (TYPE:X format)
 * @constant {string[]}
 */
export const SPECIAL_DAY_TYPES = Object.freeze(['SN', 'CO', 'CM', 'W', 'CE']);

/**
 * Plain time-off types (no work hours allowed)
 * @constant {string[]}
 */
export const PLAIN_TIME_OFF_TYPES = Object.freeze(['D', 'CN', 'CR']);

/**
 * All valid time-off type codes
 * @constant {string[]}
 */
export const ALL_TIME_OFF_TYPES = Object.freeze([
    'CO', 'CM', 'SN', 'W', 'CR', 'CN', 'D', 'CE'
]);

// =============================================================================
// STATUS TYPE CONSTANTS
// =============================================================================

/**
 * Status type metadata for admin sync status
 *
 * @typedef {Object} StatusTypeMetadata
 * @property {string} label - Display label
 * @property {string} class - CSS text class
 * @property {string} badge - Bootstrap badge class
 *
 * @constant {Map<string, StatusTypeMetadata>}
 *
 * @example
 *   const status = STATUS_TYPES.get('USER_DONE');
 *   console.log(status.label); // 'User Completed'
 *   console.log(status.badge); // 'bg-success'
 */
export const STATUS_TYPES = new Map([
    ['USER_DONE', {
        label: 'User Completed',
        class: 'text-success',
        badge: 'bg-success'
    }],
    ['ADMIN_EDITED', {
        label: 'Admin Modified',
        class: 'text-warning',
        badge: 'bg-warning'
    }],
    ['USER_IN_PROCESS', {
        label: 'In Progress',
        class: 'text-info',
        badge: 'bg-info'
    }],
    ['ADMIN_BLANK', {
        label: 'Admin Blank',
        class: 'text-secondary',
        badge: 'bg-secondary'
    }],
    ['TEAM_EDITED', {
        label: 'Team Edited',
        class: 'text-primary',
        badge: 'bg-primary'
    }],
    ['ADMIN_FINAL', {
        label: 'Admin Final',
        class: 'text-dark',
        badge: 'bg-dark'
    }],
    ['TEAM_FINAL', {
        label: 'Team Final',
        class: 'text-success',
        badge: 'bg-success'
    }],
    ['USER_INPUT', {
        label: 'User Input',
        class: 'text-info',
        badge: 'bg-info'
    }]
]);

// =============================================================================
// DATE/TIME CONSTANTS
// =============================================================================

/**
 * Days of the week (Sunday = 0)
 * @constant {string[]}
 */
export const DAYS_OF_WEEK = Object.freeze([
    'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'
]);

/**
 * Month names
 * @constant {string[]}
 */
export const MONTHS = Object.freeze([
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
]);

// =============================================================================
// APPROVAL STATUS CONSTANTS
// =============================================================================

/**
 * Approval status values for check register
 * @constant {Object}
 */
export const APPROVAL_STATUS = Object.freeze({
    APPROVED: 'APPROVED',
    PARTIALLY_APPROVED: 'PARTIALLY APPROVED',
    CORRECTION: 'CORRECTION'
});

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

/**
 * Add a new action type dynamically
 * @param {string} key - Action type name
 * @param {number} value - Complexity value
 * @returns {boolean} - Success status
 */
export function addActionType(key, value) {
    if (typeof value !== 'number' || value < 0) {
        console.error(`Invalid value for action type ${key}: ${value}`);
        return false;
    }
    ACTION_TYPE_VALUES.set(key, value);
    console.log(`Added action type: ${key} = ${value}`);
    return true;
}

/**
 * Remove an action type dynamically
 * @param {string} key - Action type name
 * @returns {boolean} - Success status
 */
export function removeActionType(key) {
    if (!ACTION_TYPE_VALUES.has(key)) {
        console.error(`Action type ${key} does not exist`);
        return false;
    }
    ACTION_TYPE_VALUES.delete(key);
    console.log(`Removed action type: ${key}`);
    return true;
}

/**
 * Add a new check type dynamically
 * @param {string} key - Check type name
 * @param {number} value - Base value
 * @returns {boolean} - Success status
 */
export function addCheckType(key, value) {
    if (typeof value !== 'number' || value < 0) {
        console.error(`Invalid value for check type ${key}: ${value}`);
        return false;
    }
    CHECK_TYPE_VALUES.set(key, value);
    console.log(`Added check type: ${key} = ${value}`);
    return true;
}

/**
 * Remove a check type dynamically
 * @param {string} key - Check type name
 * @returns {boolean} - Success status
 */
export function removeCheckType(key) {
    if (!CHECK_TYPE_VALUES.has(key)) {
        console.error(`Check type ${key} does not exist`);
        return false;
    }
    CHECK_TYPE_VALUES.delete(key);
    console.log(`Removed check type: ${key}`);
    return true;
}

/**
 * Get all action type keys
 * @returns {string[]}
 */
export function getActionTypes() {
    return Array.from(ACTION_TYPE_VALUES.keys());
}

/**
 * Get all check type keys
 * @returns {string[]}
 */
export function getCheckTypes() {
    return Array.from(CHECK_TYPE_VALUES.keys());
}

/**
 * Get all time-off type keys
 * @returns {string[]}
 */
export function getTimeOffTypes() {
    return Array.from(TIME_OFF_TYPES.keys());
}

/**
 * Check if a type is article-based
 * @param {string} type - Check type
 * @returns {boolean}
 */
export function isArticleBasedType(type) {
    return ARTICLE_BASED_TYPES.includes(type);
}

/**
 * Check if a type is file-based
 * @param {string} type - Check type
 * @returns {boolean}
 */
export function isFileBasedType(type) {
    return FILE_BASED_TYPES.includes(type);
}

/**
 * Check if time-off type allows work hours
 * @param {string} type - Time-off type code
 * @returns {boolean}
 */
export function allowsWorkHours(type) {
    const baseType = type.split(':')[0].toUpperCase();
    return TIME_OFF_TYPES.get(baseType)?.allowsWork || false;
}

// =============================================================================
// EXPORTS
// =============================================================================

export default {
    ActionTypeConstants,
    ACTION_TYPE_VALUES,
    CHECK_TYPE_VALUES,
    COMPLEXITY_PRINT_PREPS,
    NEUTRAL_PRINT_PREPS,
    TIME_OFF_TYPES,
    STATUS_TYPES,
    ARTICLE_BASED_TYPES,
    FILE_BASED_TYPES,
    SPECIAL_DAY_TYPES,
    PLAIN_TIME_OFF_TYPES,
    ALL_TIME_OFF_TYPES,
    DAYS_OF_WEEK,
    MONTHS,
    APPROVAL_STATUS,

    // Helper functions
    addActionType,
    removeActionType,
    addCheckType,
    removeCheckType,
    getActionTypes,
    getCheckTypes,
    getTimeOffTypes,
    isArticleBasedType,
    isFileBasedType,
    allowsWorkHours
};