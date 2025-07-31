package com.ctgraphdep.session.commands;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.WorkScheduleQuery;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDate;
import java.time.LocalDateTime;
// ============================================================================
// 2. REFACTORED ResumeFromTemporaryStopCommand
// ============================================================================

/**
 * REFACTORED ResumeFromTemporaryStopCommand using BaseWorktimeUpdateSessionCommand
 * Eliminates duplication while preserving all resume specific logic
 */
public class ResumeFromTemporaryStopCommand extends BaseWorktimeUpdateSessionCommand<WorkUsersSessionsStates> {

    private static final long RESUME_COOLDOWN_MS = 1500; // 1.5 seconds

    public ResumeFromTemporaryStopCommand(String username, Integer userId) {
        super(username, userId);
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithDeduplication(context, username, this::executeResumeLogic, null, RESUME_COOLDOWN_MS);
    }

    public WorkUsersSessionsStates executeResumeLogic(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Resuming work for user %s after temporary stop", username));

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);
            LocalDateTime resumeTime = timeValues.getCurrentTime();
            debug(String.format("Resume time: %s", resumeTime));

            // Get the current session
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);

            // Validate session is in temporary stop state
            if (!SessionValidator.isInTemporaryStopState(session, this.getClass())) {
                warn("Session is not in temporary stop state, cannot resume");
                return session;
            }

            // Process the resume operation using the calculation command
            debug("Processing resume from temporary stop");
            ctx.processResumeFromTempStop(session, resumeTime);

            // Save the updated session
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            // ENHANCED: Update worktime entry with special day detection using abstract base class
            updateWorktimeEntryWithSpecialDayLogic(session, ctx);

            // Manage monitoring state based on schedule completion
            manageMonitoringState(session, resumeTime, ctx);

            info(String.format("Resumed work for user %s after temporary stop", username));
            return session;
        });
    }

    // ========================================================================
    // ABSTRACT METHOD IMPLEMENTATIONS - ResumeFromTemporaryStopCommand specific logic
    // ========================================================================

    @Override
    protected WorkTimeTable findOrCreateEntry(LocalDate workDate, WorkUsersSessionsStates session, SessionContext context) {
        // Resume commands should only update existing entries
        return findExistingEntry(workDate, context);
    }

    @Override
    protected void applyCommandSpecificCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("resume from temporary stop");

        // Apply resume-specific customizations (critical for resuming temporary stops)
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes()); // Critical for resume
        entry.setAdminSync(MergingStatusConstants.USER_IN_PROCESS); // Still in process
    }

    @Override
    protected void applyPostSpecialDayCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("post-special-day resume from temporary stop");

        // Re-apply resume customizations that might have been modified by special day logic
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes()); // Critical
        entry.setAdminSync(MergingStatusConstants.USER_IN_PROCESS);
    }

    @Override
    protected String getCommandDescription() {
        return "resume from temporary stop";
    }

    // ========================================================================
    // PRESERVED ORIGINAL HELPER METHODS
    // ========================================================================

    private void manageMonitoringState(WorkUsersSessionsStates session, LocalDateTime resumeTime, SessionContext context) {
        try {
            if (isScheduleCompleted(session, context)) {
                debug(String.format("Schedule is completed for user %s, activating hourly monitoring", username));

                boolean result = context.getSessionMonitorService().activateExplicitHourlyMonitoring(username, resumeTime);

                if (result) {
                    info(String.format("Successfully activated hourly monitoring for user %s after resuming from temp stop", username));
                } else {
                    warn(String.format("Failed to activate hourly monitoring for user %s after resuming from temp stop", username));
                }
            } else {
                debug(String.format("Schedule is not completed for user %s, resuming schedule monitoring", username));
                context.getSessionMonitorService().resumeScheduleMonitoring(username);
                info(String.format("Resumed schedule monitoring for user %s after temp stop", username));
            }
        } catch (Exception e) {
            error(String.format("Error managing monitoring state for user %s: %s", username, e.getMessage()), e);
        }
    }

    private boolean isScheduleCompleted(WorkUsersSessionsStates session, SessionContext context) {
        try {
            LocalDate workDate = session.getDayStartTime().toLocalDate();
            Integer userSchedule = context.getUserService().getUserById(session.getUserId())
                    .map(User::getSchedule).orElse(8);

            WorkScheduleQuery query = context.getCommandFactory().createWorkScheduleQuery(workDate, userSchedule);
            WorkScheduleQuery.ScheduleInfo scheduleInfo = context.executeQuery(query);

            int workedMinutes = session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0;
            return scheduleInfo.isScheduleCompleted(workedMinutes);

        } catch (Exception e) {
            error(String.format("Error checking schedule completion: %s", e.getMessage()), e);
            return false;
        }
    }
}