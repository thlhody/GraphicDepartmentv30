package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.util.SessionEntityBuilder;

import java.time.LocalDate;

/**
 * Command to create a worktime entry from a session
 */
public class CreateWorktimeEntryCommand extends BaseSessionCommand<WorkTimeTable> {
    private final String username;
    private final WorkUsersSessionsStates session;
    private final String operatingUsername;

    /**
     * Creates a command to create a worktime entry from a session
     *
     * @param username The username for the worktime entry
     * @param session The session to create the entry from
     * @param operatingUsername The username of the user performing the operation
     */
    public CreateWorktimeEntryCommand(String username, WorkUsersSessionsStates session, String operatingUsername) {
        validateUsername(username);
        validateCondition(session != null, "Session cannot be null");
        validateUsername(operatingUsername);

        this.username = username;
        this.session = session;
        this.operatingUsername = operatingUsername;
    }

    @Override
    public WorkTimeTable execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            // Validate that session has start time
            if (session.getDayStartTime() == null) {
                logAndThrow("Cannot create worktime entry: session has no start time");
            }

            // Use session date
            LocalDate sessionDate = session.getDayStartTime().toLocalDate();
            debug(String.format("Creating worktime entry for date: %s", sessionDate));

            // Use createWorktimeEntryFromSession
            WorkTimeTable entry = SessionEntityBuilder.createWorktimeEntryFromSession(session);

            // Save the entry using session date
            ctx.getWorkTimeService().saveWorkTimeEntry(
                    username,
                    entry,
                    sessionDate.getYear(),
                    sessionDate.getMonthValue(),
                    operatingUsername
            );

            info(String.format("Created worktime entry for user %s for date %s", username, sessionDate));

            return entry;
        });
    }
}