package com.ctgraphdep.session.util;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.*;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.session.query.WorkScheduleQuery;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Centralized service for all session-related calculations.
 * This ensures consistent calculation logic across all commands.
 */
public class SessionCalculationService {

    /**
     * Updates calculations for a session with user schedule
     * @param session The session to update
     * @param currentTime The current time reference
     * @param userSchedule The user's schedule in hours
     * @param context The session context
     * @return The updated session
     */
    public WorkUsersSessionsStates updateSessionCalculations(WorkUsersSessionsStates session, LocalDateTime currentTime, int userSchedule, SessionContext context) {
        if (session == null) {
            return null;
        }

        try {
            if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                updateOnlineCalculations(session, currentTime, userSchedule, context);
            } else if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                updateTempStopCalculations(session, currentTime);
            }

            // Always update last activity
            session.setLastActivity(currentTime);

            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating session calculations: " + e.getMessage(), e);
            return session;
        }
    }

    /**
     * Updates calculations for a session in ONLINE status
     * @param session The session to update
     * @param currentTime The current time reference
     * @param userSchedule The user's schedule in hours
     * @param context The session context
     */
    private void updateOnlineCalculations(WorkUsersSessionsStates session, LocalDateTime currentTime, int userSchedule, SessionContext context) {
        // Calculate raw work minutes
        int rawWorkedMinutes = calculateRawWorkMinutes(session, currentTime);

        // Calculate work time using utility
        WorkTimeCalculationResult result = CalculateWorkHoursUtil.calculateWorkTime(rawWorkedMinutes, userSchedule);

        // Update session with calculated values
        SessionEntityBuilder.updateSession(session, builder -> {
            builder.totalWorkedMinutes(rawWorkedMinutes)
                    .finalWorkedMinutes(result.getProcessedMinutes())
                    .totalOvertimeMinutes(result.getOvertimeMinutes())
                    .lunchBreakDeducted(result.isLunchDeducted())
                    .workdayCompleted(isWorkdayCompleted(result, userSchedule, context));
        });
    }

    /**
     * Calculates raw work minutes for a session (minutes worked excluding adjustments)
     * @param session The session to calculate for
     * @param currentTime The current time reference
     * @return The total minutes worked
     */
    public int calculateRawWorkMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        if (session == null || session.getDayStartTime() == null) {
            return 0;
        }

        int totalMinutes = 0;
        LocalDateTime currentStartPoint = session.getDayStartTime();
        // Use the end time passed in

        // Handle completed temporary stops
        if (session.getTemporaryStops() != null) {
            for (TemporaryStop stop : session.getTemporaryStops()) {
                // Only count stops that have ended
                if (stop.getEndTime() != null) {
                    // Add worked minutes before each stop
                    totalMinutes += CalculateWorkHoursUtil.calculateMinutesBetween(currentStartPoint, stop.getStartTime());
                    // Move current time to after the stop
                    currentStartPoint = stop.getEndTime();
                }
            }
        }

        // Add minutes from last stop (or start) until now if in ONLINE status
        totalMinutes += CalculateWorkHoursUtil.calculateMinutesBetween(
                currentStartPoint,
                currentTime
        );

        return totalMinutes;
    }

    /**
     * Updates calculations for a session in TEMPORARY_STOP status
     * @param session The session to update
     * @param currentTime The current time reference
     */
    private void updateTempStopCalculations(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        if (session.getLastTemporaryStopTime() != null) {
            int totalStopMinutes = calculateTotalTempStopMinutes(session, currentTime);
            session.setTotalTemporaryStopMinutes(totalStopMinutes);

            // Update current stop duration if there are any stops
            if (session.getTemporaryStops() != null && !session.getTemporaryStops().isEmpty()) {
                TemporaryStop currentStop = session.getTemporaryStops()
                        .get(session.getTemporaryStops().size() - 1);

                int currentStopMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(
                        currentStop.getStartTime(), currentTime
                );
                currentStop.setDuration(currentStopMinutes);
            }
        }
    }

    /**
     * Calculates total temporary stop minutes for a session
     * @param session The session to calculate for
     * @param currentTime The current time reference
     * @return The total minutes in temporary stop
     */
    public int calculateTotalTempStopMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        if (session == null || session.getTemporaryStops() == null || session.getTemporaryStops().isEmpty()) {
            return 0;
        }

        int totalStopMinutes = 0;

        for (TemporaryStop stop : session.getTemporaryStops()) {
            if (stop.getEndTime() != null) {
                // Add duration of completed stops
                totalStopMinutes += CalculateWorkHoursUtil.calculateMinutesBetween(
                        stop.getStartTime(),
                        stop.getEndTime()
                );
            } else {
                // For current ongoing stop, calculate time up to now
                totalStopMinutes += CalculateWorkHoursUtil.calculateMinutesBetween(
                        stop.getStartTime(),
                        currentTime
                );
            }
        }

        return totalStopMinutes;
    }

    /**
     * Calculates worked minutes between two time points
     * @param startTime The start time
     * @param endTime The end time
     * @return The minutes between start and end times
     */
    public int calculateWorkedMinutesBetween(LocalDateTime startTime, LocalDateTime endTime) {
        return CalculateWorkHoursUtil.calculateMinutesBetween(startTime, endTime);
    }

    /**
     * Determines if a workday is completed based on calculation results and schedule
     * @param result The calculation result
     * @param schedule The user's schedule in hours
     * @return True if the workday is completed, false otherwise
     */

    public boolean isWorkdayCompleted(WorkTimeCalculationResult result, int schedule, SessionContext context) {
        // Get standardized time values
        GetSessionTimeValuesQuery timeQuery = context.getCommandFactory().getSessionTimeValuesQuery();
        GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

        // Use the current date from timeValues
        WorkScheduleQuery query = context.getCommandFactory().createWorkScheduleQuery(timeValues.getCurrentDate(), schedule);
        WorkScheduleQuery.ScheduleInfo scheduleInfo = context.executeQuery(query);

        return result.getRawMinutes() >= scheduleInfo.getFullDayDuration();
    }

    /**
     * Processes final calculations for ending a session
     * @param session The session to end
     * @param endTime The end time
     * @param finalMinutes The final minutes worked
     * @return The updated session
     */
    public WorkUsersSessionsStates calculateEndDayValues(WorkUsersSessionsStates session, LocalDateTime endTime, Integer finalMinutes) {
        // Use builder to update all values
        return SessionEntityBuilder.updateSession(session, builder -> {
            builder.status(WorkCode.WORK_OFFLINE)
                    .dayEndTime(endTime)
                    .finalWorkedMinutes(finalMinutes != null ? finalMinutes : session.getFinalWorkedMinutes())
                    .workdayCompleted(true);
        });
    }

    /**
     * Updates the last temporary stop with end time and duration
     * @param session The session to update
     * @param endTime The end time of the temporary stop
     */
    public void updateLastTemporaryStop(WorkUsersSessionsStates session, LocalDateTime endTime) {
        if (session.getTemporaryStops() == null || session.getTemporaryStops().isEmpty()) {
            return;
        }

        TemporaryStop lastStop = session.getTemporaryStops().get(session.getTemporaryStops().size() - 1);
        int stopMinutes = calculateWorkedMinutesBetween(lastStop.getStartTime(), endTime);
        lastStop.setEndTime(endTime);
        lastStop.setDuration(stopMinutes);
    }

    /**
     * Creates and adds a temporary stop for a break period
     * @param session The session to update
     * @param startTime The start time of the break
     * @param endTime The end time of the break
     */
    public void addBreakAsTempStop(WorkUsersSessionsStates session, LocalDateTime startTime, LocalDateTime endTime) {
        // Create the temporary stop
        TemporaryStop breakStop = new TemporaryStop();
        breakStop.setStartTime(startTime);
        breakStop.setEndTime(endTime);
        breakStop.setDuration(calculateWorkedMinutesBetween(startTime, endTime));

        // Calculate new stop count
        int newStopCount = session.getTemporaryStopCount() != null ? session.getTemporaryStopCount() + 1 : 1;

        // Update session
        SessionEntityBuilder.updateSession(session, builder -> {
            builder.addTemporaryStop(breakStop)
                    .temporaryStopCount(newStopCount)
                    .totalTemporaryStopMinutes(calculateTotalTempStopMinutes(session, endTime));
        });
    }

    /**
     * Process resuming from a temporary stop
     * @param session The session to update
     * @param resumeTime The time of resuming
     */
    public void processResumeFromTempStop(WorkUsersSessionsStates session, LocalDateTime resumeTime) {
        // Update the last temporary stop
        updateLastTemporaryStop(session, resumeTime);

        // Calculate total temporary stop minutes
        int totalStopMinutes = calculateTotalTempStopMinutes(session, resumeTime);

        // Update session
        SessionEntityBuilder.updateSession(session, builder -> {
            builder.status(WorkCode.WORK_ONLINE)
                    .currentStartTime(resumeTime)
                    .totalTemporaryStopMinutes(totalStopMinutes)
                    .finalWorkedMinutes(session.getTotalWorkedMinutes() != null ?
                            session.getTotalWorkedMinutes() : 0);
        });
    }

    /**
     * Process temporary stop for a session
     * @param session The session to update
     * @param stopTime The time of stopping
     */
    public void processTemporaryStop(WorkUsersSessionsStates session, LocalDateTime stopTime) {
        // Calculate work minutes from last start to now
        int workedMinutes = calculateWorkedMinutesBetween(session.getCurrentStartTime(), stopTime);

        // Create new temporary stop entry
        TemporaryStop stop = new TemporaryStop();
        stop.setStartTime(stopTime);

        // Update session for temporary stop using the builder
        SessionEntityBuilder.updateSession(session, builder -> {
            builder.status(WorkCode.WORK_TEMPORARY_STOP)
                    .totalWorkedMinutes((session.getTotalWorkedMinutes() != null ?
                            session.getTotalWorkedMinutes() : 0) + workedMinutes)
                    .lastTemporaryStopTime(stopTime)
                    .temporaryStopCount(session.getTemporaryStopCount() + 1)
                    .addTemporaryStop(stop);
        });
    }
}