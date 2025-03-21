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

// Command to resume work after a temporary stop
public class ResumeFromTemporaryStopCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    public ResumeFromTemporaryStopCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        LoggerUtil.info(this.getClass(), String.format("Resuming work for user %s after temporary stop", username));

        // Get standardized time values using the new validation system
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);
        LocalDateTime resumeTime = timeValues.getCurrentTime();

        // Get the current session
        WorkUsersSessionsStates session = context.getCurrentSession(username, userId);

        // Validate session is in temporary stop state
        if (!SessionValidator.isInTemporaryStopState(session, this.getClass())) {
            return session;
        }

        // Process the resume operation using the calculation command
        context.processResumeFromTempStop(session, resumeTime);

        // Save the updated session
        SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(session);
        context.executeCommand(saveCommand);

        // Update the worktime entry with the resumed temporary stop information
        updateWorktimeEntryFromSession(session, context);

        LoggerUtil.info(this.getClass(), String.format("Resumed work for user %s after temporary stop", username));

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
                LoggerUtil.warn(this.getClass(), String.format("No worktime entry found for user %s on %s, cannot update", username, workDate));
                return;
            }

            // Update the entry with values from the session
            entry.setTemporaryStopCount(session.getTemporaryStopCount());
            entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
            // Important for resuming: update the total temporary stop minutes from the session
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

            LoggerUtil.info(this.getClass(), String.format("Updated worktime entry for user %s with resumed temporary stop information", username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to update worktime entry after resuming for user %s: %s", username, e.getMessage()));
        }
    }
}