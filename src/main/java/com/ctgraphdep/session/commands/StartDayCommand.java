package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.config.CommandConstants;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.session.util.SessionSpecialDayDetector;
import com.ctgraphdep.validation.commands.ValidateSessionForStartCommand;

import java.time.LocalDate;

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
        manageMonitoringState(context, CommandConstants.CLEAR, username);

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
        WorkUsersSessionsStates newSession = SessionEntityBuilder.createSession(username, userId, getStandardCurrentTime(context));

        // Save session using command factory
        SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(newSession);
        context.executeCommand(saveCommand);

        // ENHANCED: Create/update worktime entry with special day detection using abstract base class
        updateWorktimeEntryWithSpecialDayLogic(newSession, context);

        // Start session monitoring
        manageMonitoringState(context, CommandConstants.START, username);

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
        logCustomization(CommandConstants.START_DAY);

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
        logCustomization(CommandConstants.SPECIAL_START_DAY);

        // For start day, we mainly need to ensure timeOffType is set correctly for special days
        // The special day logic should have already set it, but let's verify
        LocalDate workDate = session.getDayStartTime().toLocalDate();

        // If this is a weekend and no timeOffType is set, ensure it gets set to "W"
        if (SessionSpecialDayDetector.isWeekend(workDate) && (entry.getTimeOffType() == null || entry.getTimeOffType().trim().isEmpty())) {
            entry.setTimeOffType(WorkCode.WEEKEND_CODE);
            info(String.format("Set weekend timeOffType: %s", WorkCode.WEEKEND_CODE));
        }
    }

    @Override
    protected String getCommandDescription() {
        return CommandConstants.START_DAY;
    }

    // ========================================================================
    // PRESERVED ORIGINAL HELPER METHODS
    // ========================================================================

    private void resetSessionBeforeStart(SessionContext context) {
        manageMonitoringState(context, CommandConstants.CLEAR, username);

        // Create fresh offline session
        WorkUsersSessionsStates freshSession = SessionEntityBuilder.createSession(username, userId);
        freshSession.setSessionStatus(WorkCode.WORK_OFFLINE);
        freshSession.setWorkdayCompleted(false);

        SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(freshSession);
        context.executeCommand(saveCommand);

        info(String.format("Reset session for user %s before starting new one", username));
    }
}