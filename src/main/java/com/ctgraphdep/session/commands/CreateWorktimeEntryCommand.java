package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;

/**
 * REFACTORED CreateWorktimeEntryCommand - Now much simpler!
 * Just delegates to existing SessionEntityBuilder.createWorktimeEntryFromSession()
 */
@Deprecated
public class CreateWorktimeEntryCommand extends BaseSessionCommand<WorkTimeTable> {
    private final String username;
    private final WorkUsersSessionsStates session;

    public CreateWorktimeEntryCommand(String username, WorkUsersSessionsStates session) {
        validateUsername(username);
        validateCondition(session != null, "Session cannot be null");
        assert session != null;
        validateCondition(session.getDayStartTime() != null, "Session must have start time");

        this.username = username;
        this.session = session;
    }

    @Override
    public WorkTimeTable execute(SessionContext context) {
        return executeWithDefault(context, ctx -> {
            info(String.format("Creating worktime entry from session for user %s", username));

            // REFACTORED: Simply delegate to existing SessionEntityBuilder method
            // All the logic for creating worktime entry from session is already there!
            WorkTimeTable entry = ctx.createWorktimeEntryFromSession(session);

            debug(String.format("Created worktime entry: date=%s, start=%s, totalMinutes=%d, tempStops=%d",
                    entry.getWorkDate(),
                    entry.getDayStartTime() != null ? entry.getDayStartTime().toLocalTime() : "null",
                    entry.getTotalWorkedMinutes(),
                    entry.getTemporaryStopCount()));

            info(String.format("Successfully created worktime entry for user %s", username));
            return entry;

        }, null);
    }
}
