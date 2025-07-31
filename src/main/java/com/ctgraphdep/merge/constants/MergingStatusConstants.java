package com.ctgraphdep.merge.constants;

import java.time.Instant;

/**
 * Enhanced MergingStatusConstants with timestamped status creation support.
 * Provides both base statuses and methods to create timestamped edit statuses.
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

    /** Entry marked for deletion */
    public static final String DELETE = "DELETE";

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

    /**
     * Create timestamped user edit status for specific time
     * Format: "USER_EDITED_[minutesSinceEpoch]"
     */
    public static String createUserEditedStatus(long minutesSinceEpoch) {
        return USER_EDITED_PREFIX + minutesSinceEpoch;
    }

    /**
     * Create timestamped admin edit status for specific time
     * Format: "ADMIN_EDITED_[minutesSinceEpoch]"
     */
    public static String createAdminEditedStatus(long minutesSinceEpoch) {
        return ADMIN_EDITED_PREFIX + minutesSinceEpoch;
    }

    /**
     * Create timestamped team edit status for specific time
     * Format: "TEAM_EDITED_[minutesSinceEpoch]"
     */
    public static String createTeamEditedStatus(long minutesSinceEpoch) {
        return TEAM_EDITED_PREFIX + minutesSinceEpoch;
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
     * Returns "USER", "ADMIN", "TEAM", or "UNKNOWN"
     */
    public static String getEditorType(String status) {
        if (isUserEditedStatus(status)) return "USER";
        if (isAdminEditedStatus(status)) return "ADMIN";
        if (isTeamEditedStatus(status)) return "TEAM";
        return "UNKNOWN";
    }
}