package com.ctgraphdep.session.query;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

// Query to determine schedule-related information including weekend status, work durations, and schedule-specific calculations.
public class WorkScheduleQuery implements SessionQuery<WorkScheduleQuery.ScheduleInfo> {

    private final LocalDate date;
    private final Integer userSchedule;

    // Creates a query for a specific date with specified user schedule
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
                dateToUse = context.getCurrentStandardDate();
            }

            boolean isWeekend = context.isWeekend(dateToUse);

            // Normalize schedule (if null or invalid, default to 8 hours)
            int normalizedSchedule = context.normalizeSchedule(userSchedule);

            // Calculate schedule durations
            int scheduledMinutes = normalizedSchedule * WorkCode.HOUR_DURATION;
            int fullDayDuration = context.calculateFullDayDuration(normalizedSchedule);

            // Calculate expected end time
            LocalTime expectedEndTime = context.calculateExpectedEndTime(normalizedSchedule, isWeekend);

            // Calculate lunch break info
            boolean includesLunchBreak = context.includesLunchBreak(normalizedSchedule);
            int lunchBreakDuration = includesLunchBreak ? WorkCode.HALF_HOUR_DURATION : WorkCode.DEFAULT_ZERO;

            // Pass SessionContext to ScheduleInfo for proper calculation service access
            return new ScheduleInfo(dateToUse, isWeekend, normalizedSchedule,
                    scheduledMinutes, fullDayDuration, expectedEndTime,
                    includesLunchBreak, lunchBreakDuration, context);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in WorkScheduleQuery: " + e.getMessage(), e);
            // Return default values as fallback
            return new ScheduleInfo(
                    date != null ? date : context.getCurrentStandardDate(),
                    false,
                    WorkCode.INTERVAL_HOURS_C,
                    WorkCode.INTERVAL_HOURS_C * WorkCode.HOUR_DURATION,
                    (WorkCode.INTERVAL_HOURS_C * WorkCode.HOUR_DURATION) + WorkCode.HALF_HOUR_DURATION,
                    LocalTime.of(WorkCode.DEFAULT_END_HOUR, WorkCode.DEFAULT_ZERO),
                    true,
                    WorkCode.HALF_HOUR_DURATION,
                    context
            );
        }
    }

    //Value class to hold schedule information
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
        private final SessionContext context;

        public ScheduleInfo(LocalDate date, boolean isWeekend, int scheduleHours, int scheduledMinutes, int fullDayDuration, LocalTime expectedEndTime,
                            boolean includesLunchBreak, int lunchBreakDuration, SessionContext context) {
            this.date = date;
            this.isWeekend = isWeekend;
            this.scheduleHours = scheduleHours;
            this.scheduledMinutes = scheduledMinutes;
            this.fullDayDuration = fullDayDuration;
            this.expectedEndTime = expectedEndTime;
            this.includesLunchBreak = includesLunchBreak;
            this.lunchBreakDuration = lunchBreakDuration;
            this.context = context; // Store context for calculations
        }

        // Checks if a specific time meets or exceeds the scheduled duration
        public boolean isScheduleCompleted(int workedMinutes) {
            return workedMinutes >= fullDayDuration;
        }

        // Calculate overtime minutes using SessionContext instead of direct utility
        public int calculateOvertimeMinutes(int workedMinutes) {
            try {
                // Use SessionContext to access CalculationService properly
                WorkTimeCalculationResultDTO result = context.calculateWorkTime(workedMinutes, scheduleHours);
                return result.getOvertimeMinutes();
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error calculating overtime minutes: " + e.getMessage(), e);
                return WorkCode.DEFAULT_ZERO; // Safe fallback
            }
        }
    }
}