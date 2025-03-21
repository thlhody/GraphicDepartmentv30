package com.ctgraphdep.session.query;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Query to determine schedule-related information including weekend status,
 * work durations, and schedule-specific calculations.
 */
public class WorkScheduleQuery implements SessionQuery<WorkScheduleQuery.ScheduleInfo> {

    private final LocalDate date;
    private final Integer userSchedule;

    /**
     * Creates a query for the current date with specified user schedule
     *
     * @param userSchedule The user's schedule in hours
     */
    public WorkScheduleQuery(Integer userSchedule) {
        this.date = LocalDate.now();
        this.userSchedule = userSchedule;
    }

    /**
     * Creates a query for a specific date with specified user schedule
     *
     * @param date The date to check
     * @param userSchedule The user's schedule in hours
     */
    public WorkScheduleQuery(LocalDate date, Integer userSchedule) {
        this.date = date;
        this.userSchedule = userSchedule;
    }

    @Override
    public ScheduleInfo execute(SessionContext context) {
        try {
            // Get standardized time values if date is null
            LocalDate dateToUse = date;
            if (dateToUse == null) {
                // Get standardized time values using the new validation system
                GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
                GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);
                dateToUse = timeValues.getCurrentDate();
            }

            // Calculate if it's a weekend
            boolean isWeekend = isWeekend(dateToUse);

            // Normalize schedule (if null or invalid, default to 8 hours)
            int normalizedSchedule = normalizeSchedule(userSchedule);

            // Calculate schedule durations
            int scheduledMinutes = normalizedSchedule * WorkCode.HOUR_DURATION;
            int fullDayDuration = calculateFullDayDuration(normalizedSchedule);

            // Calculate expected end time
            LocalTime expectedEndTime = calculateExpectedEndTime(dateToUse, normalizedSchedule, isWeekend);

            // Calculate lunch break info
            boolean includesLunchBreak = includesLunchBreak(normalizedSchedule);
            int lunchBreakDuration = includesLunchBreak ? WorkCode.HALF_HOUR_DURATION : 0;

            return new ScheduleInfo(
                    dateToUse,
                    isWeekend,
                    normalizedSchedule,
                    scheduledMinutes,
                    fullDayDuration,
                    expectedEndTime,
                    includesLunchBreak,
                    lunchBreakDuration
            );

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in WorkScheduleQuery: " + e.getMessage(), e);
            // Return default values as fallback
            return new ScheduleInfo(
                    date != null ? date : LocalDate.now(),
                    false,
                    WorkCode.INTERVAL_HOURS_C,
                    WorkCode.INTERVAL_HOURS_C * WorkCode.HOUR_DURATION,
                    (WorkCode.INTERVAL_HOURS_C * WorkCode.HOUR_DURATION) + WorkCode.HALF_HOUR_DURATION,
                    LocalTime.of(17, 0),
                    true,
                    WorkCode.HALF_HOUR_DURATION
            );
        }
    }

    /**
     * Checks if a date is a weekend (Saturday or Sunday)
     */
    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /**
     * Normalizes schedule hours (defaults to 8 if invalid)
     */
    private int normalizeSchedule(Integer schedule) {
        if (schedule == null || schedule <= 0) {
            return WorkCode.INTERVAL_HOURS_C; // Default to 8 hours
        }
        return schedule;
    }

    /**
     * Calculates the full day duration in minutes, accounting for lunch break
     */
    private int calculateFullDayDuration(int schedule) {
        // For 8-hour schedule: 8.5 hours (510 minutes)
        // For others: schedule hours + lunch break if applicable
        if (schedule == WorkCode.INTERVAL_HOURS_C) {
            return (schedule * WorkCode.HOUR_DURATION) + WorkCode.HALF_HOUR_DURATION;
        } else {
            return schedule * WorkCode.HOUR_DURATION;
        }
    }

    /**
     * Calculates the expected end time based on schedule
     */
    private LocalTime calculateExpectedEndTime(LocalDate date, int schedule, boolean isWeekend) {
        // Weekend special case (end at 1 PM)
        if (isWeekend) {
            return LocalTime.of(13, 0);
        }

        // For 8-hour schedule, default end time is 17:00 (5 PM)
        if (schedule == WorkCode.INTERVAL_HOURS_C) {
            return LocalTime.of(17, 0);
        }

        // For custom schedules, assume start at 9 and add schedule hours
        return LocalTime.of(9 + schedule, 0);
    }

    /**
     * Checks if a schedule includes a lunch break
     */
    private boolean includesLunchBreak(int schedule) {
        return schedule == WorkCode.INTERVAL_HOURS_C;
    }

    /**
     * Value class to hold schedule information
     */
    @Getter
    public static class ScheduleInfo {
        private final LocalDate date;
        private final boolean isWeekend;
        private final int scheduleHours;
        private final int scheduledMinutes;
        private final int fullDayDuration;
        private final LocalTime expectedEndTime;
        private final boolean includesLunchBreak;
        private final int lunchBreakDuration;

        public ScheduleInfo(
                LocalDate date,
                boolean isWeekend,
                int scheduleHours,
                int scheduledMinutes,
                int fullDayDuration,
                LocalTime expectedEndTime,
                boolean includesLunchBreak,
                int lunchBreakDuration) {
            this.date = date;
            this.isWeekend = isWeekend;
            this.scheduleHours = scheduleHours;
            this.scheduledMinutes = scheduledMinutes;
            this.fullDayDuration = fullDayDuration;
            this.expectedEndTime = expectedEndTime;
            this.includesLunchBreak = includesLunchBreak;
            this.lunchBreakDuration = lunchBreakDuration;
        }

        public boolean isWeekend() {
            return isWeekend;
        }

        public boolean isWeekday() {
            return !isWeekend;
        }

        public LocalDateTime getExpectedEndDateTime() {
            return LocalDateTime.of(date, expectedEndTime);
        }

        public boolean includesLunchBreak() {
            return includesLunchBreak;
        }

        public boolean isStandardEightHourSchedule() {
            return scheduleHours == WorkCode.INTERVAL_HOURS_C;
        }

        /**
         * Calculates the appropriate end time for resolving a session
         */
        public LocalDateTime getRecommendedEndTime() {
            return LocalDateTime.of(date, expectedEndTime);
        }

        /**
         * Checks if a specific time meets or exceeds the scheduled duration
         */
        public boolean isScheduleCompleted(int workedMinutes) {
            return workedMinutes >= fullDayDuration;
        }

        /**
         * Calculates overtime minutes if any
         * Delegates to CalculateWorkHoursUtil for consistency
         */
        public int calculateOvertimeMinutes(int workedMinutes) {
            return CalculateWorkHoursUtil.calculateOvertimeMinutes(workedMinutes, scheduleHours);
        }
    }
}