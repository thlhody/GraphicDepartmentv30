package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Command to resume a previously completed session
public class ResumePreviousSessionCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    public ResumePreviousSessionCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        LoggerUtil.info(this.getClass(), String.format("Executing ResumePreviousSessionCommand for user %s", username));

        // Get standardized time values
        // Get standardized time values using the new validation system
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);
        LocalDateTime resumeTime = timeValues.getCurrentTime();

        // Get the current session
        WorkUsersSessionsStates session = context.getCurrentSession(username, userId);

        // Validate session can be resumed - this requires special validation
        if (!SessionValidator.isCompletedSession(session, this.getClass())) {
            return session;
        }

        // Process resume operation - update the session first
        processResumeSession(session, resumeTime, context);

        // Save the updated session
        SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(session);
        context.executeCommand(saveCommand);

        // Then update the worktime entry using the session information
        updateWorktimeEntryFromSession(session, context);

        // Start monitoring
        context.getSessionMonitorService().startMonitoring(username);

        LoggerUtil.info(this.getClass(), String.format("Resumed previous session for user %s", username));

        return session;
    }

    // Handles the main resume process
    private void processResumeSession(WorkUsersSessionsStates session, LocalDateTime resumeTime, SessionContext context) {
        // Create a temporary stop for the break period
        final LocalDateTime previousEndTime = session.getDayEndTime();
        if (previousEndTime != null) {
            // Use the calculation command through context to add a break as temporary stop
            context.addBreakAsTempStop(session, previousEndTime, resumeTime);
            // Update total temporary stop minutes using the dedicated method
            int totalStopMinutes = context.calculateTotalTempStopMinutes(session, resumeTime);
            session.setTotalTemporaryStopMinutes(totalStopMinutes);
        }

        // Update session state using builder
        SessionEntityBuilder.updateSession(session, builder -> {
            builder.status(WorkCode.WORK_ONLINE)
                    .currentStartTime(resumeTime)
                    .dayEndTime(null)
                    .workdayCompleted(false);
        });
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

            // Find the entry for this specific day
            WorkTimeTable entry = entries.stream()
                    .filter(e -> e.getWorkDate().equals(workDate))
                    .findFirst()
                    .orElse(null);

            if (entry != null) {
                // Update existing entry with session values
                entry.setDayEndTime(null); // Reset end time since we're resuming
                entry.setTemporaryStopCount(session.getTemporaryStopCount());
                entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
                entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
                entry.setAdminSync(SyncStatus.USER_IN_PROCESS); // Mark as in-process

                // Save the updated entry
                context.getWorkTimeService().saveWorkTimeEntry(
                        username,
                        entry,
                        workDate.getYear(),
                        workDate.getMonthValue(),
                        username
                );

                LoggerUtil.info(this.getClass(), "Updated worktime entry for resumed session");
            } else {
                LoggerUtil.warn(this.getClass(), String.format("No worktime entry found for user %s on %s, cannot update", username, workDate));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Failed to update worktime entry: %s", e.getMessage()));
        }
    }
}