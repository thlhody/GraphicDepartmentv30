package com.ctgraphdep.session;

import com.ctgraphdep.fileOperations.service.SystemAvailabilityService;
import com.ctgraphdep.fileOperations.data.SessionDataService;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.service.*;
import com.ctgraphdep.service.cache.SessionCacheService;
import com.ctgraphdep.session.service.SessionMonitorService;
import com.ctgraphdep.session.service.SessionService;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public class SessionContext {
    // Core dependencies
    private final UserService userService;
    private final MainDefaultUserContextService mainDefaultUserContextService;
    private final ReadFileNameStatusService readFileNameStatusService;
    private final SessionMonitorService sessionMonitorService;
    private final FolderStatus folderStatus;
    private final SessionCommandFactory commandFactory;
    private final TimeValidationService validationService;
    private final NotificationService notificationService;
    private final SessionService sessionService;
    private final SessionDataService sessionDataService;
    private final SystemAvailabilityService systemAvailabilityService;

    @Autowired
    private SessionCacheService sessionCacheService;
    @Autowired
    private CalculationService calculationService;
    private final WorktimeOperationContext worktimeOperationContext;

    // Constructor with dependency injection
    public SessionContext(
            UserService userService, MainDefaultUserContextService mainDefaultUserContextService, ReadFileNameStatusService readFileNameStatusService,
            @Lazy SessionMonitorService sessionMonitorService,
            FolderStatus folderStatus, SessionCommandFactory commandFactory, TimeValidationService validationService,
            NotificationService notificationService, SessionService sessionService,
            SessionDataService sessionDataService, SystemAvailabilityService systemAvailabilityService, WorktimeOperationContext worktimeOperationContext) {

        this.userService = userService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        this.readFileNameStatusService = readFileNameStatusService;
        this.sessionMonitorService = sessionMonitorService;
        this.folderStatus = folderStatus;
        this.commandFactory = commandFactory;
        this.validationService = validationService;
        this.notificationService = notificationService;
        this.sessionService = sessionService;
        this.sessionDataService = sessionDataService;
        this.systemAvailabilityService = systemAvailabilityService;
        this.worktimeOperationContext = worktimeOperationContext;
    }

    // Execute command
    public <T> T executeCommand(SessionCommand<T> command) {
        return command.execute(this);
    }

    // Execute query
    public <T> T executeQuery(SessionQuery<T> query) {
        return query.execute(this);
    }

    public WorkUsersSessionsStates getCurrentSession(String username, Integer userId) {
        // SessionCacheService handles all fallback internally
        return sessionCacheService.readSessionWithFallback(username, userId);
    }

    // Load session worktime using data accessor pattern
    public List<WorkTimeTable> loadSessionWorktime(String username, int year, int month) {
        WorktimeDataAccessor accessor = worktimeOperationContext.getDataAccessor(username);
        return accessor.readWorktime(username, year, month);
    }

    // Save session worktime using data accessor pattern
    public void saveSessionWorktime(String username, WorkTimeTable entry, int year, int month) {
        WorktimeDataAccessor accessor = worktimeOperationContext.getDataAccessor(username);

        // Load current entries
        List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);
        if (entries == null) {
            entries = new ArrayList<>();
        }

        // Add or replace the entry (implement logic directly)
        addOrReplaceEntry(entries, entry);

        // Save using accessor
        try {
            accessor.writeWorktimeWithStatus(username, entries, year, month, mainDefaultUserContextService.getCurrentUserRole());
        } catch (UnsupportedOperationException e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Cannot save worktime for %s: accessor does not support write operations", username));
            throw new RuntimeException("Write operation not supported for this user context", e);
        }
    }

    // Find session entry using data accessor pattern
    public WorkTimeTable findSessionEntry(String username, Integer userId, LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();

        WorktimeDataAccessor accessor = worktimeOperationContext.getDataAccessor(username);
        List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);

        if (entries == null || entries.isEmpty()) {
            return null;
        }

        // Find entry by date and user ID (implement logic directly)
        return findEntryByDate(entries, userId, date);
    }

    // ========================================================================
    // UTILITY METHODS - Implement the missing logic directly
    // ========================================================================

    // Add or replace entry in list by date and user ID
    private void addOrReplaceEntry(List<WorkTimeTable> entries, WorkTimeTable newEntry) {
        // Remove existing entry for same date and user
        entries.removeIf(entry ->
                newEntry.getUserId().equals(entry.getUserId()) &&
                        newEntry.getWorkDate().equals(entry.getWorkDate())
        );

        // Add the new entry
        entries.add(newEntry);

        // Sort by date and user ID
        entries.sort(Comparator.comparing(WorkTimeTable::getWorkDate)
                .thenComparing(WorkTimeTable::getUserId));
    }

    // Find entry in list by date and user ID
    private WorkTimeTable findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst()
                .orElse(null);
    }

    // Use existing SessionEntityBuilder method
    public WorkTimeTable createWorktimeEntryFromSession(WorkUsersSessionsStates session) {
        return SessionEntityBuilder.createWorktimeEntryFromSession(session);
    }

    // Helper for updating existing entries with session data
    public WorkTimeTable updateEntryFromSession(WorkTimeTable entry, WorkUsersSessionsStates session) {
        // Update key fields that change during session
        entry.setDayEndTime(session.getDayEndTime());
        entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
        entry.setTotalOvertimeMinutes(session.getTotalOvertimeMinutes() != null ? session.getTotalOvertimeMinutes() : 0);
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
        entry.setTemporaryStops(session.getTemporaryStops());
        entry.setLunchBreakDeducted(session.getLunchBreakDeducted() != null ? session.getLunchBreakDeducted() : false);
        entry.setAdminSync(MergingStatusConstants.USER_IN_PROCESS);
        return entry;
    }


    // Calculate work time using CalculationService
    public WorkTimeCalculationResultDTO calculateWorkTime(int minutes, int schedule) {
        return calculationService.calculateWorkTime(minutes, schedule);
    }

    // Update session calculations using CalculationService
    public WorkUsersSessionsStates updateSessionCalculations(WorkUsersSessionsStates session, LocalDateTime currentTime, int userSchedule) {
        return calculationService.updateSessionCalculations(session, currentTime, userSchedule);
    }

    public int calculateRawWorkMinutesForEntry(WorkTimeTable entry, LocalDateTime endTime){
        return calculationService.calculateRawWorkMinutesForEntry(entry, endTime);
    }

    // Calculate total temporary stop minutes using CalculationService
    public int calculateTotalTempStopMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        return calculationService.calculateTotalTempStopMinutes(session, currentTime);
    }

    // Add break as temporary stop using CalculationService
    public WorkUsersSessionsStates addBreakAsTempStop(WorkUsersSessionsStates session, LocalDateTime startTime, LocalDateTime endTime) {
        return calculationService.addBreakAsTempStop(session, startTime, endTime);
    }

    // Process resuming from temporary stop using CalculationService
    public WorkUsersSessionsStates processResumeFromTempStop(WorkUsersSessionsStates session, LocalDateTime resumeTime) {
        return calculationService.processResumeFromTempStop(session, resumeTime);
    }

    // Calculate end day values using CalculationService
    public WorkUsersSessionsStates calculateEndDayValues(WorkUsersSessionsStates session, LocalDateTime endTime, Integer finalMinutes, int userSchedule) {
        return calculationService.calculateEndDayValues(session, endTime, finalMinutes, userSchedule);

    }

    // Calculate raw work minutes for session using CalculationService
    public int calculateRawWorkMinutes(WorkUsersSessionsStates session, LocalDateTime endTime) {
        return calculationService.calculateRawWorkMinutes(session, endTime);
    }

    public WorkUsersSessionsStates processTemporaryStop(WorkUsersSessionsStates session, LocalDateTime stopTime) {
        return calculationService.processTemporaryStop(session, stopTime);
    }

    /**
     * Check if a date is a weekend (Saturday or Sunday).
     * Delegates to CalculationService for consistency with the service layer pattern.
     */
    public boolean isWeekend(LocalDate date) {
        return calculationService.isWeekend(date);
    }

    /**
     * Normalizes schedule hours (defaults to 8 if invalid).
     * Delegates to CalculationService for centralized schedule validation.
     */
    public int normalizeSchedule(Integer schedule) {
        return calculationService.normalizeSchedule(schedule);
    }

    /**
     * Calculates the full day duration in minutes, accounting for lunch break.
     * Delegates to CalculationService for centralized schedule calculations.
     */
    public int calculateFullDayDuration(int schedule) {
        return calculationService.calculateFullDayDuration(schedule);
    }

    /**
     * Calculates the expected end time based on schedule and weekend status.
     * Delegates to CalculationService for centralized time calculations.
     */
    public LocalTime calculateExpectedEndTime(int schedule, boolean isWeekend) {
        return calculationService.calculateExpectedEndTime(schedule, isWeekend);
    }

    /**
     * Checks if a schedule includes a lunch break.
     * Delegates to CalculationService for centralized lunch break logic.
     */
    public boolean includesLunchBreak(int schedule) {
        return calculationService.includesLunchBreak(schedule);
    }

    // ========================================================================
// TIME AND DATE CONVENIENCE METHODS
// ========================================================================

    /**
     * Gets the current standardized date using the validation service.
     * Convenience method to avoid repetitive validation service calls throughout the codebase.
     */
    public LocalDate getCurrentStandardDate() {
        try {
            GetStandardTimeValuesCommand timeCommand = validationService.getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);
            return timeValues.getCurrentDate();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting current standard date: " + e.getMessage(), e);
            // Fallback to system date if validation service fails
            return LocalDate.now();
        }
    }

    /**
     * Gets the current standardized time using the validation service.
     * Convenience method to avoid repetitive validation service calls throughout the codebase.
     */
    public LocalDateTime getCurrentStandardTime() {
        try {
            GetStandardTimeValuesCommand timeCommand = validationService.getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);
            return timeValues.getCurrentTime();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting current standard time: " + e.getMessage(), e);
            // Fallback to system time if validation service fails
            return LocalDateTime.now();
        }
    }
}