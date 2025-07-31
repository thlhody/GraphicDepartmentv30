package com.ctgraphdep.session.commands;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.IsInTempStopMonitoringQuery;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDate;
import java.time.LocalDateTime;

// ============================================================================
// 1. REFACTORED StartTemporaryStopCommand
// ============================================================================

/**
 * REFACTORED StartTemporaryStopCommand using BaseWorktimeUpdateSessionCommand
 * Eliminates duplication while preserving all temp stop specific logic
 */
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

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);
            LocalDateTime stopTime = timeValues.getCurrentTime();
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
            ctx.getSessionMonitorService().pauseScheduleMonitoring(username);

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
        logCustomization("start temporary stop");

        // Apply temp stop specific fields
        entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
        entry.setAdminSync(MergingStatusConstants.USER_IN_PROCESS); // Still in process
    }

    @Override
    protected void applyPostSpecialDayCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("post-special-day start temporary stop");

        // Re-apply temp stop customizations that might have been modified by special day logic
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
        entry.setAdminSync(MergingStatusConstants.USER_IN_PROCESS);
    }

    @Override
    protected String getCommandDescription() {
        return "start temporary stop";
    }
}



/**
 * REFACTORING BENEFITS FOR TEMP STOP COMMANDS:
 * ✅ ELIMINATED DUPLICATION: No more repeated worktime update logic
 * ✅ PRESERVED FUNCTIONALITY: All original temp stop logic maintained
 * ✅ ENHANCED CAPABILITIES: Automatic special day detection and processing
 * ✅ CLEAN SEPARATION: Temp stop logic vs common worktime logic
 * ✅ CONSISTENT PATTERN: Same structure as other commands
 * WHAT'S PRESERVED:
 * - Temp stop validation and state checking
 * - Critical temp stop field updates
 * - Resume monitoring management
 * - Schedule completion checking
 * - All original error handling
 * WHAT'S ENHANCED:
 * - Special day detection for temp stops
 * - Proper SN/CO/CM/W handling during temp stops
 * - Consistent logging and field management
 * - Automatic re-application of temp stop fields after special day logic
 */