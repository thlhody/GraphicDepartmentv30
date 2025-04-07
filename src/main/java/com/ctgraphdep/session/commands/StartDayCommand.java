package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.session.util.SessionEntityBuilder;

/**
 * Command to start a new work day for a user
 */
public class StartDayCommand extends BaseSessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    /**
     * Creates a new command to start a work day
     *
     * @param username The username
     * @param userId The user ID
     */
    public StartDayCommand(String username, Integer userId) {
        validateUsername(username);
        validateUserId(userId);

        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Executing StartDayCommand for user %s", username));

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);

            // Create fresh session with standardized start time
            WorkUsersSessionsStates newSession = SessionEntityBuilder.createSession(username, userId, timeValues.getStartTime());

            // Save session using command factory
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(newSession);
            ctx.executeCommand(saveCommand);

            // Create worktime entry
            CreateWorktimeEntryCommand createEntryCommand = ctx.getCommandFactory().createWorktimeEntryCommand(username, newSession, username);
            WorkTimeTable entry = ctx.executeCommand(createEntryCommand);

            // Save worktime entry using standardized date values
            ctx.getWorkTimeService().saveWorkTimeEntry(
                    username,
                    entry,
                    timeValues.getCurrentDate().getYear(),
                    timeValues.getCurrentDate().getMonthValue(),
                    username
            );

            // Start session monitoring
            ctx.getSessionMonitorService().startMonitoring(username);

            info(String.format("Started new session for user %s (start time set to %s)", username, timeValues.getStartTime()));

            return newSession;
        });
    }
}