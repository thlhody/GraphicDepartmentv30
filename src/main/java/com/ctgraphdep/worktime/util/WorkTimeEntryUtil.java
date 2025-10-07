package com.ctgraphdep.worktime.util;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Utility class for common WorkTimeTable operations.
 * This consolidates duplicated methods from various services.
 */
public class WorkTimeEntryUtil {

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
        copy.setTemporaryStops(source.getTemporaryStops());
        copy.setLunchBreakDeducted(source.isLunchBreakDeducted());
        copy.setTimeOffType(source.getTimeOffType());
        copy.setTotalWorkedMinutes(source.getTotalWorkedMinutes());
        copy.setTotalTemporaryStopMinutes(source.getTotalTemporaryStopMinutes());
        copy.setTotalOvertimeMinutes(source.getTotalOvertimeMinutes());
        copy.setAdminSync(source.getAdminSync());
        return copy;
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
            LoggerUtil.warn(WorkTimeEntryUtil.class, String.format("YearMonth %s is outside reasonable range (%s to %s)", yearMonth, minYearMonth, maxYearMonth));
        }
    }

    /**
     * Checks if an entry is displayable (not ADMIN_BLANK)
     */
    public static boolean isEntryDisplayable(WorkTimeTable entry) {
        if (entry == null) return false;

        // Display USER_IN_PROCESS entries with partial info
        if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
            return true;
        }

        // Show all other valid entries
        return entry.getAdminSync() != null;
    }

}