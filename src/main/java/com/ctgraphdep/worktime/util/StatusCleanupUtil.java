package com.ctgraphdep.worktime.util;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.util.List;

/**
 * Utility for cleaning up old enum-based adminSync statuses during merge/consolidation processes.
 * This ensures that old SyncStatusMerge enum values are converted to new string-based statuses
 * and persisted back to JSON files.
 * Cleanup Period: Active until manually removed (approximately 2 months from deployment)
 * Purpose: Ensure all old enum values are converted and persisted in file system
 */
public class StatusCleanupUtil {

    // Cleanup period - will be manually removed when conversion is complete
    private static final LocalDate CLEANUP_END_DATE = LocalDate.of(2025, 12, 1);

    static {
        LoggerUtil.initialize(StatusCleanupUtil.class, null);
    }

    /**
     * Clean up old enum-based statuses in a list of WorkTimeTable entries.
     * Converts old enum values to new string constants and logs conversions.
     * @param entries List of WorkTimeTable entries to clean up
     * @param source Description of the source (for logging)
     * @return true if any entries were modified, false otherwise
     */
    public static boolean cleanupStatuses(List<WorkTimeTable> entries, String source) {
        if (!isCleanupPeriodActive()) {
            return false;
        }

        if (entries == null || entries.isEmpty()) {
            return false;
        }

        boolean hasChanges = false;
        int conversionCount = 0;

        for (WorkTimeTable entry : entries) {
            String original = entry.getAdminSync();
            String cleaned = convertOldStatusToNew(original);

            if (!cleaned.equals(original)) {
                entry.setAdminSync(cleaned);
                hasChanges = true;
                conversionCount++;

                LoggerUtil.debug(StatusCleanupUtil.class, String.format("Status conversion [%s]: '%s' → '%s' [%s, userId=%d]",
                        source, original, cleaned, entry.getWorkDate(), entry.getUserId()));
            }
        }

        if (conversionCount > 0) {
            LoggerUtil.info(StatusCleanupUtil.class, String.format("Status cleanup [%s]: %d entries converted to new format", source, conversionCount));
        } else {
            LoggerUtil.debug(StatusCleanupUtil.class, String.format("Status cleanup [%s]: %d entries checked, no conversions needed", source, entries.size()));
        }

        return hasChanges;
    }

    /**
     * Convert old/unknown statuses to USER_INPUT, preserve valid new format statuses
     * @param oldStatus The old status (possibly enum-based)
     * @return New string-based status constant
     */
    private static String convertOldStatusToNew(String oldStatus) {
        if (oldStatus == null) {
            return MergingStatusConstants.USER_INPUT;
        }

        // Preserve valid new format statuses
        if (isNewFormatStatus(oldStatus)) {
            return oldStatus;
        }

        // Convert ALL old/unknown statuses to USER_INPUT (including old ADMIN_BLANK, DELETE, etc.)
        LoggerUtil.debug(StatusCleanupUtil.class, String.format("Converting old status '%s' to USER_INPUT", oldStatus));
        return MergingStatusConstants.USER_INPUT;
    }

    /**
     * Check if status is already in new format (should be preserved).
     *
     * @param status The status to check
     * @return true if status is in new format, false if it needs conversion
     */
    private static boolean isNewFormatStatus(String status) {
        if (status == null) return false;

        return MergingStatusConstants.USER_INPUT.equals(status) ||
                MergingStatusConstants.USER_IN_PROCESS.equals(status) ||
                MergingStatusConstants.ADMIN_INPUT.equals(status) ||
                MergingStatusConstants.TEAM_INPUT.equals(status) ||
                MergingStatusConstants.ADMIN_FINAL.equals(status) ||
                MergingStatusConstants.TEAM_FINAL.equals(status) ||
                MergingStatusConstants.isTimestampedEditStatus(status);
    }

    /**
     * Check if cleanup period is still active.
     * Will be manually removed when conversion is complete.
     *
     * @return true if cleanup should be performed, false otherwise
     */
    private static boolean isCleanupPeriodActive() {
        boolean active = LocalDate.now().isBefore(CLEANUP_END_DATE);

        if (!active) {
            LoggerUtil.info(StatusCleanupUtil.class,
                    "Status cleanup period has ended - no conversions will be performed");
        }

        return active;
    }

    /**
     * Get cleanup status information for monitoring.
     *
     * @return Status information string
     */
    public static String getCleanupStatus() {
        boolean active = isCleanupPeriodActive();
        long daysRemaining = LocalDate.now().until(CLEANUP_END_DATE).getDays();

        return String.format("Status cleanup: %s (ends %s, %d days remaining)",
                active ? "ACTIVE" : "INACTIVE",
                CLEANUP_END_DATE,
                Math.max(0, daysRemaining));
    }

    /**
     * Force cleanup status check for a single entry (for testing/debugging).
     *
     * @param entry The entry to check
     * @return Cleanup information
     */
    public static String checkEntryStatus(WorkTimeTable entry) {
        if (entry == null) return "Entry is null";

        String original = entry.getAdminSync();
        String cleaned = convertOldStatusToNew(original);
        boolean needsCleanup = !cleaned.equals(original);

        return String.format("Entry [%s, userId=%d]: status='%s' → '%s' (needs cleanup: %s)",
                entry.getWorkDate(), entry.getUserId(), original, cleaned, needsCleanup);
    }
}