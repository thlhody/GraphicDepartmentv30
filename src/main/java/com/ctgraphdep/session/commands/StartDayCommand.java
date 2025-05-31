package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.commands.ValidateSessionForStartCommand;

/**
 * Enhanced StartDayCommand with deduplication protection.
 * Prevents issues from rapid clicking of "Start Session" button.
 */
public class StartDayCommand extends BaseSessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    // Custom cooldown for start command (longer because it's a major operation)
    private static final long START_COMMAND_COOLDOWN_MS = 3000; // 3 seconds

    public StartDayCommand(String username, Integer userId) {
        validateUsername(username);
        validateUserId(userId);
        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        // Use deduplication with custom cooldown
        return executeWithDeduplication(context, username, this::executeStartDayLogic, null, START_COMMAND_COOLDOWN_MS);
    }

    /**
     * The actual start day logic (separated for clarity).
     */
    private WorkUsersSessionsStates executeStartDayLogic(SessionContext context) {
        info(String.format("Executing StartDayCommand for user %s", username));

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

        // Create worktime entry
        CreateWorktimeEntryCommand createEntryCommand = context.getCommandFactory().createWorktimeEntryCommand(username, newSession, username);
        WorkTimeTable entry = context.executeCommand(createEntryCommand);

        // Save worktime entry using standardized date values
        context.getWorktimeManagementService().saveWorkTimeEntry(username, entry,
                timeValues.getCurrentDate().getYear(), timeValues.getCurrentDate().getMonthValue(), username);

        // Start session monitoring
        context.getSessionMonitorService().startEnhancedMonitoring(username);

        info(String.format("Successfully started session for user %s", username));
        return newSession;
    }

    /**
     * Reset session before starting new one.
     */
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