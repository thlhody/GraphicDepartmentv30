package com.ctgraphdep.session.commands;

import com.ctgraphdep.calculations.commands.UpdateLastTemporaryStopCommand;
import com.ctgraphdep.calculations.queries.CalculateRawWorkMinutesQuery;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.model.DayType;
import com.ctgraphdep.session.query.WorkScheduleQuery;
import com.ctgraphdep.session.util.SessionSpecialDayDetector;
import com.ctgraphdep.config.WorkCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class EndDayCommand extends BaseWorktimeUpdateSessionCommand<WorkUsersSessionsStates> {

    private final Integer finalMinutes;
    private final LocalDateTime explicitEndTime;
    private static final long END_COMMAND_COOLDOWN_MS = 2000; // 2 seconds

    public EndDayCommand(String username, Integer userId, Integer finalMinutes, LocalDateTime endTime) {
        super(username, userId);

        if (finalMinutes != null) {
            validateCondition(finalMinutes >= 0, "Final minutes cannot be negative");
        }

        this.finalMinutes = finalMinutes;
        this.explicitEndTime = endTime;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithDeduplication(context, username, this::executeEndDayLogic, null, END_COMMAND_COOLDOWN_MS);
    }

    public WorkUsersSessionsStates executeEndDayLogic(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Executing EndDayCommand with special day detection for user %s with %d minutes", username, finalMinutes != null ? finalMinutes : 0));

            LocalDateTime today = getStandardCurrentTime(ctx);

            // Get and validate session
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);

            if (session == null) {
                warn("Session is null, cannot end");
                return null;
            }

            // Use explicit end time if provided, otherwise use standardized current time
            LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : today;

            // If user is in temporary stop, close the last temp stop
            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                debug("Session is in temporary stop state, updating last temporary stop");
                UpdateLastTemporaryStopCommand updateTempStopCommand = new UpdateLastTemporaryStopCommand(session, endTime);
                session = ctx.getCalculationService().executeCommand(updateTempStopCommand);
            }

            // Calculate final worked minutes
            Integer effectiveFinalMinutes = calculateEffectiveFinalMinutes(session, endTime, ctx);

            // Process end session operation using calculation command
            session = processEndSession(session, endTime, ctx, effectiveFinalMinutes);

            // Save the updated session
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            // Update session status
            ctx.getSessionStatusService().updateSessionStatus(username, userId, WorkCode.WORK_OFFLINE, endTime);

            // ENHANCED: Update worktime entry with special day detection using abstract base class
            updateWorktimeEntryWithSpecialDayLogic(session, ctx);

            // Clean up monitoring
            cleanupMonitoring(ctx);

            info(String.format("Successfully ended session for user %s with %d minutes",
                    username, effectiveFinalMinutes != null ? effectiveFinalMinutes : 0));

            return session;
        });
    }

    // ========================================================================
    // ABSTRACT METHOD IMPLEMENTATIONS - EndDayCommand specific logic
    // ========================================================================

    @Override
    protected WorkTimeTable findOrCreateEntry(LocalDate workDate, WorkUsersSessionsStates session, SessionContext context) {
        // EndDayCommand should find existing entry or create new one if needed
        return findOrCreateNewEntry(workDate, session, context);
    }

    @Override
    protected void applyCommandSpecificCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("end day");

        // Set final end time - this is critical for end day
        LocalDateTime endTime = explicitEndTime;
        if (endTime == null) {
            // Get standardized current time using correct pattern

            endTime = getStandardCurrentTime(context);
        }

        entry.setDayEndTime(endTime);

        // Mark as completed (this may be modified by special day logic, but we'll re-apply it later)
        entry.setAdminSync(MergingStatusConstants.USER_INPUT); // Final state, not in-process

        debug(String.format("Set end time to %s and marked as completed", endTime));
    }

    @Override
    protected void applyPostSpecialDayCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("post-special-day end day");

        // Re-apply final sync status (may have been modified by special day logic)
        entry.setAdminSync(MergingStatusConstants.USER_INPUT); // Final state, not in-process

        // For regular days, apply normal overtime calculation if special day logic wasn't applied
        LocalDate workDate = session.getDayStartTime().toLocalDate();
        DayType dayType = SessionSpecialDayDetector.detectDayType(workDate, username, userId, context);

        if (!dayType.requiresSpecialOvertimeLogic()) {
            debug("Applying regular day overtime calculation");
            applyRegularDayOvertimeLogic(entry, session, context, workDate);
        }

        debug("Ensured final end day state is properly set");
    }

    @Override
    protected String getCommandDescription() {
        return "end day";
    }

    // ========================================================================
    // PRESERVED ORIGINAL HELPER METHODS
    // ========================================================================

    private Integer calculateEffectiveFinalMinutes(WorkUsersSessionsStates session, LocalDateTime endTime, SessionContext context) {
        Integer effectiveFinalMinutes = finalMinutes;
        if (effectiveFinalMinutes == null) {
            try {
                CalculateRawWorkMinutesQuery calculateQuery = context.getCalculationFactory().createCalculateRawWorkMinutesQuery(session, endTime);
                effectiveFinalMinutes = context.getCalculationService().executeQuery(calculateQuery);
                debug(String.format("Calculated final minutes: %d", effectiveFinalMinutes));
            } catch (Exception e) {
                warn(String.format("Error calculating final minutes, using session value: %s", e.getMessage()));
                effectiveFinalMinutes = session.getTotalWorkedMinutes();
            }
        }
        return effectiveFinalMinutes;
    }

    private WorkUsersSessionsStates processEndSession(WorkUsersSessionsStates session, LocalDateTime endTime, SessionContext context, Integer effectiveFinalMinutes) {
        return context.calculateEndDayValues(session, endTime, effectiveFinalMinutes);
    }

    // Apply regular day overtime logic using schedule information. This is only called for non-special days
    private void applyRegularDayOvertimeLogic(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context, LocalDate workDate) {

        try {
            Integer userSchedule = context.getUserService().getUserById(session.getUserId())
                    .map(User::getSchedule).orElse(WorkCode.INTERVAL_HOURS_C);

            WorkScheduleQuery query = context.getCommandFactory().createWorkScheduleQuery(workDate, userSchedule);
            WorkScheduleQuery.ScheduleInfo scheduleInfo = context.executeQuery(query);

            if (scheduleInfo != null && session.getTotalWorkedMinutes() != null) {
                int overtimeMinutes = scheduleInfo.calculateOvertimeMinutes(session.getTotalWorkedMinutes());
                if (overtimeMinutes > 0 && (entry.getTotalOvertimeMinutes() == null || entry.getTotalOvertimeMinutes() == 0)) {
                    entry.setTotalOvertimeMinutes(overtimeMinutes);
                    debug(String.format("Applied regular day overtime: %d minutes", overtimeMinutes));
                }
            }
        } catch (Exception e) {
            warn(String.format("Error calculating regular day overtime: %s", e.getMessage()));
        }
    }

    private void cleanupMonitoring(SessionContext context) {
        context.getSessionMonitorService().stopMonitoring(username);
        context.getSessionMonitorService().deactivateHourlyMonitoring(username);
        try {
            context.getSessionMonitorService().clearMonitoring(username);
        } catch (Exception e) {
            warn(String.format("Error clearing monitoring: %s", e.getMessage()));
        }
    }
}