package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        GetSessionTimeValuesQuery timeQuery = context.getCommandFactory().getSessionTimeValuesQuery();
        GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

        // Get the current session
        WorkUsersSessionsStates session = context.getCurrentSession(username, userId);

        // Validate session can be resumed - this requires special validation
        if (!SessionValidator.isCompletedSession(session, this.getClass())) {
            return session;
        }

        // Process resume operation
        processResumeSession(session, timeValues.getCurrentTime(), context);

        // Save session and update other entities
        updateEntitiesAndPersist(session, context);

        LoggerUtil.info(this.getClass(), String.format("Resumed previous session for user %s", username));

        return session;
    }


    // Handles the main resume process
    private void processResumeSession(WorkUsersSessionsStates session, LocalDateTime now, SessionContext context) {
        // Create a temporary stop for the break period
        final LocalDateTime previousEndTime = session.getDayEndTime();
        if (previousEndTime != null) {
            context.getCalculationService().addBreakAsTempStop(session, previousEndTime, now);
        }

        // Update session state using builder
        SessionEntityBuilder.updateSession(session, builder -> {
            builder.status(WorkCode.WORK_ONLINE)
                    .currentStartTime(now)
                    .dayEndTime(null)
                    .workdayCompleted(false);
        });
    }

    // Persists session changes and updates related entities
    private void updateEntitiesAndPersist(WorkUsersSessionsStates session, SessionContext context) {
        try {
            // Save session using SaveSessionCommand
            SaveSessionCommand saveCommand = new SaveSessionCommand(session);
            context.executeCommand(saveCommand);

            // Update the worktime entry
            updateWorktimeEntry(session, context);

            // Start monitoring
            context.getSessionMonitorService().startMonitoring(username);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error persisting resumed session: %s", e.getMessage()));
        }
    }

    // Updates the worktime entry for the resumed session
    private void updateWorktimeEntry(WorkUsersSessionsStates session, SessionContext context) {
        final LocalDate workDate = session.getDayStartTime().toLocalDate();

        try {
            // Get existing entries for this day
            List<WorkTimeTable> entries = loadUserEntries(workDate.getYear(), workDate.getMonthValue(), context);

            // Find the entry for this specific day
            WorkTimeTable existingEntry = findEntryForDate(entries, workDate);

            if (existingEntry != null) {
                // Update existing entry - set end time to null to indicate resumed session
                existingEntry.setDayEndTime(null);
                existingEntry.setAdminSync(SyncStatus.USER_IN_PROCESS);
                existingEntry.setTemporaryStopCount(session.getTemporaryStopCount());
                existingEntry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());

                // Save the updated entry
                saveWorkTimeEntry(existingEntry, workDate, context);
            } else {
                // Create a new worktime entry using the command
                CreateWorktimeEntryCommand createCommand = context.getCommandFactory()
                        .createWorktimeEntryCommand(username,  session, username);

                WorkTimeTable newEntry = context.executeCommand(createCommand);

                // Save the new entry
                saveWorkTimeEntry(newEntry, workDate, context);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to update worktime entry: %s", e.getMessage()));
        }
    }

    // Helper method to save worktime entry
    private void saveWorkTimeEntry(WorkTimeTable entry, LocalDate workDate, SessionContext context) {
        context.getWorkTimeService().saveWorkTimeEntry(
                username,
                entry,
                workDate.getYear(),
                workDate.getMonthValue(),
                username);

        LoggerUtil.info(this.getClass(), "Updated worktime entry for resumed session");
    }

    // Loads user entries for a specific period
    private List<WorkTimeTable> loadUserEntries(int year, int month, SessionContext context) {
        try {
            return context.getWorkTimeService().loadUserEntries(username, year, month, username);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading user entries: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Finds the entry for a specific date
    private WorkTimeTable findEntryForDate(List<WorkTimeTable> entries, LocalDate date) {
        return entries.stream()
                .filter(e -> e.getWorkDate().equals(date))
                .findFirst()
                .orElse(null);
    }
}