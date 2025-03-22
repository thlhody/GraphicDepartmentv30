package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.queries.CalculateTotalTempStopMinutesQuery;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.util.SessionEntityBuilder;

import java.time.LocalDateTime;

/**
 * Command to process resuming from a temporary stop
 */
public class ProcessResumeFromTempStopCommand extends BaseCalculationCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime resumeTime;

    public ProcessResumeFromTempStopCommand(WorkUsersSessionsStates session, LocalDateTime resumeTime) {
        this.session = session;
        this.resumeTime = resumeTime;
    }

    @Override
    public void validate() {
        validateSession(session);
        validateDateTime(resumeTime, "Resume time");
    }

    @Override
    protected WorkUsersSessionsStates executeCommand(CalculationContext context) {
        // Update the last temporary stop
        UpdateLastTemporaryStopCommand updateCommand =
                context.getCommandFactory().createUpdateLastTemporaryStopCommand(session, resumeTime);
        context.executeCommand(updateCommand);

        // Calculate total temporary stop minutes
        CalculateTotalTempStopMinutesQuery query =
                context.getCommandFactory().createCalculateTotalTempStopMinutesQuery(session, resumeTime);
        int totalStopMinutes = context.executeQuery(query);

        // Update session
        SessionEntityBuilder.updateSession(session, builder -> {
            builder.status(WorkCode.WORK_ONLINE)
                    .currentStartTime(resumeTime)
                    .totalTemporaryStopMinutes(totalStopMinutes)
                    .finalWorkedMinutes(session.getTotalWorkedMinutes() != null ?
                            session.getTotalWorkedMinutes() : 0);
        });

        return session;
    }

    @Override
    protected WorkUsersSessionsStates handleError(Exception e) {
        return session;
    }
}