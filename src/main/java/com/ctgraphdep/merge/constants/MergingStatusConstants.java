package com.ctgraphdep.merge.constants;

import com.ctgraphdep.config.SecurityConstants;

import java.time.Instant;

/**
 * Enhanced MergingStatusConstants with timestamped status creation support.
 * Provides both base statuses and methods to create timestamped edit statuses.
 * this is mostly for display purposes
 * Status Hierarchy:
 * 1. BASE: USER_INPUT, ADMIN_INPUT, USER_IN_PROCESS
 * 2. TIMESTAMPED EDITS: USER_EDITED_[epoch], ADMIN_EDITED_[epoch], TEAM_EDITED_[epoch]
 * 3. FINAL: ADMIN_FINAL, TEAM_FINAL
 * 4. SPECIAL: DELETE
 */
public class MergingStatusConstants {

    // ========================================================================
    // BASE STATUSES (No timestamps)
    // ========================================================================

    /** Admin created entry directly */
    public static final String ADMIN_INPUT = "ADMIN_INPUT";

    /** Team lead created new entry or completed initial work */
    public static final String TEAM_INPUT = "TEAM_INPUT";

    /** User created new entry or completed initial work */
    public static final String USER_INPUT = "USER_INPUT";

    /** User is actively working on entry (protected from external changes) */
    public static final String USER_IN_PROCESS = "USER_IN_PROCESS";

    // ========================================================================
    // FINAL STATUSES (Immutable)
    // ========================================================================

    /** Admin finalized - absolutely immutable */
    public static final String ADMIN_FINAL = "ADMIN_FINAL";

    /** Team leader finalized for their scope */
    public static final String TEAM_FINAL = "TEAM_FINAL";

    // ========================================================================
    // TIMESTAMPED EDIT STATUS PREFIXES
    // ========================================================================

    /** Prefix for user edit statuses */
    public static final String USER_EDITED_PREFIX = "USER_EDITED_";

    /** Prefix for admin edit statuses */
    public static final String ADMIN_EDITED_PREFIX = "ADMIN_EDITED_";

    /** Prefix for team leader edit statuses */
    public static final String TEAM_EDITED_PREFIX = "TEAM_EDITED_";

    // ========================================================================
    // DELETION STATUS PREFIXES (Tombstone-based deletion)
    // ========================================================================

    /** Prefix for user deletion statuses */
    public static final String USER_DELETED_PREFIX = "USER_DELETED_";

    /** Prefix for admin deletion statuses */
    public static final String ADMIN_DELETED_PREFIX = "ADMIN_DELETED_";

    /** Prefix for team leader deletion statuses */
    public static final String TEAM_DELETED_PREFIX = "TEAM_DELETED_";

    // ========================================================================
    // TIMESTAMPED STATUS CREATION METHODS
    // ========================================================================

    /**
     * Create timestamped user edit status for current time
     * Format: "USER_EDITED_[minutesSinceEpoch]"
     */
    public static String createUserEditedStatus() {
        long minutesSinceEpoch = Instant.now().getEpochSecond() / 60;
        return USER_EDITED_PREFIX + minutesSinceEpoch;
    }

    /**
     * Create timestamped admin edit status for current time
     * Format: "ADMIN_EDITED_[minutesSinceEpoch]"
     */
    public static String createAdminEditedStatus() {
        long minutesSinceEpoch = Instant.now().getEpochSecond() / 60;
        return ADMIN_EDITED_PREFIX + minutesSinceEpoch;
    }

    /**
     * Create timestamped team edit status for current time
     * Format: "TEAM_EDITED_[minutesSinceEpoch]"
     */
    public static String createTeamEditedStatus() {
        long minutesSinceEpoch = Instant.now().getEpochSecond() / 60;
        return TEAM_EDITED_PREFIX + minutesSinceEpoch;
    }


    // ========================================================================
    // DELETION STATUS CREATION METHODS
    // ========================================================================

    /**
     * Create timestamped user deletion status for current time
     * Format: "USER_DELETED_[minutesSinceEpoch]"
     */
    public static String createUserDeletedStatus() {
        long minutesSinceEpoch = Instant.now().getEpochSecond() / 60;
        return USER_DELETED_PREFIX + minutesSinceEpoch;
    }

    /**
     * Create timestamped team deletion status for current time
     * Format: "TEAM_DELETED_[minutesSinceEpoch]"
     */
    public static String createTeamDeletedStatus() {
        long minutesSinceEpoch = Instant.now().getEpochSecond() / 60;
        return TEAM_DELETED_PREFIX + minutesSinceEpoch;
    }

    // ========================================================================
    // STATUS CHECKING UTILITY METHODS
    // ========================================================================

    /**
     * Check if status is a timestamped edit status
     */
    public static boolean isTimestampedEditStatus(String status) {
        if (status == null) return false;
        return status.startsWith(USER_EDITED_PREFIX) ||
                status.startsWith(ADMIN_EDITED_PREFIX) ||
                status.startsWith(TEAM_EDITED_PREFIX);
    }

    /**
     * Check if status is a user edit (timestamped)
     */
    public static boolean isUserEditedStatus(String status) {
        return status != null && status.startsWith(USER_EDITED_PREFIX);
    }

    /**
     * Check if status is an admin edit (timestamped)
     */
    public static boolean isAdminEditedStatus(String status) {
        return status != null && status.startsWith(ADMIN_EDITED_PREFIX);
    }

    /**
     * Check if status is a team edit (timestamped)
     */
    public static boolean isTeamEditedStatus(String status) {
        return status != null && status.startsWith(TEAM_EDITED_PREFIX);
    }

    /**
     * Check if status is a final status (immutable)
     */
    public static boolean isFinalStatus(String status) {
        return ADMIN_FINAL.equals(status) || TEAM_FINAL.equals(status);
    }

    /**
     * Check if status is a base input status
     */
    public static boolean isBaseInputStatus(String status) {
        return USER_INPUT.equals(status) || ADMIN_INPUT.equals(status);
    }

    /**
     * Check if status is valid in the merge system.
     * Used by GenericEntityWrapper for status normalization.
     */
    public static boolean isValidStatus(String status) {
        if (status == null) return false;

        return USER_INPUT.equals(status) ||
                USER_IN_PROCESS.equals(status) ||
                ADMIN_INPUT.equals(status) ||
                TEAM_INPUT.equals(status) ||
                ADMIN_FINAL.equals(status) ||
                TEAM_FINAL.equals(status) ||
                isTimestampedEditStatus(status) ||
                isDeletedStatus(status);  // ← FIX: Include deletion statuses
    }

    /**
     * Extract timestamp from timestamped status
     * Returns 0 if not a timestamped status or parsing fails
     */
    public static long extractTimestamp(String status) {
        if (!isTimestampedEditStatus(status)) {
            return 0L;
        }

        try {
            // Find the last underscore and extract everything after it
            int lastUnderscore = status.lastIndexOf('_');
            if (lastUnderscore == -1 || lastUnderscore == status.length() - 1) {
                return 0L;
            }

            String timestampStr = status.substring(lastUnderscore + 1);
            return Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Get editor type from timestamped status
     * Returns "USER", "ADMIN", "TEAM", or "user"
     */
    public static String getEditorType(String status) {
        if (isUserEditedStatus(status)) return SecurityConstants.ROLE_USER;
        if (isAdminEditedStatus(status)) return SecurityConstants.ROLE_ADMIN;
        if (isTeamEditedStatus(status)) return SecurityConstants.ROLE_TEAM_LEADER ;
        return SecurityConstants.ROLE_USER;
    }

    // ========================================================================
    // DELETION STATUS CHECKING METHODS
    // ========================================================================

    /**
     * Check if status is NOT a deletion status (active entry)
     * Primary method - used in filter operations to identify active entries
     */
    public static boolean isActiveStatus(String status) {
        if (status == null) return true; // null status means active entry
        return !status.startsWith(USER_DELETED_PREFIX) &&
                !status.startsWith(ADMIN_DELETED_PREFIX) &&
                !status.startsWith(TEAM_DELETED_PREFIX);
    }

    /**
     * Check if status is a deletion status (tombstone)
     * Convenience method - returns opposite of isActiveStatus()
     */
    public static boolean isDeletedStatus(String status) {
        return !isActiveStatus(status);
    }

    /**
     * Check if status is a user deletion
     */
    @SuppressWarnings("unused")
    public static boolean isUserDeletedStatus(String status) {
        return status != null && status.startsWith(USER_DELETED_PREFIX);
    }

    /**
     * Check if status is an admin deletion
     */
    @SuppressWarnings("unused")
    public static boolean isAdminDeletedStatus(String status) {
        return status != null && status.startsWith(ADMIN_DELETED_PREFIX);
    }

    /**
     * Check if status is a team deletion
     */
    @SuppressWarnings("unused")
    public static boolean isTeamDeletedStatus(String status) {
        return status != null && status.startsWith(TEAM_DELETED_PREFIX);
    }

    /**
     * Extract timestamp from deletion status
     * Returns 0 if not a deletion status or parsing fails
     */
    public static long extractDeletionTimestamp(String status) {
        if (!isDeletedStatus(status)) {
            return 0L;
        }

        try {
            int lastUnderscore = status.lastIndexOf('_');
            if (lastUnderscore == -1 || lastUnderscore == status.length() - 1) {
                return 0L;
            }

            String timestampStr = status.substring(lastUnderscore + 1);
            return Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // ========================================================================
    // STATUS DISPLAY UTILITIES
    // ========================================================================

    /**
     * Convert full status to compact display format
     * USER_INPUT → UI
     * USER_EDITED_12345 → UE
     * TEAM_EDITED_67890 → TE
     * ADMIN_FINAL → AF
     * USER_DELETED_12345 → UD
     * etc.
     */
    @SuppressWarnings("unused")
    public static String toCompactDisplay(String status) {
        if (status == null) {
            return "??";
        }

        // Check prefixes first (for timestamped statuses)
        if (status.startsWith(USER_EDITED_PREFIX)) return "UE";
        if (status.startsWith(ADMIN_EDITED_PREFIX)) return "AE";
        if (status.startsWith(TEAM_EDITED_PREFIX)) return "TE";
        if (status.startsWith(USER_DELETED_PREFIX)) return "UD";
        if (status.startsWith(ADMIN_DELETED_PREFIX)) return "AD";
        if (status.startsWith(TEAM_DELETED_PREFIX)) return "TD";

        // Base and final statuses (exact matches)
        return switch (status) {
            case USER_INPUT -> "UI";
            case TEAM_INPUT -> "TI";
            case ADMIN_INPUT -> "AI";
            case USER_IN_PROCESS -> "UP";
            case ADMIN_FINAL -> "AF";
            case TEAM_FINAL -> "TF";
            default -> "??";
        };
    }

    /**
     * Get status description for tooltip/title
     */
    @SuppressWarnings("unused")
    public static String getStatusDescription(String status) {
        if (status == null) {
            return "Unknown status";
        }

        // Check prefixes first (for timestamped statuses)
        if (status.startsWith(USER_EDITED_PREFIX)) {
            long ts = extractTimestamp(status);
            return "User Edited (timestamp: " + ts + ")";
        }
        if (status.startsWith(ADMIN_EDITED_PREFIX)) {
            long ts = extractTimestamp(status);
            return "Admin Edited (timestamp: " + ts + ")";
        }
        if (status.startsWith(TEAM_EDITED_PREFIX)) {
            long ts = extractTimestamp(status);
            return "Team Edited (timestamp: " + ts + ")";
        }
        if (status.startsWith(USER_DELETED_PREFIX)) {
            long ts = extractDeletionTimestamp(status);
            return "User Deleted (timestamp: " + ts + ")";
        }
        if (status.startsWith(ADMIN_DELETED_PREFIX)) {
            long ts = extractDeletionTimestamp(status);
            return "Admin Deleted (timestamp: " + ts + ")";
        }
        if (status.startsWith(TEAM_DELETED_PREFIX)) {
            long ts = extractDeletionTimestamp(status);
            return "Team Deleted (timestamp: " + ts + ")";
        }

        // Base and final statuses (exact matches)
        return switch (status) {
            case USER_INPUT -> "User Input";
            case TEAM_INPUT -> "Team Input";
            case ADMIN_INPUT -> "Admin Input";
            case USER_IN_PROCESS -> "User In Process";
            case ADMIN_FINAL -> "Admin Final (Locked)";
            case TEAM_FINAL -> "Team Final";
            default -> "Unknown: " + status;
        };
    }
}