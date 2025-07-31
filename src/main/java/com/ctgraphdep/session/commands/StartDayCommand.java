package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.session.util.SessionSpecialDayDetector;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.commands.ValidateSessionForStartCommand;

import java.time.LocalDate;

/**
 * REFACTORED StartDayCommand using BaseWorktimeUpdateSessionCommand
 * Eliminates duplication while preserving all original functionality
 * Handles weekend detection and timeOffType preservation
 */
public class StartDayCommand extends BaseWorktimeUpdateSessionCommand<WorkUsersSessionsStates> {

    private static final long START_COMMAND_COOLDOWN_MS = 3000; // 3 seconds

    public StartDayCommand(String username, Integer userId) {
        super(username, userId);
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithDeduplication(context, username, this::executeStartDayLogic, null, START_COMMAND_COOLDOWN_MS);
    }

    private WorkUsersSessionsStates executeStartDayLogic(SessionContext context) {
        info(String.format("Executing StartDayCommand with special day detection for user %s", username));

        // Clear monitoring first to prevent conflicts
        context.getSessionMonitorService().clearMonitoring(username);

        // Get standardized time values
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);

        // Get current session
        WorkUsersSessionsStates currentSession = context.getCurrentSession(username, userId);

        // Validate if session needs reset
        ValidateSessionForStartCommand validateCommand = context.getValidationService().getValidationFactory().createValidateSessionForStartCommand(currentSession);
        boolean needsReset = context.getValidationService().execute(validateCommand);

        if (needsReset) {
            info(String.format("Session reset needed for user %s", username));
            resetSessionBeforeStart(context);
        }

        // Create new session with standardized start time
        WorkUsersSessionsStates newSession = SessionEntityBuilder.createSession(username, userId, timeValues.getStartTime());

        // Save session using command factory
        SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(newSession);
        context.executeCommand(saveCommand);

        // ENHANCED: Create/update worktime entry with special day detection using abstract base class
        updateWorktimeEntryWithSpecialDayLogic(newSession, context);

        // Start session monitoring
        context.getSessionMonitorService().startEnhancedMonitoring(username);

        info(String.format("Successfully started session for user %s", username));
        return newSession;
    }

    // ========================================================================
    // ABSTRACT METHOD IMPLEMENTATIONS - StartDayCommand specific logic
    // ========================================================================

    @Override
    protected WorkTimeTable findOrCreateEntry(LocalDate workDate, WorkUsersSessionsStates session, SessionContext context) {
        // StartDayCommand should find existing entry or create new one
        return findOrCreateNewEntry(workDate, session, context);
    }

    @Override
    protected void applyCommandSpecificCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("start day");

        // Preserve existing timeOffType if it exists (critical for SN/CO/CM preservation)
        String existingTimeOffType = entry.getTimeOffType();
        if (existingTimeOffType != null && !existingTimeOffType.trim().isEmpty()) {
            info(String.format("Preserving existing timeOffType: %s", existingTimeOffType));
            // Don't modify it - keep the existing value
        } else {
            // For new entries, the timeOffType will be set by special day logic if needed
            debug("No existing timeOffType found - will be set by special day logic if needed");
        }

        // Set initial start day values
        entry.setDayStartTime(session.getDayStartTime());
        // Don't set dayEndTime - this is a start operation
    }

    @Override
    protected void applyPostSpecialDayCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("post-special-day start day");

        // For start day, we mainly need to ensure timeOffType is set correctly for special days
        // The special day logic should have already set it, but let's verify
        LocalDate workDate = session.getDayStartTime().toLocalDate();

        // If this is a weekend and no timeOffType is set, ensure it gets set to "W"
        if (SessionSpecialDayDetector.isWeekend(workDate) &&
                (entry.getTimeOffType() == null || entry.getTimeOffType().trim().isEmpty())) {
            entry.setTimeOffType(WorkCode.WEEKEND_CODE);
            info(String.format("Set weekend timeOffType: %s", WorkCode.WEEKEND_CODE));
        }
    }

    @Override
    protected String getCommandDescription() {
        return "start day";
    }

    // ========================================================================
    // PRESERVED ORIGINAL HELPER METHODS
    // ========================================================================

    private void resetSessionBeforeStart(SessionContext context) {
        context.getSessionMonitorService().clearMonitoring(username);

        // Create fresh offline session
        WorkUsersSessionsStates freshSession = SessionEntityBuilder.createSession(username, userId);
        freshSession.setSessionStatus(WorkCode.WORK_OFFLINE);
        freshSession.setWorkdayCompleted(false);

        SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(freshSession);
        context.executeCommand(saveCommand);

        info(String.format("Reset session for user %s before starting new one", username));
    }
}

/**
 * REFACTORING BENEFITS:
 * ✅ ELIMINATED DUPLICATION: No more repeated special day detection logic
 * ✅ PRESERVED FUNCTIONALITY: All original start day logic maintained
 * ✅ ENHANCED CAPABILITIES: Automatic special day detection and weekend handling
 * ✅ CLEAN SEPARATION: Command-specific logic clearly separated from common logic
 * ✅ EXTENSIBLE: Easy to add new special day types or modify logic
 * WHAT'S PRESERVED:
 * - Session validation and reset logic
 * - Monitoring start/stop
 * - Standardized time values
 * - Deduplication cooldown
 * - All original error handling
 * WHAT'S ENHANCED:
 * - Automatic weekend detection → creates "W" entries
 * - Existing SN/CO/CM preservation
 * - Special day overtime logic integration
 * - Consistent logging and error handling
 */