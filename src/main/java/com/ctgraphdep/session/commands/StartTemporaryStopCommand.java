package com.ctgraphdep.session.commands;

import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.IsInTempStopMonitoringQuery;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Command to start a temporary stop (break) during a work session
 */
public class StartTemporaryStopCommand extends BaseSessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    /**
     * Creates a command to start a temporary stop
     *
     * @param username The username
     * @param userId The user ID
     */
    public StartTemporaryStopCommand(String username, Integer userId) {
        validateUsername(username);
        validateUserId(userId);

        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Starting temporary stop for user: %s", username));

            // NEW: Check if already in temporary stop monitoring mode
            IsInTempStopMonitoringQuery isInTempStopQuery = ctx.getCommandFactory().createIsInTempStopMonitoringQuery(username);
            boolean alreadyInTempStop = ctx.executeQuery(isInTempStopQuery);

            if (alreadyInTempStop) {
                debug(String.format("User %s is already in temporary stop monitoring mode", username));
                // We can still proceed with the command
            }

            // Get the current session
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);

            // Validate session is in the correct state
            if (!SessionValidator.isInOnlineState(session, this.getClass())) {
                warn("Session is not in online state, cannot start temporary stop");
                return session; // Return early if validation fails
            }

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);
            LocalDateTime stopTime = timeValues.getCurrentTime();
            debug(String.format("Temporary stop time: %s", stopTime));

            // Process temporary stop using the calculation command
            debug("Processing temporary stop");
            session = ctx.processTemporaryStop(session, stopTime);

            // Save the updated session using command factory
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            // Then update the worktime entry using the updated session info
            updateWorktimeEntryFromSession(session, ctx);

            // IMPROVEMENT: Explicitly pause schedule monitoring when entering temp stop
            ctx.getSessionMonitorService().pauseScheduleMonitoring(username);

            info(String.format("Temporary stop started for user %s", username));

            return session;
        });
    }

    /**
     * Updates the worktime entry based on the session information
     */
    private void updateWorktimeEntryFromSession(WorkUsersSessionsStates session, SessionContext context) {
        try {
            if (session.getDayStartTime() == null) {
                warn("Cannot update worktime entry: session has no start time");
                return;
            }

            LocalDate workDate = session.getDayStartTime().toLocalDate();
            debug(String.format("Updating worktime entry for date: %s", workDate));

            // Find existing worktime entries for the month
            List<WorkTimeTable> entries = context.getWorktimeManagementService().loadUserEntries(username, workDate.getYear(), workDate.getMonthValue(), username);

            // Find the entry for today's date
            WorkTimeTable entry = entries.stream().filter(e -> e.getWorkDate().equals(workDate)).findFirst().orElse(null);

            if (entry == null) {
                warn(String.format("No worktime entry found for user %s on %s, cannot update", username, workDate));
                return;
            }

            // Explicitly transfer worked minutes from session to worktime entry
            debug("Updating worktime entry with session values");
            entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
            entry.setTemporaryStopCount(session.getTemporaryStopCount());
            entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
            entry.setAdminSync(SyncStatusWorktime.USER_IN_PROCESS);

            // Save the updated entry
            context.getWorktimeManagementService().saveWorkTimeEntry(username, entry, workDate.getYear(), workDate.getMonthValue(), username);

            info(String.format("Updated worktime entry for user %s - Total worked minutes: %d, Temp stop count: %d", username, session.getTotalWorkedMinutes(), session.getTemporaryStopCount()));

        } catch (Exception e) {
            error(String.format("Failed to update worktime entry with temporary stop for user %s: %s", username, e.getMessage()), e);
        }
    }
}
