package com.ctgraphdep.session;

import com.ctgraphdep.calculations.CalculationCommandFactory;
import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.CalculationCommandService;
import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.service.*;
import com.ctgraphdep.validation.TimeValidationService;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class SessionContext {
    // Core dependencies
    private final DataAccessService dataAccessService;
    private final UserWorkTimeService workTimeService;
    private final UserService userService;
    private final SessionStatusService sessionStatusService;
    private final SystemNotificationService notificationService;
    private final SystemNotificationBackupService backupService;
    private final SessionMonitorService sessionMonitorService;
    private final PathConfig pathConfig;
    private final FolderStatusService folderStatusService;
    private final SessionCommandFactory commandFactory;
    private final TimeValidationService validationService;

    // Calculation dependencies
    private final CalculationCommandFactory calculationFactory;
    private final CalculationContext calculationContext;
    private final CalculationCommandService calculationService;

    // Constructor with dependency injection
    public SessionContext(
            DataAccessService dataAccessService,
            UserWorkTimeService workTimeService,
            UserService userService,
            SessionStatusService sessionStatusService,
            SystemNotificationService notificationService,
            SystemNotificationBackupService backupService,
            SessionMonitorService sessionMonitorService,
            PathConfig pathConfig,
            FolderStatusService folderStatusService,
            SessionCommandFactory commandFactory, TimeValidationService validationService) {

        this.dataAccessService = dataAccessService;
        this.workTimeService = workTimeService;
        this.userService = userService;
        this.sessionStatusService = sessionStatusService;
        this.notificationService = notificationService;
        this.backupService = backupService;
        this.sessionMonitorService = sessionMonitorService;
        this.pathConfig = pathConfig;
        this.folderStatusService = folderStatusService;
        this.commandFactory = commandFactory;
        this.validationService = validationService;

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

    // Get current session
    public WorkUsersSessionsStates getCurrentSession(String username, Integer userId) {
        return dataAccessService.readLocalSessionFile(username, userId);
    }

    // Save session
    public void saveSession(WorkUsersSessionsStates session) {
        dataAccessService.writeLocalSessionFile(session);
    }

    // Calculate work time using CalculationService
    public WorkTimeCalculationResult calculateWorkTime(int minutes, int schedule) {
        var query = calculationFactory.createCalculateWorkTimeQuery(minutes, schedule);
        return calculationService.executeQuery(query);
    }

    // Calculate minutes between two times using CalculationService
    public int calculateMinutesBetween(LocalDateTime start, LocalDateTime end) {
        var query = calculationFactory.createCalculateMinutesBetweenQuery(start, end);
        return calculationService.executeQuery(query);
    }

    // Update session calculations using CalculationService
    public WorkUsersSessionsStates updateSessionCalculations(
            WorkUsersSessionsStates session,
            LocalDateTime currentTime,
            int userSchedule) {
        var command = calculationFactory.createUpdateSessionCalculationsCommand(session, currentTime, userSchedule);
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

    // Calculate worked minutes between two times using CalculationService
    public int calculateWorkedMinutesBetween(LocalDateTime startTime, LocalDateTime endTime) {
        var query = calculationFactory.createCalculateMinutesBetweenQuery(startTime, endTime);
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
    public WorkUsersSessionsStates calculateEndDayValues(
            WorkUsersSessionsStates session,
            LocalDateTime endTime,
            Integer finalMinutes) {
        var command = calculationFactory.createCalculateEndDayValuesCommand(session, endTime, finalMinutes);
        return calculationService.executeCommand(command);
    }

    public WorkUsersSessionsStates processTemporaryStop(WorkUsersSessionsStates session, LocalDateTime stopTime) {
        var command = calculationFactory.createProcessTemporaryStopCommand(session, stopTime);
        return calculationService.executeCommand(command);
    }
}