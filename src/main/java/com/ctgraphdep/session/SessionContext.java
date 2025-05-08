package com.ctgraphdep.session;

import com.ctgraphdep.calculations.CalculationCommandFactory;
import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.CalculationCommandService;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.service.*;
import com.ctgraphdep.validation.TimeValidationService;
import lombok.Getter;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDateTime;

@Getter
public class SessionContext {
    // Core dependencies
    private final DataAccessService dataAccessService;
    private final WorktimeManagementService worktimeManagementService;
    private final UserService userService;
    private final SessionStatusService sessionStatusService;
    private final SessionMonitorService sessionMonitorService;
    private final FolderStatus folderStatus;
    private final SessionCommandFactory commandFactory;
    private final TimeValidationService validationService;
    private final NotificationService notificationService;
    private final SessionService sessionService;

    // Calculation dependencies
    private final CalculationCommandFactory calculationFactory;
    private final CalculationContext calculationContext;
    private final CalculationCommandService calculationService;

    // Constructor with dependency injection
    public SessionContext(
            DataAccessService dataAccessService,
            WorktimeManagementService worktimeManagementService,
            UserService userService,
            SessionStatusService sessionStatusService,
            @Lazy SessionMonitorService sessionMonitorService,
            FolderStatus folderStatus,
            SessionCommandFactory commandFactory, TimeValidationService validationService, NotificationService notificationService, SessionService sessionService) {

        this.dataAccessService = dataAccessService;
        this.worktimeManagementService = worktimeManagementService;
        this.userService = userService;
        this.sessionStatusService = sessionStatusService;
        this.sessionMonitorService = sessionMonitorService;
        this.folderStatus = folderStatus;
        this.commandFactory = commandFactory;
        this.validationService = validationService;
        this.notificationService = notificationService;
        this.sessionService = sessionService;

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