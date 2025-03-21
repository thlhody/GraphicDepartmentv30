package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.session.util.SessionEntityBuilder;

// Command to start a new work day for a user
public class StartDayCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    public StartDayCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        LoggerUtil.info(this.getClass(), String.format("Executing StartDayCommand for user %s", username));

        // Get standardized time values using the new validation system
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);

        // Create fresh session with standardized start time
        WorkUsersSessionsStates newSession = SessionEntityBuilder.createSession(username, userId, timeValues.getStartTime());

        // Save session using command factory
        SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(newSession);
        context.executeCommand(saveCommand);

        // Create worktime entry
        CreateWorktimeEntryCommand createEntryCommand = context.getCommandFactory().createWorktimeEntryCommand(username, newSession, username);
        WorkTimeTable entry = context.executeCommand(createEntryCommand);

        // Save worktime entry using standardized date values
        context.getWorkTimeService().saveWorkTimeEntry(
                username,
                entry,
                timeValues.getCurrentDate().getYear(),
                timeValues.getCurrentDate().getMonthValue(),
                username
        );

        // Start session monitoring
        context.getSessionMonitorService().startMonitoring(username);

        LoggerUtil.info(this.getClass(),
                String.format("Started new session for user %s (start time set to %s)",
                        username, timeValues.getStartTime()));

        return newSession;
    }
}