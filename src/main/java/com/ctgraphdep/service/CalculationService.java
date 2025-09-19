package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Simplified calculation service that replaces the complex command pattern.
 * Provides direct access to all calculation logic with clear method signatures.
 * This service directly uses CalculateWorkHoursUtil for core calculations and
 * provides additional business logic methods for session and worktime management.
 */
@Service
public class CalculationService {

    // ========================================================================
    // SESSION CALCULATION METHODS (Replace Command Pattern)
    // ========================================================================

    // Main router method that replaces SessionCalculationRouterCommand. Routes session calculations based on current session status.
    public WorkUsersSessionsStates updateSessionCalculations(WorkUsersSessionsStates session, LocalDateTime currentTime, int userSchedule) {

        if (session == null || currentTime == null) {
            LoggerUtil.warn(this.getClass(), "Session or currentTime is null - returning session unchanged");
            return session;
        }

        try {
            // Route based on session status
            if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                session = updateOnlineSessionCalculations(session, currentTime, userSchedule);

            } else if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                session = updateTempStopCalculations(session, currentTime);
            }

            // Always update last activity
            session.setLastActivity(currentTime);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Updated session calculations for %s: status=%s, totalMinutes=%d, overtime=%d",
                    session.getUsername(), session.getSessionStatus(),
                    session.getTotalWorkedMinutes(), session.getTotalOvertimeMinutes()));

            return session;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error updating session calculations for " + session.getUsername(), e);
            return session;
        }
    }

    // Update calculations for online sessions (replaces UpdateOnlineSessionCalculationsCommand). Calculates current work progress for active sessions.
    public WorkUsersSessionsStates updateOnlineSessionCalculations(WorkUsersSessionsStates session, LocalDateTime currentTime, int userSchedule) {

        try {
            // Calculate raw work minutes using proven utility
            int rawWorkedMinutes = CalculateWorkHoursUtil.calculateRawWorkMinutes(session, currentTime);

            // Calculate processed work time and overtime
            WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(rawWorkedMinutes, userSchedule);

            // Determine if workday is completed based on processed minutes vs schedule
            boolean workdayCompleted = result.getProcessedMinutes() >= (userSchedule * 60);

            // Update session with calculated values using builder pattern
            SessionEntityBuilder.updateSession(session, builder -> builder
                    .totalWorkedMinutes(rawWorkedMinutes)
                    .finalWorkedMinutes(result.getProcessedMinutes())
                    .totalOvertimeMinutes(result.getOvertimeMinutes())
                    .lunchBreakDeducted(result.isLunchDeducted())
                    .workdayCompleted(workdayCompleted));

            LoggerUtil.debug(this.getClass(), String.format(
                    "Online session updated: raw=%d, processed=%d, overtime=%d, lunch=%s, complete=%s",
                    rawWorkedMinutes, result.getProcessedMinutes(), result.getOvertimeMinutes(),
                    result.isLunchDeducted(), workdayCompleted));

            return session;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error updating online session calculations for " + session.getUsername(), e);
            return session;
        }
    }

    /**
     * Update calculations for temporary stop sessions (replaces UpdateTempStopCalculationsCommand).
     * Updates running total of temporary stop time.
     */
    public WorkUsersSessionsStates updateTempStopCalculations(WorkUsersSessionsStates session, LocalDateTime currentTime) {

        try {
            // Calculate total temporary stop minutes including current ongoing stop
            int totalTempStopMinutes = calculateTotalTempStopMinutes(session, currentTime);

            // Update session with current temp stop total
            SessionEntityBuilder.updateSession(session, builder -> builder
                    .totalTemporaryStopMinutes(totalTempStopMinutes));

            LoggerUtil.debug(this.getClass(), String.format(
                    "Temp stop updated for %s: total=%d minutes",
                    session.getUsername(), totalTempStopMinutes));

            return session;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error updating temp stop calculations for " + session.getUsername(), e);
            return session;
        }
    }

    /**
     * Calculate final values when ending a work day (replaces CalculateEndDayValuesCommand).
     * Processes final work time calculations and sets session to offline.
     */
    public WorkUsersSessionsStates calculateEndDayValues(WorkUsersSessionsStates session, LocalDateTime endTime, Integer finalMinutes, int userSchedule) {

        try {
            // Use provided final minutes or calculate from session data
            int totalMinutes = finalMinutes != null ? finalMinutes : CalculateWorkHoursUtil.calculateRawWorkMinutes(session, endTime);

            // Calculate final processed work time and overtime
            WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(totalMinutes, userSchedule);

            // Update session with end-of-day values
            SessionEntityBuilder.updateSession(session, builder -> builder
                    .status(WorkCode.WORK_OFFLINE)
                    .dayEndTime(endTime)
                    .totalWorkedMinutes(totalMinutes)
                    .finalWorkedMinutes(result.getProcessedMinutes())
                    .totalOvertimeMinutes(result.getOvertimeMinutes())
                    .lunchBreakDeducted(result.isLunchDeducted())
                    .workdayCompleted(true));

            LoggerUtil.info(this.getClass(), String.format(
                    "End day calculated for %s: total=%d, processed=%d, overtime=%d, ended at %s",
                    session.getUsername(), totalMinutes, result.getProcessedMinutes(),
                    result.getOvertimeMinutes(), endTime));

            return session;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating end day values for " + session.getUsername(), e);
            return session;
        }
    }

    // ========================================================================
    // TEMPORARY STOP PROCESSING METHODS
    // ========================================================================

    /**
     * Process starting a temporary stop (replaces ProcessTemporaryStopCommand).
     * Records the start time and increments the stop counter.
     */
    public WorkUsersSessionsStates processTemporaryStop(WorkUsersSessionsStates session, LocalDateTime stopTime) {

        try {
            // Set temporary stop start time for current calculation
            session.setLastTemporaryStopTime(stopTime);

            // Increment temporary stop counter
            int currentCount = session.getTemporaryStopCount() != null ? session.getTemporaryStopCount() : 0;
            session.setTemporaryStopCount(currentCount + 1);

            // Update session status to temporary stop
            SessionEntityBuilder.updateSession(session, builder -> builder.status(WorkCode.WORK_TEMPORARY_STOP).currentStartTime(stopTime));

            LoggerUtil.info(this.getClass(), String.format("Temporary stop started for %s at %s (count: %d)", session.getUsername(), stopTime, currentCount + 1));

            return session;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing temporary stop for " + session.getUsername(), e);
            return session;
        }
    }

    /**
     * Process resuming from temporary stop (replaces ProcessResumeFromTempStopCommand).
     * Calculates stop duration and updates totals.
     */
    public WorkUsersSessionsStates processResumeFromTempStop(WorkUsersSessionsStates session, LocalDateTime resumeTime) {
        try {
            if (session.getLastTemporaryStopTime() != null) {
                // Calculate duration of the temporary stop
                long stopMinutes = ChronoUnit.MINUTES.between(session.getLastTemporaryStopTime(), resumeTime);

                // Calculate total from completed temporary stops only (don't use session.getTotalTemporaryStopMinutes as it may include live calculation)
                int currentTotal = 0;
                if (session.getTemporaryStops() != null) {
                    currentTotal = session.getTemporaryStops().stream()
                            .mapToInt(TemporaryStop::getDuration)
                            .sum();
                }
                int newTotal = currentTotal + (int) stopMinutes;

                // Create and add TemporaryStop record
                TemporaryStop stop = new TemporaryStop();
                stop.setStartTime(session.getLastTemporaryStopTime());
                stop.setEndTime(resumeTime);
                stop.setDuration((int) stopMinutes);

                // ✅ FIX: Include totalTemporaryStopMinutes in the builder call
                SessionEntityBuilder.updateSession(session, builder -> builder
                        .addTemporaryStop(stop)
                        .totalTemporaryStopMinutes(newTotal));  // <-- ADD THIS LINE

                // Clear temporary stop tracking
                session.setLastTemporaryStopTime(null);

                LoggerUtil.info(this.getClass(), String.format(
                        "Resumed from temporary stop for %s: duration=%d minutes (total: %d)",
                        session.getUsername(), stopMinutes, newTotal));
            }

            // ✅ FIX: Preserve totalTemporaryStopMinutes in status update
            int preservedTotal = session.getTotalTemporaryStopMinutes() != null ? session.getTotalTemporaryStopMinutes() : 0;

            // Update session status to online
            SessionEntityBuilder.updateSession(session, builder -> builder
                    .status(WorkCode.WORK_ONLINE)
                    .currentStartTime(resumeTime)
                    .totalTemporaryStopMinutes(preservedTotal));  // <-- ADD THIS LINE

            return session;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error processing resume from temporary stop for " + session.getUsername(), e);
            return session;
        }
    }

    /**
     * Add a break as temporary stop (replaces AddBreakAsTempStopCommand).
     * PRESERVED: Matches original command logic exactly - creates TemporaryStop record and updates totals.
     */
    public WorkUsersSessionsStates addBreakAsTempStop(WorkUsersSessionsStates session, LocalDateTime startTime, LocalDateTime endTime) {

        try {
            // Validate inputs (same as original command)
            if (session == null || startTime == null || endTime == null) {
                LoggerUtil.error(this.getClass(), "Invalid parameters for addBreakAsTempStop");
                return session;
            }

            if (endTime.isBefore(startTime)) {
                LoggerUtil.error(this.getClass(), "End time cannot be before start time");
                return session;
            }

            // PRESERVED: Create the temporary stop (same as original command)
            TemporaryStop breakStop = new TemporaryStop();
            breakStop.setStartTime(startTime);
            breakStop.setEndTime(endTime);
            breakStop.setDuration(CalculateWorkHoursUtil.calculateMinutesBetween(startTime, endTime));

            // PRESERVED: Calculate new stop count (same as original command)
            int newStopCount = session.getTemporaryStopCount() != null ? session.getTemporaryStopCount() + 1 : 1;

            // PRESERVED: Update session using builder (same as original command)
            SessionEntityBuilder.updateSession(session, builder -> builder
                    .addTemporaryStop(breakStop)
                    .temporaryStopCount(newStopCount));

            // PRESERVED: Calculate new total temporary stop minutes from completed stops only
            final int totalStopMinutes = session.getTemporaryStops() != null ? 
                    session.getTemporaryStops().stream()
                            .mapToInt(TemporaryStop::getDuration)
                            .sum() : 0;
            session.setTotalTemporaryStopMinutes(totalStopMinutes);

            LoggerUtil.info(this.getClass(), String.format(
                    "Added break as temp stop for %s: %d minutes (%s to %s), total stops: %d, total minutes: %d",
                    session.getUsername(), breakStop.getDuration(), startTime, endTime,
                    newStopCount, totalStopMinutes));

            return session;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error adding break as temp stop for " + (session != null ? session.getUsername() : "null session"), e);
            return session;
        }
    }

    // ========================================================================
    // DIRECT CALCULATION METHODS (Replace Query Pattern)
    // ========================================================================

    /**
     * Calculate raw work minutes for a session (replaces CalculateRawWorkMinutesQuery).
     * Direct access to CalculateWorkHoursUtil method.
     */
    public int calculateRawWorkMinutes(WorkUsersSessionsStates session, LocalDateTime endTime) {
        try {
            return CalculateWorkHoursUtil.calculateRawWorkMinutes(session, endTime);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error calculating raw work minutes for %s: %s",
                    session != null ? session.getUsername() : "null", e.getMessage()));
            return 0;
        }
    }

    /**
     * Calculate raw work minutes for a worktime entry (replaces CalculateRawWorkMinutesForEntryQuery).
     * Direct access to CalculateWorkHoursUtil method.
     */
    public int calculateRawWorkMinutesForEntry(WorkTimeTable entry, LocalDateTime endTime) {
        try {
            return CalculateWorkHoursUtil.calculateRawWorkMinutes(entry, endTime);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating raw work minutes for entry: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Calculate minutes between two times (replaces CalculateMinutesBetweenQuery).
     * Direct access to CalculateWorkHoursUtil method.
     */
    public int calculateMinutesBetween(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            return CalculateWorkHoursUtil.calculateMinutesBetween(startTime, endTime);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating minutes between times: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Calculate work time processing (replaces CalculateWorkTimeQuery).
     * Direct access to CalculateWorkHoursUtil method.
     */
    public WorkTimeCalculationResultDTO calculateWorkTime(int minutes, int schedule) {
        try {
            return CalculateWorkHoursUtil.calculateWorkTime(minutes, schedule);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating work time: " + e.getMessage());
            // Return error-safe DTO
            return new WorkTimeCalculationResultDTO(minutes, 0, 0, false, 0);
        }
    }

    /**
     * Calculate recommended end time for a worktime entry (replaces CalculateRecommendedEndTimeQuery).
     * Direct access to CalculateWorkHoursUtil method.
     */
    public LocalDateTime calculateRecommendedEndTime(WorkTimeTable entry, int userSchedule) {
        try {
            return CalculateWorkHoursUtil.calculateRecommendedEndTime(entry, userSchedule);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating recommended end time: " + e.getMessage());
            return LocalDateTime.now();
        }
    }

    /**
     * Calculate total temporary stop minutes including ongoing stops (replaces CalculateTotalTempStopMinutesQuery).
     */
    public int calculateTotalTempStopMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        try {
            int total = session.getTotalTemporaryStopMinutes() != null ? session.getTotalTemporaryStopMinutes() : 0;

            // Add current temporary stop if active AND only if we haven't already counted it
            if (session.getLastTemporaryStopTime() != null && 
                WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                
                long currentStopMinutes = ChronoUnit.MINUTES.between(session.getLastTemporaryStopTime(), currentTime);
                
                // If total is 0, this is a live calculation - add the current stop
                // If total > 0, the stop might already be included from updateTempStopCalculations
                if (total == 0) {
                    total += (int) currentStopMinutes;
                } else {
                    // Check if the stored total already includes the current stop by comparing timestamps
                    // This prevents double counting when updateTempStopCalculations has already run
                    return total; // Return the already calculated total
                }
            }

            return total;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating total temp stop minutes: " + e.getMessage());
            return session.getTotalTemporaryStopMinutes() != null ? session.getTotalTemporaryStopMinutes() : 0;
        }
    }

    // ========================================================================
    // UTILITY AND FORMATTING METHODS
    // ========================================================================

    /**
     * Format minutes as HH:MM display.
     * Direct access to CalculateWorkHoursUtil method.
     */
    public String minutesToHHmm(Integer minutes) {
        return CalculateWorkHoursUtil.minutesToHHmm(minutes);
    }

    /**
     * Format minutes as hours only.
     * Direct access to CalculateWorkHoursUtil method.
     */
    public String minutesToHH(Integer minutes) {
        return CalculateWorkHoursUtil.minutesToHH(minutes);
    }

    /**
     * Calculate discarded minutes (minutes that don't count toward regular or overtime).
     * This includes partial hours that get rounded down.
     */
    public int calculateDiscardedMinutes(int totalMinutes, int schedule) {
        try {
            WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(totalMinutes, schedule);

            // Discarded = adjusted minutes - (processed + overtime)
            int adjustedMinutes = result.isLunchDeducted() ? totalMinutes - 30 : totalMinutes;
            int countedMinutes = result.getProcessedMinutes() + result.getOvertimeMinutes();

            return Math.max(0, adjustedMinutes - countedMinutes);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating discarded minutes: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Check if lunch break should be deducted based on work time and schedule.
     * Direct access to CalculateWorkHoursUtil method.
     */
    public boolean isLunchBreakDeducted(int minutes, int schedule) {
        return CalculateWorkHoursUtil.isLunchBreakDeducted(minutes, schedule);
    }

    /**
     * Calculate work days in a month (excluding weekends).
     * Direct access to CalculateWorkHoursUtil method.
     */
    public int calculateWorkDays(Integer year, Integer month) {
        return CalculateWorkHoursUtil.calculateWorkDays(year, month);
    }

    /**
     * Format datetime for display.
     * Direct access to CalculateWorkHoursUtil method.
     */
    public String formatDateTime(LocalDateTime dateTime) {
        return CalculateWorkHoursUtil.formatDateTime(dateTime);
    }

    // ========================================================================
    // BUSINESS LOGIC VALIDATION METHODS
    // ========================================================================

    /**
     * Validate if a session can be processed for calculations.
     */
    public boolean isValidSessionForCalculation(WorkUsersSessionsStates session) {
        return session != null &&
                session.getDayStartTime() != null &&
                session.getUserId() != null &&
                session.getUsername() != null &&
                !session.getUsername().trim().isEmpty();
    }

    /**
     * Validate if a worktime entry can be processed for calculations.
     */
    public boolean isValidEntryForCalculation(WorkTimeTable entry) {
        return entry != null &&
                entry.getDayStartTime() != null &&
                entry.getUserId() != null;
    }

    /**
     * Check if session has reached maximum allowed work hours.
     */
    public boolean hasExceededMaxWorkHours(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        if (!isValidSessionForCalculation(session)) {
            return false;
        }

        try {
            int totalMinutes = calculateRawWorkMinutes(session, currentTime);
            int maxMinutes = WorkCode.MAX_TEMP_STOP_HOURS * 60; // Convert max hours to minutes

            return totalMinutes > maxMinutes;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error checking max work hours for " + session.getUsername(), e);
            return false;
        }
    }

    // ========================================================================
    // SCHEDULE AND DATE CALCULATION METHODS
    // ========================================================================

    /**
     * Check if a date is a weekend (Saturday or Sunday).
     * Moved from CalculateWorkHoursUtil for better service layer architecture.
     */
    public boolean isWeekend(LocalDate date) {
        if (date == null) {
            LoggerUtil.warn(this.getClass(), "Date is null in isWeekend check");
            return false;
        }

        try {
            DayOfWeek day = date.getDayOfWeek();
            boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;

            LoggerUtil.debug(this.getClass(), String.format("Date %s is %s", date, weekend ? "weekend" : "weekday"));
            return weekend;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking weekend status for date: " + date, e);
            return false;
        }
    }

    /**
     * Normalizes schedule hours (defaults to 8 if invalid).
     * Moved from WorkScheduleQuery for centralized schedule validation.
     */
    public int normalizeSchedule(Integer schedule) {
        if (schedule == null || schedule <= 0) {
            LoggerUtil.warn(this.getClass(), String.format("Invalid schedule %s, defaulting to %d hours", schedule, WorkCode.INTERVAL_HOURS_C));
            return WorkCode.INTERVAL_HOURS_C; // Default to 8 hours
        }

        LoggerUtil.debug(this.getClass(), String.format("Normalized schedule: %d hours", schedule));
        return schedule;
    }

    /**
     * Calculates the full day duration in minutes, accounting for lunch break.
     * Moved from WorkScheduleQuery for centralized schedule calculations.
     */
    public int calculateFullDayDuration(int schedule) {
        try {
            int duration;

            // For 8-hour schedule: 8.5 hours (510 minutes) including lunch
            // For others: schedule hours without lunch break
            if (schedule == WorkCode.INTERVAL_HOURS_C) {
                duration = (schedule * WorkCode.HOUR_DURATION) + WorkCode.HALF_HOUR_DURATION;
            } else {
                duration = schedule * WorkCode.HOUR_DURATION;
            }

            LoggerUtil.debug(this.getClass(), String.format("Full day duration for %d hour schedule: %d minutes", schedule, duration));
            return duration;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error calculating full day duration for schedule %d: %s", schedule, e.getMessage()), e);
            return WorkCode.INTERVAL_HOURS_C * WorkCode.HOUR_DURATION; // Safe fallback
        }
    }

    /**
     * Calculates the expected end time based on schedule and weekend status.
     * Moved from WorkScheduleQuery for centralized time calculations.
     */
    public LocalTime calculateExpectedEndTime(int schedule, boolean isWeekend) {
        try {
            LocalTime endTime;

            // Weekend special case (end at 1 PM)
            if (isWeekend) {
                endTime = LocalTime.of(13, 0);
            }
            // For 8-hour schedule, default end time is 17:00 (5 PM)
            else if (schedule == WorkCode.INTERVAL_HOURS_C) {
                endTime = LocalTime.of(17, 0);
            }
            // For custom schedules, assume start at 9 and add schedule hours
            else {
                int endHour = 9 + schedule;
                // Cap at 23:59 to avoid invalid times
                if (endHour > 23) {
                    endHour = 23;
                    LoggerUtil.warn(this.getClass(), String.format("Schedule %d hours would exceed 23:59, capping end time", schedule));
                }
                endTime = LocalTime.of(endHour, 0);
            }

            LoggerUtil.debug(this.getClass(), String.format("Expected end time for %d hour schedule (weekend: %b): %s", schedule, isWeekend, endTime));
            return endTime;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error calculating expected end time for schedule %d: %s", schedule, e.getMessage()), e);
            return LocalTime.of(17, 0); // Safe fallback to 5 PM
        }
    }

    /**
     * Checks if a schedule includes a lunch break.
     * Moved from WorkScheduleQuery for centralized lunch break logic.
     */
    public boolean includesLunchBreak(int schedule) {
        boolean includes = schedule == WorkCode.INTERVAL_HOURS_C;
        LoggerUtil.debug(this.getClass(), String.format("Schedule %d hours includes lunch break: %b", schedule, includes));
        return includes;
    }
}