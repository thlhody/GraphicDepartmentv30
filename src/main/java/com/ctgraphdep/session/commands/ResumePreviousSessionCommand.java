package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.util.SessionEntityBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ResumePreviousSessionCommand extends BaseWorktimeUpdateSessionCommand<WorkUsersSessionsStates> {

    public ResumePreviousSessionCommand(String username, Integer userId) {
        super(username, userId);
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Executing ResumePreviousSessionCommand for user %s", username));

            LocalDateTime resumeTime = getStandardCurrentTime(context);
            debug(String.format("Resume time: %s", resumeTime));

            // Get the current session
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);

            // Validate that the session needs resuming
            if (session == null || !session.getWorkdayCompleted()) {
                warn("Session does not need resuming or is not completed");
                return session;
            }

            // Process the resume session logic
            processResumeSession(session, resumeTime, ctx);

            // Save the updated session
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            // ENHANCED: Update worktime entry with special day detection using abstract base class
            updateWorktimeEntryWithSpecialDayLogic(session, ctx);

            // Start monitoring
            ctx.getSessionMonitorService().startEnhancedMonitoring(username);

            info(String.format("Resumed previous session for user %s", username));
            return session;
        });
    }

    // ========================================================================
    // ABSTRACT METHOD IMPLEMENTATIONS
    // ========================================================================

    @Override
    protected WorkTimeTable findOrCreateEntry(LocalDate workDate, WorkUsersSessionsStates session, SessionContext context) {
        // Resume previous session should only update existing entries
        return findExistingEntry(workDate, context);
    }

    @Override
    protected void applyCommandSpecificCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("resume previous session");

        // Resume previous session specific customizations
        entry.setDayEndTime(null); // Reset end time since we're resuming
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
        entry.setAdminSync(MergingStatusConstants.USER_IN_PROCESS); // Mark as in-process
    }

    @Override
    protected void applyPostSpecialDayCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("post-special-day resume previous session");

        // Re-apply resume customizations that might have been modified by special day logic
        entry.setDayEndTime(null); // Critical - we're resuming
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
        entry.setAdminSync(MergingStatusConstants.USER_IN_PROCESS);
    }

    @Override
    protected String getCommandDescription() {
        return "resume previous session";
    }

    // ========================================================================
    // PRESERVED ORIGINAL HELPER METHODS
    // ========================================================================

    private void processResumeSession(WorkUsersSessionsStates session, LocalDateTime resumeTime, SessionContext context) {
        debug("Processing resume for previously completed session");

        final LocalDateTime previousEndTime = session.getDayEndTime();
        if (previousEndTime != null) {
            debug(String.format("Creating temporary stop from %s to %s", previousEndTime, resumeTime));

            // Use the calculation command through context to add a break as temporary stop
            context.addBreakAsTempStop(session, previousEndTime, resumeTime);

            // Update total temporary stop minutes using the dedicated method
            int totalStopMinutes = context.calculateTotalTempStopMinutes(session, resumeTime);

            // Update session state using builder
            SessionEntityBuilder.updateSession(session, builder -> builder
                    .status(WorkCode.WORK_ONLINE)
                    .currentStartTime(resumeTime)
                    .dayEndTime(null)
                    .workdayCompleted(false)
                    .totalTemporaryStopMinutes(totalStopMinutes));

            debug(String.format("Updated total temporary stop minutes: %d", totalStopMinutes));
        } else {
            // If no previous end time, just update the session state
            SessionEntityBuilder.updateSession(session, builder -> builder
                    .status(WorkCode.WORK_ONLINE)
                    .currentStartTime(resumeTime)
                    .dayEndTime(null)
                    .workdayCompleted(false));
        }

        debug("Session state updated to WORK_ONLINE");
    }
}