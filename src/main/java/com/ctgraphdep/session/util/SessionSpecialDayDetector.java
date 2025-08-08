package com.ctgraphdep.session.util;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.model.DayType;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.DayOfWeek;
import java.time.LocalDate;

// Utility class for detecting special day types in session commands and applying appropriate special day overtime logic.
public class SessionSpecialDayDetector {

    // Detect the day type for a given work date Priority: Existing timeOffType > Weekend > Regular Day
    public static DayType detectDayType(LocalDate workDate, String username, Integer userId, SessionContext context) {
        try {
            LoggerUtil.debug(SessionSpecialDayDetector.class, String.format("Detecting day type for %s (user: %s)", workDate, username));

            // 1. Check existing worktime entry for timeOffType (highest priority)
            WorkTimeTable existingEntry = context.findSessionEntry(username, userId, workDate);
            if (existingEntry != null && existingEntry.getTimeOffType() != null && !existingEntry.getTimeOffType().trim().isEmpty()) {
                DayType existingDayType = DayType.fromTimeOffCode(existingEntry.getTimeOffType());
                LoggerUtil.info(SessionSpecialDayDetector.class, String.format("Found existing timeOffType: %s -> %s", existingEntry.getTimeOffType(), existingDayType));
                return existingDayType;
            }

            // 2. Check if weekend (second priority)
            if (isWeekend(workDate)) {
                LoggerUtil.info(SessionSpecialDayDetector.class, String.format("Detected weekend work: %s", workDate));
                return DayType.WEEKEND;
            }

            // 3. Default to regular day
            LoggerUtil.debug(SessionSpecialDayDetector.class, String.format("Regular work day: %s", workDate));
            return DayType.REGULAR_DAY;

        } catch (Exception e) {
            LoggerUtil.error(SessionSpecialDayDetector.class, String.format("Error detecting day type for %s: %s", workDate, e.getMessage()), e);
            return DayType.REGULAR_DAY; // Safe fallback
        }
    }

    // Check if a date is a weekend (Saturday or Sunday)
    public static boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    // Apply special day logic to a worktime entry if needed
    public static WorkTimeTable applySpecialDayLogic(WorkTimeTable entry, WorkUsersSessionsStates session, DayType dayType) {
        try {
            if (dayType == null || !dayType.requiresSpecialOvertimeLogic()) {
                LoggerUtil.debug(SessionSpecialDayDetector.class, "Regular day - no special logic needed");
                return entry;
            }

            if (session == null) {
                LoggerUtil.warn(SessionSpecialDayDetector.class, "Session is null - cannot apply special day logic");
                return entry;
            }

            // Calculate total session minutes (worked + temp stop)
            int totalSessionMinutes = calculateTotalSessionMinutes(session);

            LoggerUtil.info(SessionSpecialDayDetector.class, String.format("Applying special day logic: dayType=%s, totalMinutes=%d", dayType, totalSessionMinutes));

            // Use the existing SpecialDayOvertimeProcessor
            return SpecialDayOvertimeProcessor.processSpecialDayOvertime(entry, totalSessionMinutes, dayType);

        } catch (Exception e) {
            LoggerUtil.error(SessionSpecialDayDetector.class, String.format("Error applying special day logic: %s", e.getMessage()), e);
            return entry; // Return unchanged entry if error
        }
    }

    // Calculate total session minutes including temp stops for special day logic
    private static int calculateTotalSessionMinutes(WorkUsersSessionsStates session) {
        int totalMinutes = 0;

        // Add worked minutes
        if (session.getTotalWorkedMinutes() != null) {
            totalMinutes += session.getTotalWorkedMinutes();
        }

        // For special days, temp stop time is also counted as work time (overtime)
        if (session.getTotalTemporaryStopMinutes() != null) {
            totalMinutes += session.getTotalTemporaryStopMinutes();
        }

        LoggerUtil.debug(SessionSpecialDayDetector.class, String.format("Calculated total session minutes: worked=%d, tempStop=%d, total=%d",
                        session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0,
                        session.getTotalTemporaryStopMinutes() != null ? session.getTotalTemporaryStopMinutes() : 0, totalMinutes));

        return totalMinutes;
    }

    // Update worktime entry with special day logic integrated into session commands
    public static void updateWorktimeEntryWithSpecialDayLogic(WorkUsersSessionsStates session, String username, Integer userId, SessionContext context) {
        try {
            if (session == null || session.getDayStartTime() == null) {
                LoggerUtil.warn(SessionSpecialDayDetector.class, "Invalid session - cannot update worktime entry");
                return;
            }

            LocalDate workDate = session.getDayStartTime().toLocalDate();
            LoggerUtil.debug(SessionSpecialDayDetector.class, String.format("Updating worktime entry with special day logic for %s", workDate));

            // 1. Detect day type
            DayType dayType = detectDayType(workDate, username, userId, context);

            // 2. Find or create worktime entry
            WorkTimeTable entry = context.findSessionEntry(username, userId, workDate);
            if (entry == null) {
                LoggerUtil.debug(SessionSpecialDayDetector.class, "Creating new worktime entry from session");
                entry = context.createWorktimeEntryFromSession(session);
            } else {
                LoggerUtil.debug(SessionSpecialDayDetector.class, "Updating existing worktime entry");
                entry = context.updateEntryFromSession(entry, session);
            }

            // 3. Apply special day logic if needed
            if (dayType.requiresSpecialOvertimeLogic()) {
                LoggerUtil.info(SessionSpecialDayDetector.class, String.format("Applying special day logic for %s day", dayType));
                entry = applySpecialDayLogic(entry, session, dayType);
            } else {
                LoggerUtil.debug(SessionSpecialDayDetector.class, "Regular day - using normal logic");
            }

            // 4. Save updated entry
            context.saveSessionWorktime(username, entry, workDate.getYear(), workDate.getMonthValue());

            LoggerUtil.info(SessionSpecialDayDetector.class, String.format("Updated worktime entry: timeOffType=%s, regularMinutes=%d, overtimeMinutes=%d",
                            entry.getTimeOffType(), entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                            entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0));

        } catch (Exception e) {
            LoggerUtil.error(SessionSpecialDayDetector.class, String.format("Failed to update worktime entry with special day logic: %s", e.getMessage()), e);
        }
    }

    // Helper method to check if a day type needs special processing
    public static boolean needsSpecialProcessing(DayType dayType) {
        return dayType != null && dayType.requiresSpecialOvertimeLogic();
    }
}