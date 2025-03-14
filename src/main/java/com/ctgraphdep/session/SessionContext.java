package com.ctgraphdep.session;

import com.ctgraphdep.session.util.SessionCalculationService;
import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.service.*;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class SessionContext {
    // Properly declare all dependencies
    private final DataAccessService dataAccessService;
    private final UserWorkTimeService workTimeService;
    private final UserService userService;
    private final SessionStatusService sessionStatusService;
    private final SystemNotificationService notificationService;
    private final SystemNotificationBackupService backupService;
    private final SessionMonitorService sessionMonitorService;
    private final SessionCalculationService calculationService;
    private final PathConfig pathConfig;
    private final FolderStatusService folderStatusService;
    private final SessionCommandFactory commandFactory;

    // Proper constructor with all dependencies injected
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
            SessionCommandFactory commandFactory) {

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
        this.calculationService = new SessionCalculationService();
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

    // Wrapper around CalculateWorkHoursUtil
    public WorkTimeCalculationResult calculateWorkTime(int minutes, int schedule) {
        return CalculateWorkHoursUtil.calculateWorkTime(minutes, schedule);
    }

    // Calculate minutes between two times
    public int calculateMinutesBetween(LocalDateTime start, LocalDateTime end) {
        return CalculateWorkHoursUtil.calculateMinutesBetween(start, end);
    }

    // Update the updateSessionCalculations method in SessionContext.java
    public WorkUsersSessionsStates updateSessionCalculations(WorkUsersSessionsStates session,
                                                             LocalDateTime currentTime,
                                                             int userSchedule) {
        return calculationService.updateSessionCalculations(session, currentTime, userSchedule, this);
    }

    // Calculate raw work minutes
    public int calculateRawWorkMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        return calculationService.calculateRawWorkMinutes(session, currentTime);
    }

    // Calculate total temporary stop minutes
    public int calculateTotalTempStopMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        return calculationService.calculateTotalTempStopMinutes(session, currentTime);
    }

    // Calculate worked minutes between two times
    public int calculateWorkedMinutesBetween(LocalDateTime startTime, LocalDateTime endTime) {
        return calculationService.calculateWorkedMinutesBetween(startTime, endTime);
    }

    // Update last temporary stop with end time
    public void updateLastTemporaryStop(WorkUsersSessionsStates session, LocalDateTime endTime) {
        calculationService.updateLastTemporaryStop(session, endTime);
    }

    // Add break as temporary stop
    public void addBreakAsTempStop(WorkUsersSessionsStates session, LocalDateTime startTime, LocalDateTime endTime) {
        calculationService.addBreakAsTempStop(session, startTime, endTime);
    }

    // Process resuming from temporary stop
    public void processResumeFromTempStop(WorkUsersSessionsStates session, LocalDateTime resumeTime) {
        calculationService.processResumeFromTempStop(session, resumeTime);
    }

    // Calculate end day values
    public WorkUsersSessionsStates calculateEndDayValues(WorkUsersSessionsStates session,
                                                         LocalDateTime endTime,
                                                         Integer finalMinutes) {
        return calculationService.calculateEndDayValues(session, endTime, finalMinutes);
    }

}