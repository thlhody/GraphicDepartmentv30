package com.ctgraphdep.session;

import com.ctgraphdep.service.SessionMidnightHandler;
import com.ctgraphdep.session.commands.*;
import com.ctgraphdep.session.commands.notification.*;
import com.ctgraphdep.session.query.*;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;


// Factory for creating session commands
@Component
public class SessionCommandFactory {

    //========
    // Session Management Commands
    //========

    // Creates a command to start a new day
    public StartDayCommand createStartDayCommand(String username, Integer userId) {
        return new StartDayCommand(username, userId);
    }

    // Creates a command to start a work day from a notification
    public StartWorkDayCommand createStartWorkDayCommand(String username, Integer userId) {
        return new StartWorkDayCommand(username, userId);
    }

    // Creates a command to end the work day
    public EndDayCommand createEndDayCommand(String username, Integer userId, Integer finalMinutes, LocalDateTime endTime) {
        return new EndDayCommand(username, userId, finalMinutes, endTime);
    }

    // Creates a command to end a session from a notification
    public EndSessionFromNotificationCommand createEndSessionFromNotificationCommand(String username, Integer userId, Integer finalMinutes) {
        return new EndSessionFromNotificationCommand(username, userId, finalMinutes);
    }

    // Creates a command to resume a previously completed session
    public ResumePreviousSessionCommand createResumePreviousSessionCommand(String username, Integer userId) {
        return new ResumePreviousSessionCommand(username, userId);
    }

    // Creates a command to save a session
    public SaveSessionCommand createSaveSessionCommand(WorkUsersSessionsStates session) {
        return new SaveSessionCommand(session);
    }

    //========
    // Temporary Stop Commands
    //========

    // Creates a command to start a temporary stop
    public StartTemporaryStopCommand createStartTemporaryStopCommand(String username, Integer userId) {
        return new StartTemporaryStopCommand(username, userId);
    }

    // Creates a command to resume work after a temporary stop
    public ResumeFromTemporaryStopCommand createResumeFromTemporaryStopCommand(String username, Integer userId) {
        return new ResumeFromTemporaryStopCommand(username, userId);
    }

    // Creates a command to resume from temporary stop via a notification
    public ResumeFromTempStopCommand createResumeFromTempStopCommand(String username, Integer userId) {
        return new ResumeFromTempStopCommand(username, userId);
    }

    // Creates a command to continue temporary stop
    public ContinueTempStopCommand createContinueTempStopCommand(String username, Integer userId) {
        return new ContinueTempStopCommand(username, userId);
    }

    //========
    // Work Session Calculations Commands
    //========

    // Creates a command to update session calculations
    public UpdateSessionCalculationsCommand createUpdateSessionCalculationsCommand(WorkUsersSessionsStates session,LocalDateTime explicitEndTime) {
        return new UpdateSessionCalculationsCommand(session,explicitEndTime);
    }

    // Creates a command to create a worktime entry
    public CreateWorktimeEntryCommand createWorktimeEntryCommand(String username, WorkUsersSessionsStates session, String operatingUsername) {
        return new CreateWorktimeEntryCommand(username, session, operatingUsername);
    }

    // Creates a command to update session activity timestamp
    public UpdateSessionActivityCommand createUpdateSessionActivityCommand(String username, Integer userId) {
        return new UpdateSessionActivityCommand(username, userId);
    }

    public StartupSessionCheckCommand createStartupSessionCheckCommand(SessionMidnightHandler sessionMidnightHandler) {
        return new StartupSessionCheckCommand(sessionMidnightHandler);
    }

    //========
    // Notification Commands
    //========

    // Creates a command to show session warning
    public ShowSessionWarningCommand createShowSessionWarningCommand(String username, Integer userId, Integer finalMinutes) {
        return new ShowSessionWarningCommand(username, userId, finalMinutes);
    }

    // Creates a command to show hourly warning
    public ShowHourlyWarningCommand createShowHourlyWarningCommand(String username, Integer userId, Integer finalMinutes) {
        return new ShowHourlyWarningCommand(username, userId, finalMinutes);
    }
    //Creates a command to show worktime resolution warning
    public ShowResolutionReminderCommand createShowResolutionReminderCommand(String username, Integer userId, String title, String message, String trayMessage, Integer timeoutPeriod){
        return new ShowResolutionReminderCommand(username, userId, title, message, trayMessage, timeoutPeriod);
    }

    // Creates a command to show temporary stop warning
    public ShowTempStopWarningCommand createShowTempStopWarningCommand(String username, Integer userId, LocalDateTime tempStopStart) {
        return new ShowTempStopWarningCommand(username, userId, tempStopStart);
    }

    // Creates a command to show start day reminder
    public ShowStartDayReminderCommand createShowStartDayReminderCommand(String username, Integer userId) {
        return new ShowStartDayReminderCommand(username, userId);
    }

    // Creates a command to show a test notification
    public ShowTestNotificationCommand createShowTestNotificationCommand(String username) {
        return new ShowTestNotificationCommand(username);
    }

    // Creates a command to continue working
    public ContinueWorkingCommand createContinueWorkingCommand(String username, boolean isHourly) {
        return new ContinueWorkingCommand(username, isHourly);
    }

    // Creates a command to track notification display
    public TrackNotificationDisplayCommand createTrackNotificationDisplayCommand(String username, Integer userId, boolean isTempStop) {
        return new TrackNotificationDisplayCommand(username, userId, isTempStop);
    }

    // Creates a command to activate hourly monitoring for a user
    public ActivateHourlyMonitoringCommand createActivateHourlyMonitoringCommand(String username) {
        return new ActivateHourlyMonitoringCommand(username);
    }

    //========
    // UI/View Commands
    //========

    // Creates a command to prepare the session view model
    public PrepareSessionViewModelCommand createPrepareSessionViewModelCommand(Model model, WorkUsersSessionsStates session, User user) {
        return new PrepareSessionViewModelCommand(model, session, user);
    }

    //========
    // Query Methods
    //========

    // Creates a query to get the current session
    public GetCurrentSessionQuery createGetCurrentSessionQuery(String username, Integer userId) {
        return new GetCurrentSessionQuery(username, userId);
    }

    // Creates a query to resolve which session to use
    public ResolveSessionQuery createResolveSessionQuery(String username, Integer userId) {
        return new ResolveSessionQuery(username, userId);
    }

    // Creates a query to validate authentication and get user
    public AuthenticatedUserQuery createAuthenticatedUserQuery(UserDetails userDetails) {
        return new AuthenticatedUserQuery(userDetails);
    }

    // Creates a query to determine navigation context
    public NavigationContextQuery createNavigationContextQuery(User user) {
        return new NavigationContextQuery(user);
    }

    public GetLocalUserQuery createGetLocalUserQuery() {
        return new GetLocalUserQuery();
    }

    // Creates a query to check if a notification can be shown
    public CanShowNotificationQuery createCanShowNotificationQuery(String username, String notificationType, Integer intervalMinutes) {
        return new CanShowNotificationQuery(username, notificationType, intervalMinutes);
    }

    // Creates a query to get work schedule information
    public WorkScheduleQuery createWorkScheduleQuery(LocalDate date, Integer userSchedule) {
        return new WorkScheduleQuery(date, userSchedule);
    }

    public WorktimeResolutionQuery createWorktimeResolutionQuery(String username) {
        return new WorktimeResolutionQuery(username);
    }
    public SessionStatusQuery createSessionStatusQuery(String username, Integer userId){
        return new SessionStatusQuery(username, userId);
    }
}