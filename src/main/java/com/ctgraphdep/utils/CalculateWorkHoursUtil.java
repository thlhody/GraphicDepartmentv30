package com.ctgraphdep.utils;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.service.UserWorkTimeService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.stream.IntStream;

public class CalculateWorkHoursUtil {

    public static boolean shouldDeductLunch(int inputMinutes, int schedule) {
        if (schedule < WorkCode.INTERVAL_HOURS_C) {
            return true; // Lunch break is true for schedules less than 8 hours, but we don't deduct time
        } else if (schedule == WorkCode.INTERVAL_HOURS_C) {
            int hours = inputMinutes / WorkCode.HOUR_DURATION;
            return hours > WorkCode.INTERVAL_HOURS_A && hours <= WorkCode.INTERVAL_HOURS_B;
        } else {
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
        boolean lunchDeducted = shouldDeductLunch(inputMinutes, schedule);
        int adjustedMinutes = calculateAdjustedMinutes(inputMinutes, schedule);

        // Round down to the nearest hour for schedules less than 8 hours
        if (schedule < WorkCode.INTERVAL_HOURS_C) {
            adjustedMinutes = (adjustedMinutes / WorkCode.HOUR_DURATION) * WorkCode.HOUR_DURATION;
        }

        int processedMinutes = calculateProcessedMinutes(adjustedMinutes, schedule);
        int overtimeMinutes = calculateOvertimeMinutes(adjustedMinutes, schedule);
        int finalTotalMinutes = processedMinutes + overtimeMinutes;

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
    public static int calculateWorkDays(LocalDate startDate, LocalDate endDate,
                                        UserWorkTimeService workTimeService) {
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

    // New method to convert hours to minutes with lunch break addition
    public static int hoursToMinutes(int hours) {
        int baseMinutes = hours * WorkCode.HOUR_DURATION;
        return baseMinutes + WorkCode.HALF_HOUR_DURATION; // Add 30 minutes for lunch
    }

    // New method to calculate end time based on start hour and total minutes
    public static LocalDateTime calculateEndTime(LocalDate date, int totalMinutes) {
        return date.atTime(WorkCode.START_HOUR, 0).plusMinutes(totalMinutes);
    }

}