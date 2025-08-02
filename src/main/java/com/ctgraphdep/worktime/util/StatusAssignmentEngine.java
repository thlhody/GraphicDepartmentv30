package com.ctgraphdep.worktime.util;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

/**
 * StatusAssignmentEngine - Centralized Status Management for WorktimeEntityBuilder
 * Determines and assigns the correct adminSync status based on:
 * - Current entry status (if exists)
 * - User role performing the operation
 * - Operation type being performed
 * PROTECTION RULES:
 * - USER_IN_PROCESS: Protected, no changes allowed
 * - ADMIN_FINAL: Only admin can change, all other roles cannot
 * - TEAM_FINAL: Only admin can override
 * OVERWRITE RULES:
 * - INPUT statuses (ADMIN_INPUT, TEAM_INPUT, USER_INPUT): Get overwritten with EDITED_[timestamp]
 * - EDIT statuses: Get new timestamp based on current role
 * ROLE MAPPING:
 * - USER/TEAM_LEADER/CHECKING/USER_CHECKING → USER_EDITED_[timestamp]
 * - ADMIN → ADMIN_EDITED_[timestamp]
 * - TL_CHECKING → TEAM_EDITED_[timestamp]
 */
public class StatusAssignmentEngine {

    private static final Class<?> LOGGER_CLASS = StatusAssignmentEngine.class;

    // ========================================================================
    // PUBLIC API - Main Entry Point
    // ========================================================================

    /**
     * Determines and assigns the correct status to an entry
     *
     * @param entry The WorkTimeTable entry to update
     * @param userRole The role of the user performing the operation
     * @param operationType The type of operation being performed
     * @return StatusAssignmentResult containing the decision details
     */
    public static StatusAssignmentResult assignStatus(WorkTimeTable entry, String userRole, String operationType) {
        if (entry == null) {
            LoggerUtil.warn(LOGGER_CLASS, "assignStatus() called with null entry");
            return StatusAssignmentResult.error("Entry cannot be null");
        }

        String currentStatus = entry.getAdminSync();
        LoggerUtil.debug(LOGGER_CLASS, String.format(
                "assignStatus() - User: %s, Operation: %s, CurrentStatus: %s",
                userRole, operationType, currentStatus));

        // Check protection rules first
        ProtectionResult protection = checkProtectionRules(currentStatus, userRole, operationType);
        if (protection.isProtected()) {
            LoggerUtil.info(LOGGER_CLASS, String.format(
                    "Status protected: %s - %s", currentStatus, protection.getReason()));
            return StatusAssignmentResult.protectedStatus(currentStatus, protection.getReason());
        }

        // Determine new status based on operation and role
        String newStatus = determineNewStatus(currentStatus, userRole, operationType);

        // Apply the new status
        entry.setAdminSync(newStatus);

        LoggerUtil.info(LOGGER_CLASS, String.format(
                "Status updated: %s → %s (User: %s, Operation: %s)",
                currentStatus, newStatus, userRole, operationType));

        return StatusAssignmentResult.success(currentStatus, newStatus,
                String.format("Status updated for %s operation by %s", operationType, userRole));
    }

    // ========================================================================
    // PROTECTION RULES - Check if status can be changed
    // ========================================================================

    /**
     * Checks if the current status is protected from changes
     */
    private static ProtectionResult checkProtectionRules(String currentStatus, String userRole, String operationType) {
        if (currentStatus == null) {
            return ProtectionResult.allowed();
        }

        // Rule 1: USER_IN_PROCESS - Protected from everyone
        if (MergingStatusConstants.USER_IN_PROCESS.equals(currentStatus)) {
            return ProtectionResult.protect("Entry is USER_IN_PROCESS - no external changes allowed");
        }

        // Rule 2: ADMIN_FINAL - Only admin can change
        if (MergingStatusConstants.ADMIN_FINAL.equals(currentStatus)) {
            if (isNonAdminRole(userRole)) {
                return ProtectionResult.protect("Entry is ADMIN_FINAL - only admin can change");
            }
        }

        // Rule 3: TEAM_FINAL - Only admin can override
        if (MergingStatusConstants.TEAM_FINAL.equals(currentStatus)) {
            if (isNonAdminRole(userRole)) {
                return ProtectionResult.protect("Entry is TEAM_FINAL - only admin can override");
            }
        }

        // Rule 4: DELETE status analysis
        if (MergingStatusConstants.DELETE.equals(currentStatus)) {
            return ProtectionResult.allowed(); // Anyone can modify DELETE entries
        }

        return ProtectionResult.allowed();
    }

    // ========================================================================
    // STATUS DETERMINATION - Determine new status based on rules
    // ========================================================================

    /**
     * Determines the new status based on current status, role, and operation
     */
    private static String determineNewStatus(String currentStatus, String userRole, String operationType) {

        // ✅ NEW: Handle CONSOLIDATION operations - preserve existing status
        if (isConsolidationOperation(operationType)) {
            LoggerUtil.debug(LOGGER_CLASS, String.format(
                    "Consolidation operation detected: preserving existing status '%s'", currentStatus));
            return currentStatus; // Keep whatever status the merge engine decided
        }


        // ✅ NEW: Handle DELETE operations - always return DELETE status
        if (isDeleteOperation(operationType)) {
            return MergingStatusConstants.DELETE;
        }

        // ✅ NEW: Handle RESET operations - return role-based EDITED status
        if (isResetOperation(operationType)) {
            return getEditStatusForRole(userRole);
        }

        // For new entries (null or empty status)
        if (currentStatus == null || currentStatus.trim().isEmpty()) {
            return getNewEntryStatus(userRole, operationType);
        }

        // For existing entries - check if it's an INPUT status that should be overwritten
        if (isInputStatus(currentStatus)) {
            return getEditStatusForRole(userRole);
        }

        // For existing EDIT statuses - update with new timestamp
        if (isEditStatus(currentStatus)) {
            return getEditStatusForRole(userRole);
        }

        // For other statuses, apply role-based logic
        return getEditStatusForRole(userRole);
    }

    /**
     * Get status for new entries based on role and operation type
     */
    private static String getNewEntryStatus(String userRole, String operationType) {

        // Special operation types
        if ("ADD_NATIONAL_HOLIDAY".equals(operationType)) {
            return MergingStatusConstants.ADMIN_INPUT; // Always admin for national holidays
        }

        if ("ADD_TIME_OFF".equals(operationType)) {
            // Time off follows normal role rules
            return getInputStatusForRole(userRole);
        }

        if ("UPDATE_START_TIME".equals(operationType) ||
                "UPDATE_END_TIME".equals(operationType) ||
                "UPDATE_TEMPORARY_STOP".equals(operationType)) {
            return getInputStatusForRole(userRole);  // For new entries
        }

        // Default: use role-based input status
        return getInputStatusForRole(userRole);
    }

    /**
     * Get input status based on user role (for new entries)
     */
    private static String getInputStatusForRole(String userRole) {
        return switch (normalizeRole(userRole)) {
            case SecurityConstants.ROLE_ADMIN -> MergingStatusConstants.ADMIN_INPUT;
            case SecurityConstants.ROLE_TL_CHECKING -> MergingStatusConstants.TEAM_INPUT;
            default -> MergingStatusConstants.USER_INPUT;
        };
    }

    /**
     * Get edit status based on user role (for existing entries)
     */
    private static String getEditStatusForRole(String userRole) {
        return switch (normalizeRole(userRole)) {
            case SecurityConstants.ROLE_ADMIN -> MergingStatusConstants.createAdminEditedStatus();
            case SecurityConstants.ROLE_TL_CHECKING -> MergingStatusConstants.createTeamEditedStatus();
            // USER, TEAM_LEADER, CHECKING, USER_CHECKING all get USER_EDITED
            default -> MergingStatusConstants.createUserEditedStatus();
        };
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Check if status is an INPUT status that can be overwritten
     */
    private static boolean isInputStatus(String status) {
        return MergingStatusConstants.ADMIN_INPUT.equals(status) ||
                MergingStatusConstants.TEAM_INPUT.equals(status) ||
                MergingStatusConstants.USER_INPUT.equals(status);
    }

    /**
     * Check if status is an EDIT status (timestamped)
     */
    private static boolean isEditStatus(String status) {
        return status != null && (
                status.startsWith(MergingStatusConstants.USER_EDITED_PREFIX) ||
                        status.startsWith(MergingStatusConstants.ADMIN_EDITED_PREFIX) ||
                        status.startsWith(MergingStatusConstants.TEAM_EDITED_PREFIX)
        );
    }

    /**
     * Check if user has admin role
     */
    private static boolean isNonAdminRole(String userRole) {
        return !SecurityConstants.ROLE_ADMIN.equals(normalizeRole(userRole));
    }

    /**
     * Normalize role string for consistent comparison
     */
    private static String normalizeRole(String userRole) {
        if (userRole == null) {
            return SecurityConstants.ROLE_USER; // Default to user role
        }
        return userRole.trim().toUpperCase();
    }

    /**
     * Check if operation type should result in DELETE status
     */
    private static boolean isDeleteOperation(String operationType) {
        return "DELETE_ENTRY".equals(operationType);
    }

    /**
     * Check if operation type should result in EDIT status (for special day resets)
     */
    private static boolean isResetOperation(String operationType) {
        return "RESET_SPECIAL_DAY".equals(operationType);
    }

    /**
     * Check if operation type is a consolidation that should preserve existing statuses
     */
    private static boolean isConsolidationOperation(String operationType) {
        return "CONSOLIDATE_WORKTIME".equals(operationType);
    }

    // ========================================================================
    // RESULT CLASSES
    // ========================================================================

    /**
     * Result of protection rule check
     */
    @Getter
    private static class ProtectionResult {
        private final boolean isProtected;
        private final String reason;

        private ProtectionResult(boolean isProtected, String reason) {
            this.isProtected = isProtected;
            this.reason = reason;
        }

        public static ProtectionResult protect(String reason) {
            return new ProtectionResult(true, reason);
        }

        public static ProtectionResult allowed() {
            return new ProtectionResult(false, "Changes allowed");
        }

    }
}