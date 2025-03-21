package com.ctgraphdep.utils;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.service.UserWorkTimeService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

public class CalculateWorkHoursUtil {

    private static boolean shouldDeductLunch(int inputMinutes, int schedule) {
        if (schedule < WorkCode.INTERVAL_HOURS_C) {
            return true; // Lunch break is true for schedules less than 8 hours, but we don't deduct time
        } else if (schedule == WorkCode.INTERVAL_HOURS_C) {
            int hours = inputMinutes / WorkCode.HOUR_DURATION;
            return hours > WorkCode.INTERVAL_HOURS_A && hours <= WorkCode.INTERVAL_HOURS_B;
        } else {
            return true;
        }
    }

    public static boolean isLunchBreakDeducted(int inputMinutes, int schedule) {
        int hours = inputMinutes / WorkCode.HOUR_DURATION;

        // If schedule is less than 8 hours (INTERVAL_HOURS_C)
        if (schedule < WorkCode.INTERVAL_HOURS_C) {
            return true;
        }
        // For 8 hour schedule
        else if (schedule == WorkCode.INTERVAL_HOURS_C) {
            // For hours between 4-11, check if enough time worked to have lunch deducted
            if (hours > WorkCode.INTERVAL_HOURS_A && hours <= WorkCode.INTERVAL_HOURS_B) {
                // Check if worked at least 4 hours and 30 minutes (270 minutes)
                return inputMinutes >= (WorkCode.INTERVAL_HOURS_A * WorkCode.HOUR_DURATION + WorkCode.HALF_HOUR_DURATION);
            }
            // For hours > 11
            else if (hours > WorkCode.INTERVAL_HOURS_B) {
                return true;
            }
            // For hours <= 4
            else {
                return true;
            }
        }
        // For schedules > 8 hours
        else {
            return true;
        }
    }

    public static int calculateAdjustedMinutes(int inputMinutes, int schedule) {
        if (schedule == WorkCode.INTERVAL_HOURS_C && shouldDeductLunch(inputMinutes, schedule)) {
            return inputMinutes - WorkCode.HALF_HOUR_DURATION;
        }
        return inputMinutes;
    }

    public static int calculateProcessedMinutes(int adjustedMinutes, int schedule) {
        // Round down to the nearest hour for all cases
        int roundedMinutes = (adjustedMinutes / WorkCode.HOUR_DURATION) * WorkCode.HOUR_DURATION;
        int scheduledMinutes = schedule * WorkCode.HOUR_DURATION;
        return Math.min(roundedMinutes, scheduledMinutes);
    }

    public static int calculateOvertimeMinutes(int adjustedMinutes, int schedule) {
        int scheduledMinutes = schedule * WorkCode.HOUR_DURATION;
        int overtimeMinutes = Math.max(0, adjustedMinutes - scheduledMinutes);
        // Round down to the nearest hour
        return (overtimeMinutes / WorkCode.HOUR_DURATION) * WorkCode.HOUR_DURATION;
    }

    public static WorkTimeCalculationResult calculateWorkTime(int inputMinutes, int schedule) {
        // Calculate lunch break first
        boolean lunchDeducted = isLunchBreakDeducted(inputMinutes, schedule);
        // Adjust minutes based on lunch break
        int adjustedMinutes = calculateAdjustedMinutes(inputMinutes, schedule);

        // Round down to the nearest hour for schedules less than 8 hours
        if (schedule < WorkCode.INTERVAL_HOURS_C) {
            adjustedMinutes = (adjustedMinutes / WorkCode.HOUR_DURATION) * WorkCode.HOUR_DURATION;
        }
        // Calculate final values
        int processedMinutes = calculateProcessedMinutes(adjustedMinutes, schedule);
        int overtimeMinutes = calculateOvertimeMinutes(adjustedMinutes, schedule);
        int finalTotalMinutes = processedMinutes + overtimeMinutes;

        // Return complete result including raw minutes for session update
        return new WorkTimeCalculationResult(inputMinutes, processedMinutes, overtimeMinutes, lunchDeducted, finalTotalMinutes);
    }

    //display
    public static String minutesToHHmm(Integer minutes) {
        int hours = minutes /  WorkCode.HOUR_DURATION;
        int remainingMinutes = minutes %  WorkCode.HOUR_DURATION;
        return String.format("%02d:%02d", hours, remainingMinutes);
    }

    public static String minutesToHH(Integer minutes) {
        if (minutes == null) return "0";
        return String.format("%d", minutes / WorkCode.HOUR_DURATION);
    }

    public static int calculateWorkDays(Integer year, Integer month) {
        LocalDate date = LocalDate.of(year, month, 1);
        int daysInMonth = date.lengthOfMonth();
        return (int) IntStream.rangeClosed(1, daysInMonth)
                .mapToObj(date::withDayOfMonth)
                .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY)
                .count();
    }

    // New method that also considers national holidays
    public static int calculateWorkDays(LocalDate startDate, LocalDate endDate, UserWorkTimeService workTimeService) {
        return (int) startDate.datesUntil(endDate.plusDays(1))
                .filter(date -> date.getDayOfWeek() != DayOfWeek.SATURDAY &&
                        date.getDayOfWeek() != DayOfWeek.SUNDAY)
                .filter(date -> !workTimeService.isNationalHoliday(date))  // Changed to NOT
                .peek(date -> LoggerUtil.debug(CalculateWorkHoursUtil.class,
                        String.format("Including workday: %s", date)))
                .count();
    }

    public static int calculateMinutesBetween(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return 0;
        }
        return (int) ChronoUnit.MINUTES.between(startTime, endTime);
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy :: HH:mm")) : "--/--/---- :: --:--";
    }

    /**
     * Calculates the recommended end time for a worktime entry based on user schedule
     * @param entry The work time entry
     * @param userSchedule The user's schedule in hours
     * @return The recommended end time
     */
    public static LocalDateTime calculateRecommendedEndTime(WorkTimeTable entry, int userSchedule) {
        if (entry == null || entry.getDayStartTime() == null) {
            return LocalDateTime.now(); // Fallback
        }

        // 1. Start with the day start time
        LocalDateTime startTime = entry.getDayStartTime();

        // 2. Calculate schedule duration in minutes
        int scheduleDuration = userSchedule * WorkCode.HOUR_DURATION;

        // 3. Add lunch break for 8-hour schedule
        if (userSchedule == WorkCode.INTERVAL_HOURS_C) { // 8 hours
            scheduleDuration += WorkCode.HALF_HOUR_DURATION; // Add 30 minutes for lunch
        }

        // 4. Add schedule duration to start time
        LocalDateTime scheduledEndTime = startTime.plusMinutes(scheduleDuration);

        // 5. Add temporary stop minutes if any
        if (entry.getTotalTemporaryStopMinutes() != null && entry.getTotalTemporaryStopMinutes() > 0) {
            scheduledEndTime = scheduledEndTime.plusMinutes(entry.getTotalTemporaryStopMinutes());
        }

        return scheduledEndTime;
    }

    /**
     * Calculates raw work minutes for a session (minutes worked excluding adjustments)
     * This method handles all the logic for calculating raw work time from start to end,
     * accounting for temporary stops.
     *
     * @param session The session containing work data
     * @param currentTime The current time reference for ongoing sessions
     * @return The total raw minutes worked
     */
    public static int calculateRawWorkMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        if (session == null || session.getDayStartTime() == null) {
            return 0;
        }

        int totalMinutes = 0;
        LocalDateTime currentStartPoint = session.getDayStartTime();

        // Handle completed temporary stops
        if (session.getTemporaryStops() != null) {
            for (TemporaryStop stop : session.getTemporaryStops()) {
                // Only count stops that have ended
                if (stop.getEndTime() != null) {
                    // Add worked minutes before each stop
                    totalMinutes += calculateMinutesBetween(currentStartPoint, stop.getStartTime());
                    // Move current time to after the stop
                    currentStartPoint = stop.getEndTime();
                }
            }
        }

        // Add minutes from last stop (or start) until current time
        totalMinutes += calculateMinutesBetween(currentStartPoint, currentTime);

        return totalMinutes;
    }

    /**
     * Calculates raw work minutes for a worktime entry between start and end time,
     * subtracting temporary stops
     *
     * @param entry The work time entry
     * @param endTime The end time to calculate to
     * @return Total worked minutes
     */
    public static int calculateRawWorkMinutes(WorkTimeTable entry, LocalDateTime endTime) {
        if (entry == null || entry.getDayStartTime() == null) {
            return 0;
        }

        // Total minutes between start and end
        int totalMinutes = (int) ChronoUnit.MINUTES.between(entry.getDayStartTime(), endTime);

        // Subtract temporary stop minutes if any
        if (entry.getTotalTemporaryStopMinutes() != null && entry.getTotalTemporaryStopMinutes() > 0) {
            totalMinutes -= entry.getTotalTemporaryStopMinutes();
        }

        return Math.max(0, totalMinutes);
    }

    /**
     * Calculates raw work minutes between two times with a list of temporary stops
     *
     * @param startTime The start time
     * @param endTime The end time
     * @param stops List of temporary stops during the period
     * @return Total worked minutes excluding stops
     */
    public static int calculateRawWorkMinutes(LocalDateTime startTime, LocalDateTime endTime, List<TemporaryStop> stops) {
        if (startTime == null || endTime == null) {
            return 0;
        }

        // Total minutes between start and end
        int totalMinutes = (int) ChronoUnit.MINUTES.between(startTime, endTime);

        // Subtract temporary stop minutes if any
        if (stops != null && !stops.isEmpty()) {
            int totalStopMinutes = 0;
            for (TemporaryStop stop : stops) {
                if (stop.getStartTime() != null && stop.getEndTime() != null) {
                    totalStopMinutes += calculateMinutesBetween(stop.getStartTime(), stop.getEndTime());
                }
            }
            totalMinutes -= totalStopMinutes;
        }

        return Math.max(0, totalMinutes);
    }
}