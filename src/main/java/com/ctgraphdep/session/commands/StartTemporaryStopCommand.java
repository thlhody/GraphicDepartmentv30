package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Command to start a temporary stop (break) during a work session
public class StartTemporaryStopCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    public StartTemporaryStopCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        LoggerUtil.info(this.getClass(), String.format("Starting temporary stop for user: %s", username));

        // Get the current session
        WorkUsersSessionsStates session = context.getCurrentSession(username, userId);

        // Validate session is in the correct state
        if (!SessionValidator.isInOnlineState(session, this.getClass())) {
            return session; // Return early if validation fails
        }

        // Get standardized time values using the new validation system
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);
        LocalDateTime stopTime = timeValues.getCurrentTime();

        // Process temporary stop using the calculation command
        session = context.processTemporaryStop(session, stopTime);

        // Save the updated session using command factory
        SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(session);
        context.executeCommand(saveCommand);

        // Then update the worktime entry using the updated session info
        updateWorktimeEntryFromSession(session, context);

        LoggerUtil.info(this.getClass(), String.format("Temporary stop started for user %s", username));

        return session;
    }

    // Updates the worktime entry based on the session information
    private void updateWorktimeEntryFromSession(WorkUsersSessionsStates session, SessionContext context) {
        try {
            if (session.getDayStartTime() == null) {
                LoggerUtil.warn(this.getClass(), "Cannot update worktime entry: session has no start time");
                return;
            }

            LocalDate workDate = session.getDayStartTime().toLocalDate();

            // Find existing worktime entries for the month
            List<WorkTimeTable> entries = context.getWorkTimeService().loadUserEntries(
                    username,
                    workDate.getYear(),
                    workDate.getMonthValue(),
                    username
            );

            // Find the entry for today's date
            WorkTimeTable entry = entries.stream()
                    .filter(e -> e.getWorkDate().equals(workDate))
                    .findFirst()
                    .orElse(null);

            if (entry == null) {
                LoggerUtil.warn(this.getClass(), String.format("No worktime entry found for user %s on %s, cannot update",
                        username, workDate));
                return;
            }

            // Explicitly transfer worked minutes from session to worktime entry
            entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
            entry.setTemporaryStopCount(session.getTemporaryStopCount());
            entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
            entry.setAdminSync(SyncStatus.USER_IN_PROCESS);

            // Save the updated entry
            context.getWorkTimeService().saveWorkTimeEntry(
                    username,
                    entry,
                    workDate.getYear(),
                    workDate.getMonthValue(),
                    username
            );

            LoggerUtil.info(this.getClass(), String.format("Updated worktime entry for user %s - Total worked minutes: %d, Temp stop count: %d",
                    username, session.getTotalWorkedMinutes(), session.getTemporaryStopCount()
            ));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to update worktime entry with temporary stop for user %s: %s",
                    username, e.getMessage()));
        }
    }
}