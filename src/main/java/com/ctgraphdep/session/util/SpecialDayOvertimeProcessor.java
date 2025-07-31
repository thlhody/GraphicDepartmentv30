package com.ctgraphdep.session.util;

import com.ctgraphdep.session.model.DayType;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Utility for processing special day overtime logic
 * Business Rules:
 * - SN/CO/CM/W days: All work time becomes overtime, rounded down to full hours only
 * - Regular days: Normal overtime calculation applies
 * - TimeOffType is preserved/set based on DayType
 * - Temporary stop minutes are included in the calculation
 */
public class SpecialDayOvertimeProcessor {

    /**
     * Apply special day overtime logic to a WorkTimeTable entry
     *
     * @param entry The worktime entry to update
     * @param sessionWorkedMinutes Total minutes worked from session (including temp stops)
     * @param dayType The type of day (determines logic to apply)
     * @return Updated entry with special day logic applied
     */
    public static WorkTimeTable processSpecialDayOvertime(WorkTimeTable entry, int sessionWorkedMinutes, DayType dayType) {
        if (entry == null) {
            LoggerUtil.warn(SpecialDayOvertimeProcessor.class, "Cannot process null entry");
            return null;
        }

        LoggerUtil.debug(SpecialDayOvertimeProcessor.class, String.format(
                "Processing special day overtime: dayType=%s, sessionMinutes=%d, currentTimeOff=%s",
                dayType, sessionWorkedMinutes, entry.getTimeOffType()));

        // Apply logic based on day type
        return switch (dayType) {
            case NATIONAL_HOLIDAY, TIME_OFF, MEDICAL_LEAVE, WEEKEND ->
                    applySpecialDayLogic(entry, sessionWorkedMinutes, dayType);
            case REGULAR_DAY -> applyRegularDayLogic(entry, sessionWorkedMinutes);
        };
    }

    /**
     * Apply special day logic: all time becomes overtime, rounded down to full hours
     */
    private static WorkTimeTable applySpecialDayLogic(WorkTimeTable entry, int sessionWorkedMinutes, DayType dayType) {
        if (sessionWorkedMinutes <= 0) {
            LoggerUtil.debug(SpecialDayOvertimeProcessor.class, "No work time to process for special day");
            // Still set/preserve the timeOffType
            setTimeOffType(entry, dayType);
            return entry;
        }

        // Round down to full hours only (special day rule)
        int fullHours = sessionWorkedMinutes / 60;
        int overtimeMinutes = fullHours * 60;
        int discardedMinutes = sessionWorkedMinutes % 60;

        LoggerUtil.info(SpecialDayOvertimeProcessor.class, String.format(
                "Special day calculation: %d total minutes → %d full hours (%d minutes) → %d discarded",
                sessionWorkedMinutes, fullHours, overtimeMinutes, discardedMinutes));

        // Apply special day logic
        entry.setTotalWorkedMinutes(0);  // No regular work time on special days
        entry.setTotalOvertimeMinutes(overtimeMinutes);  // All work becomes overtime

        // Set/preserve timeOffType
        setTimeOffType(entry, dayType);

        LoggerUtil.info(SpecialDayOvertimeProcessor.class, String.format(
                "Applied special day logic: timeOffType=%s, regularMinutes=0, overtimeMinutes=%d",
                entry.getTimeOffType(), entry.getTotalOvertimeMinutes()));

        return entry;
    }

    /**
     * Apply regular day logic: keep session values as-is
     */
    private static WorkTimeTable applyRegularDayLogic(WorkTimeTable entry, int sessionWorkedMinutes) {
        LoggerUtil.debug(SpecialDayOvertimeProcessor.class, String.format(
                "Applying regular day logic: sessionMinutes=%d", sessionWorkedMinutes));

        // For regular days, keep the session calculation as-is
        entry.setTotalWorkedMinutes(sessionWorkedMinutes);

        // Don't modify overtime here - let normal calculation handle it
        // entry.setTotalOvertimeMinutes() is handled elsewhere for regular days

        // Don't set timeOffType for regular days (should remain null)

        return entry;
    }

    /**
     * Set or preserve the timeOffType based on DayType
     */
    private static void setTimeOffType(WorkTimeTable entry, DayType dayType) {
        String currentTimeOffType = entry.getTimeOffType();
        String expectedTimeOffCode = dayType.getTimeOffCode();

        // If entry already has the correct timeOffType, preserve it
        if (currentTimeOffType != null && currentTimeOffType.equals(expectedTimeOffCode)) {
            LoggerUtil.debug(SpecialDayOvertimeProcessor.class, String.format(
                    "Preserving existing timeOffType: %s", currentTimeOffType));
            return;
        }

        // If entry has different timeOffType, but it's a valid special day type, preserve it
        if (currentTimeOffType != null && !currentTimeOffType.trim().isEmpty()) {
            DayType existingDayType = DayType.fromTimeOffCode(currentTimeOffType);
            if (existingDayType.requiresSpecialOvertimeLogic()) {
                LoggerUtil.debug(SpecialDayOvertimeProcessor.class, String.format(
                        "Preserving existing special day timeOffType: %s (instead of %s)",
                        currentTimeOffType, expectedTimeOffCode));
                return;
            }
        }

        // Set the new timeOffType
        entry.setTimeOffType(expectedTimeOffCode);
        LoggerUtil.debug(SpecialDayOvertimeProcessor.class, String.format(
                "Set timeOffType to: %s", expectedTimeOffCode));
    }

    /**
     * Check if a DayType requires special overtime processing
     */
    public static boolean requiresSpecialProcessing(DayType dayType) {
        return dayType != null && dayType.requiresSpecialOvertimeLogic();
    }

    /**
     * Calculate discarded minutes for special days (partial hours not counted)
     */
    public static int calculateDiscardedMinutes(int totalMinutes, DayType dayType) {
        if (!requiresSpecialProcessing(dayType)) {
            return 0; // Regular days don't discard partial hours
        }

        return totalMinutes % 60; // Minutes that don't make a full hour
    }

    /**
     * Get display format for frontend (e.g., "SN3", "CO4", "W2")
     */
    public static String getDisplayFormat(WorkTimeTable entry) {
        if (entry == null || entry.getTimeOffType() == null || entry.getTotalOvertimeMinutes() == null) {
            return null;
        }

        if (entry.getTotalOvertimeMinutes() <= 0) {
            return entry.getTimeOffType(); // Just "SN", "CO", etc.
        }

        int overtimeHours = entry.getTotalOvertimeMinutes() / 60;
        return entry.getTimeOffType() + overtimeHours; // "SN3", "CO4", etc.
    }
}