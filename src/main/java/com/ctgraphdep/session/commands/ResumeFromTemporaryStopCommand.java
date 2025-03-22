package com.ctgraphdep.session.commands;

import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Command to resume work after a temporary stop
 */
public class ResumeFromTemporaryStopCommand extends BaseSessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    /**
     * Creates a command to resume work after a temporary stop
     *
     * @param username The username
     * @param userId The user ID
     */
    public ResumeFromTemporaryStopCommand(String username, Integer userId) {
        validateUsername(username);
        validateUserId(userId);

        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Resuming work for user %s after temporary stop", username));

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);
            LocalDateTime resumeTime = timeValues.getCurrentTime();
            debug(String.format("Resume time: %s", resumeTime));

            // Get the current session
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);

            // Validate session is in temporary stop state
            if (!SessionValidator.isInTemporaryStopState(session, this.getClass())) {
                warn("Session is not in temporary stop state, cannot resume");
                return session;
            }

            // Process the resume operation using the calculation command
            debug("Processing resume from temporary stop");
            ctx.processResumeFromTempStop(session, resumeTime);

            // Save the updated session
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            // Update the worktime entry with the resumed temporary stop information
            updateWorktimeEntryFromSession(session, ctx);

            info(String.format("Resumed work for user %s after temporary stop", username));

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
                warn(String.format("No worktime entry found for user %s on %s, cannot update", username, workDate));
                return;
            }

            // Update the entry with values from the session
            debug("Updating worktime entry with session values");
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

            info(String.format("Updated worktime entry for user %s with resumed temporary stop information", username));
        } catch (Exception e) {
            error(String.format("Failed to update worktime entry after resuming for user %s: %s", username, e.getMessage()), e);
        }
    }
}