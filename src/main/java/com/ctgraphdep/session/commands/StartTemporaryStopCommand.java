package com.ctgraphdep.session.commands;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.config.CommandConstants;
import com.ctgraphdep.session.query.IsInTempStopMonitoringQuery;
import com.ctgraphdep.session.util.SessionValidator;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StartTemporaryStopCommand extends BaseWorktimeUpdateSessionCommand<WorkUsersSessionsStates> {

    private static final long TEMP_STOP_COOLDOWN_MS = 1500; // 1.5 seconds

    public StartTemporaryStopCommand(String username, Integer userId) {
        super(username, userId);
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithDeduplication(context, username, this::executeTempStopLogic, null, TEMP_STOP_COOLDOWN_MS);
    }

    public WorkUsersSessionsStates executeTempStopLogic(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Starting temporary stop for user: %s", username));

            // Check if already in temporary stop monitoring mode
            IsInTempStopMonitoringQuery isInTempStopQuery = ctx.getCommandFactory().createIsInTempStopMonitoringQuery(username);
            boolean alreadyInTempStop = ctx.executeQuery(isInTempStopQuery);

            if (alreadyInTempStop) {
                debug(String.format("User %s is already in temporary stop monitoring mode", username));
            }

            // Get the current session
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);

            // Validate session is in the correct state
            if (!SessionValidator.isInOnlineState(session, this.getClass())) {
                warn("Session is not in online state, cannot start temporary stop");
                return session;
            }

            LocalDateTime stopTime = getStandardCurrentTime(context);
            debug(String.format("Temporary stop time: %s", stopTime));

            // Process temporary stop using the calculation command
            debug("Processing temporary stop");
            session = ctx.processTemporaryStop(session, stopTime);

            // Save the updated session using command factory
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            // ENHANCED: Update worktime entry with special day detection using abstract base class
            updateWorktimeEntryWithSpecialDayLogic(session, ctx);

            // Explicitly pause schedule monitoring when entering temp stop
            manageMonitoringState(context, CommandConstants.PAUSE, username);

            info(String.format("Temporary stop started for user %s", username));
            return session;
        });
    }

    // ========================================================================
    // ABSTRACT METHOD IMPLEMENTATIONS - StartTemporaryStopCommand specific logic
    // ========================================================================

    @Override
    protected WorkTimeTable findOrCreateEntry(LocalDate workDate, WorkUsersSessionsStates session, SessionContext context) {
        // Temp stop commands should only update existing entries
        return findExistingEntry(workDate, context);
    }

    @Override
    protected void applyCommandSpecificCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization(CommandConstants.START_TEMP_STOP_COMMAND);

        // Apply temp stop specific fields
        entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
        entry.setAdminSync(MergingStatusConstants.USER_IN_PROCESS); // Still in process
    }

    @Override
    protected void applyPostSpecialDayCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization(CommandConstants.SPECIAL_START_TEMP_STOP_COMMAND);

        // Re-apply temp stop customizations that might have been modified by special day logic
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
        entry.setAdminSync(MergingStatusConstants.USER_IN_PROCESS);
    }

    @Override
    protected String getCommandDescription() {
        return CommandConstants.START_TEMP_STOP_COMMAND;
    }
}