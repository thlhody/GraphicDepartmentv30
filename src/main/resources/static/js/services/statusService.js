/**
 * Status Service - Consolidated status management
 *
 * This service consolidates all status-related logic that was previously
 * duplicated across 3+ files (worktime-admin.js, constants.js, check-register.js, etc.)
 *
 * @module services/statusService
 * @version 1.0.0
 * @since 2025-11-04
 *
 * ⚠️ IMPORTANT: This is the ONLY place for status logic.
 * Do NOT duplicate these functions in other files.
 *
 * Status Types:
 * - USER_INPUT: Initial user entry
 * - USER_DONE: User completed entry
 * - USER_IN_PROCESS: Active session (worktime only)
 * - ADMIN_EDITED: Admin modified entry
 * - TEAM_EDITED: Team lead modified entry
 * - ADMIN_FINAL: Admin locked (highest priority)
 * - TEAM_FINAL: Team locked (admin can override)
 * - ADMIN_BLANK: Admin placeholder
 *
 * Usage:
 *   import { StatusService } from './services/statusService.js';
 *
 *   const label = StatusService.getLabel('USER_DONE');
 *   const cssClass = StatusService.getClass('ADMIN_EDITED');
 *   const canEdit = StatusService.isEditable('USER_DONE', 'ROLE_ADMIN');
 */

import { STATUS_TYPES } from '../core/constants.js';

/**
 * Status Service Class
 * All methods are static - no instantiation needed
 */
export class StatusService {

    // =========================================================================
    // DISPLAY HELPERS
    // =========================================================================

    /**
     * Get display label for status
     *
     * @param {string} status - Status code
     * @returns {string} Display label
     *
     * @example
     *   StatusService.getLabel('USER_DONE') // 'User Completed'
     *   StatusService.getLabel('ADMIN_EDITED') // 'Admin Modified'
     */
    static getLabel(status) {
        if (!status) return '';

        const metadata = STATUS_TYPES.get(status);
        return metadata?.label || status;
    }

    /**
     * Get CSS text class for status
     * Used for text color styling
     *
     * @param {string} status - Status code
     * @returns {string} CSS class (e.g., 'text-success')
     *
     * @example
     *   StatusService.getClass('USER_DONE') // 'text-success'
     *   StatusService.getClass('ADMIN_EDITED') // 'text-warning'
     */
    static getClass(status) {
        if (!status) return 'text-muted';

        const metadata = STATUS_TYPES.get(status);
        return metadata?.class || 'text-muted';
    }

    /**
     * Get Bootstrap badge class for status
     * Used for badge background styling
     *
     * @param {string} status - Status code
     * @returns {string} Badge class (e.g., 'bg-success')
     *
     * @example
     *   StatusService.getBadgeClass('USER_DONE') // 'bg-success'
     *   StatusService.getBadgeClass('ADMIN_FINAL') // 'bg-dark'
     */
    static getBadgeClass(status) {
        if (!status) return 'bg-secondary';

        const metadata = STATUS_TYPES.get(status);
        return metadata?.badge || 'bg-secondary';
    }

    /**
     * Generate complete badge HTML for status
     *
     * @param {string} status - Status code
     * @param {string} [additionalClasses=''] - Additional CSS classes
     * @returns {string} Badge HTML
     *
     * @example
     *   StatusService.getBadgeHtml('USER_DONE')
     *   // '<span class="badge bg-success">User Completed</span>'
     *
     *   StatusService.getBadgeHtml('ADMIN_EDITED', 'ms-2')
     *   // '<span class="badge bg-warning ms-2">Admin Modified</span>'
     */
    static getBadgeHtml(status, additionalClasses = '') {
        const label = this.getLabel(status);
        const badgeClass = this.getBadgeClass(status);
        const classes = `badge ${badgeClass} ${additionalClasses}`.trim();

        return `<span class="${classes}">${label}</span>`;
    }

    // =========================================================================
    // STATUS CHECKS
    // =========================================================================

    /**
     * Check if status is a final state (cannot be modified)
     * Final states: ADMIN_FINAL, TEAM_FINAL
     *
     * @param {string} status - Status code
     * @returns {boolean} True if final
     *
     * @example
     *   StatusService.isFinal('ADMIN_FINAL') // true
     *   StatusService.isFinal('USER_DONE') // false
     */
    static isFinal(status) {
        return status === 'ADMIN_FINAL' || status === 'TEAM_FINAL';
    }

    /**
     * Check if status represents an active session
     *
     * @param {string} status - Status code
     * @returns {boolean} True if in process
     *
     * @example
     *   StatusService.isInProcess('USER_IN_PROCESS') // true
     *   StatusService.isInProcess('USER_DONE') // false
     */
    static isInProcess(status) {
        return status === 'USER_IN_PROCESS';
    }

    /**
     * Check if status was created by user
     *
     * @param {string} status - Status code
     * @returns {boolean} True if user-created
     */
    static isUserStatus(status) {
        return status?.startsWith('USER_');
    }

    /**
     * Check if status was modified by admin
     *
     * @param {string} status - Status code
     * @returns {boolean} True if admin-modified
     */
    static isAdminStatus(status) {
        return status?.startsWith('ADMIN_');
    }

    /**
     * Check if status was modified by team
     *
     * @param {string} status - Status code
     * @returns {boolean} True if team-modified
     */
    static isTeamStatus(status) {
        return status?.startsWith('TEAM_');
    }

    // =========================================================================
    // PERMISSION CHECKS
    // =========================================================================

    /**
     * Check if a user can edit an entry with given status
     * Based on role and current status
     *
     * @param {string} status - Current entry status
     * @param {string} userRole - User's role (e.g., 'ROLE_ADMIN', 'ROLE_USER')
     * @returns {boolean} True if editable
     *
     * @example
     *   // Admin can edit most entries
     *   StatusService.isEditable('USER_DONE', 'ROLE_ADMIN') // true
     *   StatusService.isEditable('TEAM_EDITED', 'ROLE_ADMIN') // true
     *   StatusService.isEditable('ADMIN_FINAL', 'ROLE_ADMIN') // false
     *
     *   // User can only edit their own non-final entries
     *   StatusService.isEditable('USER_DONE', 'ROLE_USER') // true
     *   StatusService.isEditable('ADMIN_EDITED', 'ROLE_USER') // false
     *   StatusService.isEditable('USER_IN_PROCESS', 'ROLE_USER') // false (active session)
     *
     *   // Team lead can edit user and own entries
     *   StatusService.isEditable('USER_DONE', 'ROLE_TEAM_LEADER') // true
     *   StatusService.isEditable('TEAM_EDITED', 'ROLE_TEAM_LEADER') // true
     *   StatusService.isEditable('ADMIN_EDITED', 'ROLE_TEAM_LEADER') // false
     */
    static isEditable(status, userRole) {
        if (!status || !userRole) return false;

        // Active sessions cannot be edited (even by admins)
        if (this.isInProcess(status)) {
            return false;
        }

        // ADMIN_FINAL cannot be edited by anyone
        if (status === 'ADMIN_FINAL') {
            return false;
        }

        // Admin can edit everything except ADMIN_FINAL and active sessions
        if (userRole === 'ROLE_ADMIN') {
            return true;
        }

        // Team lead can edit user and team entries, but not admin entries
        if (userRole === 'ROLE_TEAM_LEADER' || userRole === 'ROLE_TL_CHECKING') {
            if (this.isAdminStatus(status)) {
                return false; // Cannot edit admin entries
            }
            if (status === 'TEAM_FINAL') {
                return true; // Can edit own final
            }
            return this.isUserStatus(status) || this.isTeamStatus(status);
        }

        // Regular users can only edit their own non-final entries
        if (userRole === 'ROLE_USER' || userRole === 'ROLE_CHECKING' || userRole === 'ROLE_USER_CHECKING') {
            // Cannot edit if admin or team modified
            if (this.isAdminStatus(status) || this.isTeamStatus(status)) {
                return false;
            }
            // Can edit user entries that are not final
            return this.isUserStatus(status) && !this.isFinal(status);
        }

        return false;
    }

    /**
     * Check if a status can be overridden by another status
     * Used in merge operations
     *
     * @param {string} currentStatus - Current status
     * @param {string} newStatus - New status attempting to override
     * @returns {boolean} True if can override
     *
     * @example
     *   // Admin can override team
     *   StatusService.canOverride('TEAM_EDITED', 'ADMIN_EDITED') // true
     *
     *   // Team cannot override admin
     *   StatusService.canOverride('ADMIN_EDITED', 'TEAM_EDITED') // false
     *
     *   // Final states are strongest
     *   StatusService.canOverride('ADMIN_FINAL', 'TEAM_EDITED') // false
     */
    static canOverride(currentStatus, newStatus) {
        if (!currentStatus || !newStatus) return false;

        // Active sessions cannot be overridden
        if (this.isInProcess(currentStatus)) {
            return false;
        }

        // ADMIN_FINAL cannot be overridden by anything
        if (currentStatus === 'ADMIN_FINAL') {
            return false;
        }

        // ADMIN_FINAL can override anything
        if (newStatus === 'ADMIN_FINAL') {
            return true;
        }

        // TEAM_FINAL can be overridden by admin statuses
        if (currentStatus === 'TEAM_FINAL') {
            return this.isAdminStatus(newStatus);
        }

        // Admin statuses override team and user statuses
        if (this.isAdminStatus(newStatus)) {
            return this.isTeamStatus(currentStatus) || this.isUserStatus(currentStatus);
        }

        // Team statuses override user statuses
        if (this.isTeamStatus(newStatus)) {
            return this.isUserStatus(currentStatus);
        }

        // User statuses can override other user statuses
        if (this.isUserStatus(newStatus) && this.isUserStatus(currentStatus)) {
            return true;
        }

        return false;
    }

    /**
     * Get status priority (higher number = higher priority)
     * Used for determining which status wins in conflicts
     *
     * @param {string} status - Status code
     * @returns {number} Priority level (0-5)
     *
     * @example
     *   StatusService.getPriority('ADMIN_FINAL') // 5 (highest)
     *   StatusService.getPriority('USER_INPUT') // 1 (lowest)
     */
    static getPriority(status) {
        if (!status) return 0;

        // Define priority levels
        const priorities = {
            'ADMIN_FINAL': 5,      // Highest - locked by admin
            'TEAM_FINAL': 4,       // High - locked by team
            'ADMIN_EDITED': 3,     // Medium-high - admin modified
            'TEAM_EDITED': 2,      // Medium - team modified
            'USER_IN_PROCESS': 2,  // Medium - active session (cannot override)
            'USER_DONE': 1,        // Low - user completed
            'USER_INPUT': 1,       // Low - user created
            'ADMIN_BLANK': 1       // Low - admin placeholder
        };

        return priorities[status] || 0;
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Get all valid status codes
     *
     * @returns {string[]} Array of status codes
     */
    static getAllStatuses() {
        return Array.from(STATUS_TYPES.keys());
    }

    /**
     * Check if a status code is valid
     *
     * @param {string} status - Status code to check
     * @returns {boolean} True if valid
     */
    static isValidStatus(status) {
        if (!status) return false;
        return STATUS_TYPES.has(status);
    }

    /**
     * Get status recommendation for a role action
     * Helps determine what status to set when a user edits
     *
     * @param {string} userRole - User's role
     * @param {boolean} isFinal - Whether to mark as final
     * @returns {string} Recommended status
     *
     * @example
     *   StatusService.getStatusForAction('ROLE_ADMIN', false) // 'ADMIN_EDITED'
     *   StatusService.getStatusForAction('ROLE_ADMIN', true) // 'ADMIN_FINAL'
     *   StatusService.getStatusForAction('ROLE_TEAM_LEADER', false) // 'TEAM_EDITED'
     *   StatusService.getStatusForAction('ROLE_USER', false) // 'USER_DONE'
     */
    static getStatusForAction(userRole, isFinal = false) {
        if (!userRole) return 'USER_INPUT';

        if (userRole === 'ROLE_ADMIN') {
            return isFinal ? 'ADMIN_FINAL' : 'ADMIN_EDITED';
        }

        if (userRole === 'ROLE_TEAM_LEADER' || userRole === 'ROLE_TL_CHECKING') {
            return isFinal ? 'TEAM_FINAL' : 'TEAM_EDITED';
        }

        // Regular users
        return 'USER_DONE';
    }

    /**
     * Format status with icon and label
     *
     * @param {string} status - Status code
     * @returns {string} Formatted string with icon
     *
     * @example
     *   StatusService.formatWithIcon('USER_DONE')
     *   // '✓ User Completed'
     *
     *   StatusService.formatWithIcon('ADMIN_FINAL')
     *   // '🔒 Admin Final'
     */
    static formatWithIcon(status) {
        const label = this.getLabel(status);

        const icons = {
            'USER_DONE': '✓',
            'USER_IN_PROCESS': '⏳',
            'ADMIN_EDITED': '✎',
            'TEAM_EDITED': '✎',
            'ADMIN_FINAL': '🔒',
            'TEAM_FINAL': '🔒',
            'ADMIN_BLANK': '○',
            'USER_INPUT': '+'
        };

        const icon = icons[status] || '•';
        return `${icon} ${label}`;
    }
}

// Export as default for convenience
export default StatusService;
