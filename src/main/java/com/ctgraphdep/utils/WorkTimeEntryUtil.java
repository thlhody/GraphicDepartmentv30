package com.ctgraphdep.utils;

import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.model.WorkTimeTable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for common WorkTimeTable operations.
 * This consolidates duplicated methods from various services.
 */
public class WorktimeEntryUtil {

    /**
     * Creates a deep copy of a WorkTimeTable entry
     */
    public static WorkTimeTable copyWorkTimeEntry(WorkTimeTable source) {
        if (source == null) {
            return null;
        }

        WorkTimeTable copy = new WorkTimeTable();
        copy.setUserId(source.getUserId());
        copy.setWorkDate(source.getWorkDate());
        copy.setDayStartTime(source.getDayStartTime());
        copy.setDayEndTime(source.getDayEndTime());
        copy.setTemporaryStopCount(source.getTemporaryStopCount());
        copy.setLunchBreakDeducted(source.isLunchBreakDeducted());
        copy.setTimeOffType(source.getTimeOffType());
        copy.setTotalWorkedMinutes(source.getTotalWorkedMinutes());
        copy.setTotalTemporaryStopMinutes(source.getTotalTemporaryStopMinutes());
        copy.setTotalOvertimeMinutes(source.getTotalOvertimeMinutes());
        copy.setAdminSync(source.getAdminSync());
        return copy;
    }

    /**
     * Reset work-related fields for a time-off entry.
     */
    public static void resetWorkFields(WorkTimeTable entry) {
        if (entry == null) {
            return;
        }

        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTemporaryStopCount(0);
        entry.setLunchBreakDeducted(false);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalOvertimeMinutes(0);
    }

    /**
     * Checks if a date is a weekend
     */
    public static boolean isDateWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /**
     * Validate that year/month is within a reasonable range
     */
    public static void validateYearMonth(YearMonth yearMonth) {
        LocalDate currentDate = LocalDate.now();
        YearMonth currentYearMonth = YearMonth.from(currentDate);
        YearMonth minYearMonth = currentYearMonth.minusMonths(12);
        YearMonth maxYearMonth = currentYearMonth.plusMonths(6);

        if (yearMonth.isBefore(minYearMonth) || yearMonth.isAfter(maxYearMonth)) {
            LoggerUtil.warn(WorktimeEntryUtil.class, String.format("YearMonth %s is outside reasonable range (%s to %s)", yearMonth, minYearMonth, maxYearMonth));
        }
    }

    /**
     * Sorts worktime entries by date
     */
    public static List<WorkTimeTable> sortEntriesByDate(List<WorkTimeTable> entries) {
        return entries.stream()
                .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                .collect(Collectors.toList());
    }

    /**
     * Checks if an entry is displayable (not ADMIN_BLANK)
     */
    public static boolean isEntryDisplayable(WorkTimeTable entry) {
        if (entry == null) return false;

        // Never display ADMIN_BLANK entries
        if (SyncStatusWorktime.ADMIN_BLANK.equals(entry.getAdminSync())) {
            return false;
        }

        // Display USER_IN_PROCESS entries with partial info
        if (SyncStatusWorktime.USER_IN_PROCESS.equals(entry.getAdminSync())) {
            return true;
        }

        // Show all other valid entries
        return entry.getAdminSync() != null;
    }

    /**
     * Creates a map key for worktime entries
     */
    public static String createEntryKey(Integer userId, LocalDate date) {
        return userId + "_" + date.toString();
    }

    /**
     * Groups entries by user ID and date
     */
    public static Map<Integer, Map<LocalDate, WorkTimeTable>> groupEntriesByUserAndDate(List<WorkTimeTable> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        WorkTimeTable::getUserId,
                        Collectors.toMap(
                                WorkTimeTable::getWorkDate,
                                entry -> entry,
                                (e1, e2) -> e2  // Keep the latest in case of duplicates
                        )
                ));
    }
}