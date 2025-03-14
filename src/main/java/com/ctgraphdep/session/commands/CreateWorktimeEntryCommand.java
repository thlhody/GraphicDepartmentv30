package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.session.util.SessionEntityBuilder;

import java.time.LocalDate;

/**
 * Command to create a worktime entry from a session
 */
public class CreateWorktimeEntryCommand implements SessionCommand<WorkTimeTable> {
    private final String username;
    private final WorkUsersSessionsStates session;
    private final String operatingUsername;

    public CreateWorktimeEntryCommand(String username, WorkUsersSessionsStates session, String operatingUsername) {
        this.username = username;
        this.session = session;
        this.operatingUsername = operatingUsername;
    }

    @Override
    public WorkTimeTable execute(SessionContext context) {
        try {
            // Use session date (this is correct)
            LocalDate sessionDate = session.getDayStartTime().toLocalDate();

            // CHANGE HERE - Use createWorktimeEntryFromSession instead of createWorktimeEntry
            WorkTimeTable entry = SessionEntityBuilder.createWorktimeEntryFromSession(session);

            // Save the entry using session date (this is correct)
            context.getWorkTimeService().saveWorkTimeEntry(username, entry, sessionDate.getYear(), sessionDate.getMonthValue(), operatingUsername);

            LoggerUtil.info(this.getClass(), String.format("Created worktime entry for user %s for date %s", username, sessionDate));

            return entry;
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), "Failed to create worktime entry: %s", e);
        }
        return null;
    }
}