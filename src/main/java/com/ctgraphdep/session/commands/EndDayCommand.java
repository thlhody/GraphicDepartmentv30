package com.ctgraphdep.session.commands;

import com.ctgraphdep.calculations.commands.UpdateLastTemporaryStopCommand;
import com.ctgraphdep.calculations.queries.CalculateRawWorkMinutesQuery;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.WorkScheduleQuery;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusMerge;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * REFACTORED EndDayCommand using new SessionContext adapter methods
 * and existing SessionEntityBuilder approach
 */
public class EndDayCommand extends BaseSessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;
    private final Integer finalMinutes;
    private final LocalDateTime explicitEndTime;
    private static final long END_COMMAND_COOLDOWN_MS = 2000; // 2 seconds

    public EndDayCommand(String username, Integer userId, Integer finalMinutes, LocalDateTime endTime) {
        validateUsername(username);
        validateUserId(userId);

        if (finalMinutes != null) {
            validateCondition(finalMinutes >= 0, "Final minutes cannot be negative");
        }

        this.username = username;
        this.userId = userId;
        this.finalMinutes = finalMinutes;
        this.explicitEndTime = endTime;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithDeduplication(context, username, this::executeEndDayLogic, null, END_COMMAND_COOLDOWN_MS);
    }

    public WorkUsersSessionsStates executeEndDayLogic(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Executing EndDayCommand for user %s with %d minutes", username, finalMinutes != null ? finalMinutes : 0));

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);

            // Get and validate session
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);

            if (session == null) {
                warn("Session is null, cannot end");
                return null;
            }

            // Use explicit end time if provided, otherwise use standardized current time
            LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : timeValues.getCurrentTime();
            info(String.format("Using end time: %s for user %s", endTime, username));

            // Handle temporary stop case if needed
            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                info(String.format("Handling temporary stop before ending session for user %s", username));
                try {
                    UpdateLastTemporaryStopCommand tempStopCommand = ctx.getCalculationFactory().createUpdateLastTemporaryStopCommand(session, endTime);
                    session = ctx.getCalculationService().executeCommand(tempStopCommand);

                    SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
                    ctx.executeCommand(saveCommand);

                    info(String.format("Successfully updated temporary stop info for user %s", username));
                } catch (Exception e) {
                    warn(String.format("Error updating temporary stop: %s. Continuing with session end.", e.getMessage()));
                }
            }

            // Calculate effective final minutes if needed
            Integer effectiveFinalMinutes = finalMinutes;
            if (effectiveFinalMinutes == null && (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())
                    || WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()))) {
                try {
                    CalculateRawWorkMinutesQuery workMinutesQuery = ctx.getCalculationFactory().createCalculateRawWorkMinutesQuery(session, endTime);
                    effectiveFinalMinutes = ctx.getCalculationService().executeQuery(workMinutesQuery);
                    info(String.format("Calculated final minutes for user %s: %d", username, effectiveFinalMinutes));
                } catch (Exception e) {
                    warn(String.format("Error calculating raw work minutes: %s. Using session value.", e.getMessage()));
                    effectiveFinalMinutes = session.getTotalWorkedMinutes();
                }
            }

            // Process end session operation using calculation command
            session = processEndSession(session, endTime, ctx, effectiveFinalMinutes);

            // Save the updated session
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            // Update session status
            ctx.getSessionStatusService().updateSessionStatus(username, userId, WorkCode.WORK_OFFLINE, endTime);

            // REFACTORED: Update worktime entry using new approach
            updateWorktimeEntry(session, ctx, endTime);

            // Clean up monitoring
            ctx.getSessionMonitorService().stopMonitoring(username);
            ctx.getSessionMonitorService().deactivateHourlyMonitoring(username);
            try {
                ctx.getSessionMonitorService().clearMonitoring(username);
            } catch (Exception e) {
                warn(String.format("Error clearing monitoring: %s", e.getMessage()));
            }

            info(String.format("Successfully ended session for user %s with %d minutes", username, effectiveFinalMinutes != null ? effectiveFinalMinutes : 0));

            return session;
        });
    }

    private WorkUsersSessionsStates processEndSession(WorkUsersSessionsStates session, LocalDateTime endTime, SessionContext context, Integer effectiveFinalMinutes) {
        return context.calculateEndDayValues(session, endTime, effectiveFinalMinutes);
    }

    /**
     * REFACTORED: Updates worktime entry using new SessionContext adapter methods
     * and existing SessionEntityBuilder approach
     */
    private void updateWorktimeEntry(WorkUsersSessionsStates session, SessionContext context, LocalDateTime endTime) {
        try {
            if (session.getDayStartTime() == null) {
                warn("Cannot update worktime entry: session has no start time");
                return;
            }

            LocalDate workDate = session.getDayStartTime().toLocalDate();
            debug(String.format("Updating worktime entry for date: %s", workDate));

            // REFACTORED: Find existing entry using new adapter method
            WorkTimeTable entry = context.findSessionEntry(username, userId, workDate);

            if (entry == null) {
                // REFACTORED: Create new entry using existing SessionEntityBuilder
                entry = context.createWorktimeEntryFromSession(session);
                info("Created new worktime entry from session");
            } else {
                // REFACTORED: Update existing entry using new adapter method
                entry = context.updateEntryFromSession(entry, session);
                info("Updated existing worktime entry from session");
            }

            // Set final end time and status
            entry.setDayEndTime(endTime);
            entry.setAdminSync(SyncStatusMerge.USER_INPUT); // Final state, not in-process

            // Calculate overtime using schedule info if needed
            try {
                Integer userSchedule = context.getUserService().getUserById(session.getUserId())
                        .map(User::getSchedule)
                        .orElse(WorkCode.INTERVAL_HOURS_C);

                WorkScheduleQuery query = context.getCommandFactory().createWorkScheduleQuery(workDate, userSchedule);
                WorkScheduleQuery.ScheduleInfo scheduleInfo = context.executeQuery(query);

                if (scheduleInfo != null && session.getTotalWorkedMinutes() != null) {
                    int overtimeMinutes = scheduleInfo.calculateOvertimeMinutes(session.getTotalWorkedMinutes());
                    if (overtimeMinutes > 0 && (entry.getTotalOvertimeMinutes() == null || entry.getTotalOvertimeMinutes() == 0)) {
                        entry.setTotalOvertimeMinutes(overtimeMinutes);
                        info(String.format("Updated overtime minutes for user %s: %d minutes", username, overtimeMinutes));
                    }
                }
            } catch (Exception e) {
                warn(String.format("Error calculating overtime: %s", e.getMessage()));
            }

            // REFACTORED: Save worktime entry using new adapter method
            context.saveSessionWorktime(username, entry, workDate.getYear(), workDate.getMonthValue());

            info(String.format("Updated worktime entry for user %s - Total minutes: %d, Overtime: %d",
                    username, entry.getTotalWorkedMinutes(), entry.getTotalOvertimeMinutes()));

        } catch (Exception e) {
            error(String.format("Failed to update worktime entry for user %s: %s", username, e.getMessage()), e);
        }
    }
}