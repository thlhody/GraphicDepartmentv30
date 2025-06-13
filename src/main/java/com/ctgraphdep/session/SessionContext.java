package com.ctgraphdep.session;

import com.ctgraphdep.calculations.CalculationCommandFactory;
import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.CalculationCommandService;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.fileOperations.data.SessionDataService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.service.*;
import com.ctgraphdep.service.cache.SessionCacheService;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class SessionContext {
    // Core dependencies
    private final UserService userService;
    private final MainDefaultUserContextService mainDefaultUserContextService;
    private final SessionStatusService sessionStatusService;
    private final SessionMonitorService sessionMonitorService;
    private final FolderStatus folderStatus;
    private final SessionCommandFactory commandFactory;
    private final TimeValidationService validationService;
    private final NotificationService notificationService;
    private final SessionService sessionService;
    private final SessionDataService sessionDataService;
    private final DataAccessService dataAccessService;

    @Autowired
    private SessionCacheService sessionCacheService;
    private final WorktimeOperationContext worktimeOperationContext;
    // Calculation dependencies
    private final CalculationCommandFactory calculationFactory;
    private final CalculationContext calculationContext;
    private final CalculationCommandService calculationService;

    // Constructor with dependency injection
    public SessionContext(
            UserService userService, MainDefaultUserContextService mainDefaultUserContextService,
            SessionStatusService sessionStatusService,
            @Lazy SessionMonitorService sessionMonitorService,
            FolderStatus folderStatus,
            SessionCommandFactory commandFactory,
            TimeValidationService validationService,
            NotificationService notificationService,
            SessionService sessionService,
            SessionDataService sessionDataService, DataAccessService dataAccessService, WorktimeOperationContext worktimeOperationContext) {

        this.userService = userService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        this.sessionStatusService = sessionStatusService;
        this.sessionMonitorService = sessionMonitorService;
        this.folderStatus = folderStatus;
        this.commandFactory = commandFactory;
        this.validationService = validationService;
        this.notificationService = notificationService;
        this.sessionService = sessionService;
        this.sessionDataService = sessionDataService;
        this.dataAccessService = dataAccessService;
        this.worktimeOperationContext = worktimeOperationContext;

        // Initialize calculation components
        this.calculationFactory = new CalculationCommandFactory();
        this.calculationContext = new CalculationContext(this, calculationFactory);
        this.calculationService = new CalculationCommandService(calculationContext);
    }

    // Execute command
    public <T> T executeCommand(SessionCommand<T> command) {
        return command.execute(this);
    }

    // Execute query
    public <T> T executeQuery(SessionQuery<T> query) {
        return query.execute(this);
    }

    /**
     * Gets the current session for a user (now reads from cache)
     * @param username The username
     * @param userId The user ID
     * @return Current session from cache or file
     */
    public WorkUsersSessionsStates getCurrentSession(String username, Integer userId) {
        try {
            // Read from cache instead of direct file access
            return sessionCacheService.readSession(username, userId);
        } catch (Exception e) {
            // Fallback to direct file read if cache fails
            return sessionDataService.readLocalSessionFile(username, userId);
        }
    }

    // Session worktime adapter methods
    public List<WorkTimeTable> loadSessionWorktime(String username, int year, int month) {
        return worktimeOperationContext.loadUserWorktime(username, year, month);
    }

    public void saveSessionWorktime(String username, WorkTimeTable entry, int year, int month) {
        List<WorkTimeTable> entries = loadSessionWorktime(username, year, month);
        worktimeOperationContext.addOrReplaceEntry(entries, entry);
        worktimeOperationContext.saveUserWorktime(username, entries, year, month);
    }

    public WorkTimeTable findSessionEntry(String username, Integer userId, LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        List<WorkTimeTable> entries = loadSessionWorktime(username, year, month);
        return worktimeOperationContext.findEntryByDate(entries, userId, date).orElse(null);
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
        entry.setLunchBreakDeducted(session.getLunchBreakDeducted() != null ? session.getLunchBreakDeducted() : false);
        entry.setAdminSync(SyncStatusMerge.USER_IN_PROCESS);
        return entry;
    }


    // Calculate work time using CalculationService
    public WorkTimeCalculationResultDTO calculateWorkTime(int minutes, int schedule) {
        var query = calculationFactory.createCalculateWorkTimeQuery(minutes, schedule);
        return calculationService.executeQuery(query);
    }

    // Update session calculations using CalculationService
    public WorkUsersSessionsStates updateSessionCalculations(WorkUsersSessionsStates session, LocalDateTime currentTime, int userSchedule) {
        var command = calculationFactory.createSessionCalculationRouterCommand(session, currentTime, userSchedule);
        return calculationService.executeCommand(command);
    }

    public int calculateRawWorkMinutesForEntry(WorkTimeTable entry, LocalDateTime endTime){
        var query = calculationFactory.createCalculateRawWorkMinutesForEntryQuery(entry, endTime);
        return calculationService.executeQuery(query);
    }

    // Calculate total temporary stop minutes using CalculationService
    public int calculateTotalTempStopMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        var query = calculationFactory.createCalculateTotalTempStopMinutesQuery(session, currentTime);
        return calculationService.executeQuery(query);
    }

    // Add break as temporary stop using CalculationService
    public void addBreakAsTempStop(WorkUsersSessionsStates session, LocalDateTime startTime, LocalDateTime endTime) {
        var command = calculationFactory.createAddBreakAsTempStopCommand(session, startTime, endTime);
        calculationService.executeCommand(command);
    }

    // Process resuming from temporary stop using CalculationService
    public void processResumeFromTempStop(WorkUsersSessionsStates session, LocalDateTime resumeTime) {
        var command = calculationFactory.createProcessResumeFromTempStopCommand(session, resumeTime);
        calculationService.executeCommand(command);
    }

    // Calculate end day values using CalculationService
    public WorkUsersSessionsStates calculateEndDayValues(WorkUsersSessionsStates session, LocalDateTime endTime, Integer finalMinutes) {
        var command = calculationFactory.createCalculateEndDayValuesCommand(session, endTime, finalMinutes);
        return calculationService.executeCommand(command);
    }

    public WorkUsersSessionsStates processTemporaryStop(WorkUsersSessionsStates session, LocalDateTime stopTime) {
        var command = calculationFactory.createProcessTemporaryStopCommand(session, stopTime);
        return calculationService.executeCommand(command);
    }
}