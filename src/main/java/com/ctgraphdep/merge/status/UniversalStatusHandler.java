// ============================================================================
// UNIVERSAL STATUS HANDLER - Updated for new timestamped format
// ============================================================================

package com.ctgraphdep.merge.status;

import com.ctgraphdep.merge.constants.MergingStatusConstants;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Enhanced UniversalStatusHandler for new timestamped edit statuses.
 * Supports: USER_EDITED_[epoch], ADMIN_EDITED_[epoch], TEAM_EDITED_[epoch]
 * Handles conversion between enum and string representations for Universal Merge Engine.
 */
public class UniversalStatusHandler {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ========================================================================
    // STATUS VALIDATION AND CONVERSION
    // ========================================================================

    /**
     * Check if string represents a timestamped edit status
     * Recognizes: USER_EDITED_[epoch], ADMIN_EDITED_[epoch], TEAM_EDITED_[epoch]
     */
    public static boolean isVersionedStatus(String statusString) {
        return MergingStatusConstants.isTimestampedEditStatus(statusString);
    }

    /**
     * Extract timestamp from any timestamped edit status
     * Works with USER_EDITED_, ADMIN_EDITED_, TEAM_EDITED_ formats
     */
    public static long extractTimestamp(String versionedStatus) {
        return MergingStatusConstants.extractTimestamp(versionedStatus);
    }

    /**
     * Create timestamped user edit status for current time
     */
    public static String createUserEditedStatus() {
        return MergingStatusConstants.createUserEditedStatus();
    }

    /**
     * Create timestamped admin edit status for current time
     */
    public static String createAdminEditedStatus() {
        return MergingStatusConstants.createAdminEditedStatus();
    }

    /**
     * Create timestamped team edit status for current time
     */
    public static String createTeamEditedStatus() {
        return MergingStatusConstants.createTeamEditedStatus();
    }

    /**
     * Create timestamped edit status for specific timestamp
     */
    public static String createUserEditedStatus(long minutesSinceEpoch) {
        return MergingStatusConstants.createUserEditedStatus(minutesSinceEpoch);
    }

    /**
     * Create timestamped admin edit status for specific timestamp
     */
    public static String createAdminEditedStatus(long minutesSinceEpoch) {
        return MergingStatusConstants.createAdminEditedStatus(minutesSinceEpoch);
    }

    /**
     * Create timestamped team edit status for specific timestamp
     */
    public static String createTeamEditedStatus(long minutesSinceEpoch) {
        return MergingStatusConstants.createTeamEditedStatus(minutesSinceEpoch);
    }

    /**
     * Convert any status string to proper format
     */
    public static String normalizeStatus(String statusString) {
        if (statusString == null) {
            return MergingStatusConstants.USER_INPUT;
        }

        // Check if it's a valid-timestamped status
        if (isVersionedStatus(statusString)) {
            return statusString; // Keep as-is
        }

        // Check if it's a valid base status
        if (isValidBaseStatus(statusString)) {
            return statusString; // Keep as-is
        }

        // Default to USER_INPUT for unknown statuses
        return MergingStatusConstants.USER_INPUT;
    }

    /**
     * Check if status is a valid base status
     */
    private static boolean isValidBaseStatus(String statusString) {
        return MergingStatusConstants.USER_INPUT.equals(statusString) ||
                MergingStatusConstants.USER_IN_PROCESS.equals(statusString) ||
                MergingStatusConstants.ADMIN_INPUT.equals(statusString) ||
                MergingStatusConstants.ADMIN_FINAL.equals(statusString) ||
                MergingStatusConstants.TEAM_FINAL.equals(statusString) ||
                MergingStatusConstants.DELETE.equals(statusString);
    }

    /**
     * Get priority level for any status string (for Universal Merge Engine)
     */
    public static int getStatusPriority(String statusString) {
        if (statusString == null) {
            return 0;
        }

        // Final statuses have the highest priority
        if (MergingStatusConstants.isFinalStatus(statusString)) {
            return 4; // ADMIN_FINAL, TEAM_FINAL
        }

        // Timestamped edits have high priority
        if (isVersionedStatus(statusString)) {
            return 3; // USER_EDITED_, ADMIN_EDITED_, TEAM_EDITED_
        }

        // User protected status
        if (MergingStatusConstants.USER_IN_PROCESS.equals(statusString)) {
            return 2;
        }

        // Base input statuses
        if (MergingStatusConstants.isBaseInputStatus(statusString)) {
            return 1; // USER_INPUT, ADMIN_INPUT
        }

        return 0; // Unknown status
    }

    /**
     * Compare two status strings by priority and timestamp
     * Enhanced with admin-wins conflict resolution
     */
    public static int compareStatuses(String status1, String status2) {
        int priority1 = getStatusPriority(status1);
        int priority2 = getStatusPriority(status2);

        if (priority1 != priority2) {
            return Integer.compare(priority1, priority2);
        }

        // Same priority - for timestamped statuses, compare timestamps
        if (isVersionedStatus(status1) && isVersionedStatus(status2)) {
            long timestamp1 = extractTimestamp(status1);
            long timestamp2 = extractTimestamp(status2);

            if (timestamp1 != timestamp2) {
                return Long.compare(timestamp1, timestamp2);
            }

            // IDENTICAL TIMESTAMPS: Admin wins conflict resolution
            return compareEditorPriority(status1, status2);
        }

        return 0; // Equal priority
    }

    /**
     * Compare editor priority for conflict resolution
     * ADMIN > TEAM > USER
     */
    private static int compareEditorPriority(String status1, String status2) {
        int priority1 = getEditorPriority(status1);
        int priority2 = getEditorPriority(status2);
        return Integer.compare(priority1, priority2);
    }

    /**
     * Get editor priority for conflict resolution
     * Higher number = higher priority
     */
    private static int getEditorPriority(String status) {
        if (MergingStatusConstants.isAdminEditedStatus(status)) return 3; // ADMIN highest
        if (MergingStatusConstants.isTeamEditedStatus(status)) return 2;  // TEAM middle
        if (MergingStatusConstants.isUserEditedStatus(status)) return 1;  // USER lowest
        return 0; // Unknown
    }

    /**
     * Format timestamp for display
     */
    public static String formatTimestamp(long minutesSinceEpoch) {
        if (minutesSinceEpoch <= 0) {
            return "Unknown";
        }

        try {
            LocalDateTime dateTime = LocalDateTime.ofEpochSecond(
                    minutesSinceEpoch * 60, 0, ZoneOffset.UTC);
            return dateTime.format(DISPLAY_FORMAT);
        } catch (Exception e) {
            return "Invalid timestamp";
        }
    }

    /**
     * Get human-readable description for any status
     */
    public static String getStatusDescription(String statusString) {
        if (statusString == null) {
            return "Unknown";
        }

        if (isVersionedStatus(statusString)) {
            long timestamp = extractTimestamp(statusString);
            String formattedTime = formatTimestamp(timestamp);
            String editorType = MergingStatusConstants.getEditorType(statusString);
            return String.format("%s edited on %s", editorType, formattedTime);
        }

        return switch (statusString) {
            case MergingStatusConstants.USER_INPUT -> "User Input";
            case MergingStatusConstants.USER_IN_PROCESS -> "In Progress";
            case MergingStatusConstants.TEAM_INPUT -> "Team Input";
            case MergingStatusConstants.ADMIN_INPUT -> "Admin Input";
            case MergingStatusConstants.ADMIN_FINAL -> "Admin Finalized";
            case MergingStatusConstants.TEAM_FINAL -> "Team Finalized";
            case MergingStatusConstants.DELETE -> "Deleted";
            default -> statusString; // Return as-is if unknown
        };
    }
}