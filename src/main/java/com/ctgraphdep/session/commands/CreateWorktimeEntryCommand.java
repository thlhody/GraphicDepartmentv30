package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.session.util.SessionEntityBuilder;

import java.time.LocalDate;

/**
 * Command to create a worktime entry from a session
 */
public class CreateWorktimeEntryCommand implements SessionCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final WorkUsersSessionsStates session;
    private final String operatingUsername;

    public CreateWorktimeEntryCommand(String username, Integer userId, WorkUsersSessionsStates session, String operatingUsername) {
        this.username = username;
        this.userId = userId;
        this.session = session;
        this.operatingUsername = operatingUsername;
    }

    @Override
    public WorkTimeTable execute(SessionContext context) {
        try {
            // Get standardized time values
            GetSessionTimeValuesQuery timeQuery = new GetSessionTimeValuesQuery();
            GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

            LocalDate today = LocalDate.from(timeValues.getCurrentTime());

            // Create worktime entry using builder
            WorkTimeTable entry = SessionEntityBuilder.createWorktimeEntry(userId, session.getDayStartTime());

            // Save the entry
            context.getWorkTimeService().saveWorkTimeEntry(username, entry, today.getYear(), today.getMonthValue(), operatingUsername);

            LoggerUtil.info(this.getClass(), String.format("Created worktime entry for user %s", username));

            return entry;
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), "Failed to create worktime entry: %s",e);
        }
        return null;
    }
}