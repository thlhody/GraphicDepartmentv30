package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.CalculateRawWorkMinutesQuery;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Command to resolve a session with a specific end time
 */
public class ResolveSessionCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;
    private final LocalDateTime endTime;

    public ResolveSessionCommand(String username, Integer userId, LocalDateTime endTime) {
        this.username = username;
        this.userId = userId;
        this.endTime = endTime;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Resolving session for user %s with end time %s", username, endTime));

            // Get the current session
            GetCurrentSessionQuery sessionQuery = context.getCommandFactory().createGetCurrentSessionQuery(username, userId);
            WorkUsersSessionsStates session = context.executeQuery(sessionQuery);

            if (session == null || session.getDayStartTime() == null) {
                throw new IllegalStateException("No valid session to resolve");
            }

            // Update session end time
            session.setDayEndTime(endTime);

            // If session was in temporary stop, resume first
            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                UpdateLastTemporaryStopCommand updateCommand =
                        context.getCommandFactory().createUpdateLastTemporaryStopCommand(session, endTime);
                context.executeCommand(updateCommand);
            }

            // Handle offline sessions from midnight handler
            boolean wasOffline = false;
            if (WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                wasOffline = true;
                session.setSessionStatus(WorkCode.WORK_ONLINE);
                LoggerUtil.info(this.getClass(), "Temporarily setting Offline session to Online for calculation");
            }

            // Update calculations based on the new end time
            UpdateSessionCalculationsCommand updateCommand =
                    context.getCommandFactory().createUpdateSessionCalculationsCommand(session, endTime);
            session = context.executeCommand(updateCommand);

            // Calculate worked minutes based on session
            CalculateRawWorkMinutesQuery calculateQuery =
                    context.getCommandFactory().createCalculateRawWorkMinutesQuery(session, endTime);
            int workedMinutes = context.executeQuery(calculateQuery);
            session.setTotalWorkedMinutes(workedMinutes);

            // Get user's schedule
            int userSchedule = context.getUserService().getUserById(userId)
                    .orElseThrow(() -> new IllegalStateException("User not found"))
                    .getSchedule();

            // Calculate final worked minutes
            WorkTimeCalculationResult result = context.calculateWorkTime(workedMinutes, userSchedule);

            // If we temporarily changed to Online, set back to Offline for consistency
            if (wasOffline) {
                session.setSessionStatus(WorkCode.WORK_OFFLINE);
            }

            // End the session
            EndDayCommand endCommand = context.getCommandFactory().createEndDayCommand(
                    username, userId, result.getProcessedMinutes(), endTime);
            context.executeCommand(endCommand);

            // Resolve continuation points
            ResolveContinuationPointsCommand resolvePointsCommand =
                    context.getCommandFactory().createResolveContinuationPointsCommand(
                            username, session.getDayStartTime().toLocalDate(), username, result.getOvertimeMinutes());
            context.executeCommand(resolvePointsCommand);

            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error resolving session: %s", e.getMessage()));
            throw new RuntimeException("Failed to resolve session: " + e.getMessage(), e);
        }
    }
}