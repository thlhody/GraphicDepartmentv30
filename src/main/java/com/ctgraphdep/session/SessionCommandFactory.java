package com.ctgraphdep.session;

import com.ctgraphdep.service.SessionMidnightHandler;
import com.ctgraphdep.session.commands.*;
import com.ctgraphdep.session.commands.notification.*;
import com.ctgraphdep.session.query.*;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class SessionCommandFactory {

    //========
    // Session Management Commands
    //========

    // Creates a command to start a new day
    public StartDayCommand createStartDayCommand(String username, Integer userId) {
        return new StartDayCommand(username, userId);
    }

    // Creates a command to end the work day
    public EndDayCommand createEndDayCommand(String username, Integer userId, Integer finalMinutes, LocalDateTime endTime) {
        return new EndDayCommand(username, userId, finalMinutes, endTime);
    }

    // Creates a command to resume a previously completed session
    public ResumePreviousSessionCommand createResumePreviousSessionCommand(String username, Integer userId) {
        return new ResumePreviousSessionCommand(username, userId);
    }

    // Creates a command to save a session
    public SaveSessionCommand createSaveSessionCommand(WorkUsersSessionsStates session) {
        return new SaveSessionCommand(session);
    }

    // Add this to SessionCommandFactory
    public AutoEndSessionCommand createAutoEndSessionCommand(String username, Integer userId, LocalDateTime endTime) {
        return new AutoEndSessionCommand(username, userId, endTime);
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
    public PrepareSessionViewModelCommand createPrepareSessionViewModelCommand(Model model, User user) {
        return new PrepareSessionViewModelCommand(model, user);
    }

    //========
    // Query Methods
    //========

    // Creates a query to get the current session
    public GetCurrentSessionQuery createGetCurrentSessionQuery(String username, Integer userId) {
        return new GetCurrentSessionQuery(username, userId);
    }

    // Creates a query to determine navigation context
    public NavigationContextQuery createNavigationContextQuery(User user) {
        return new NavigationContextQuery(user);
    }

    public GetLocalUserQuery createGetLocalUserQuery() {
        return new GetLocalUserQuery();
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

    public GetUnresolvedEntriesQuery createGetUnresolvedEntriesQuery(String username, Integer userId) {
        return new GetUnresolvedEntriesQuery(username, userId);
    }
    /**
     * Creates a query to check if a user is in temporary stop monitoring mode
     *
     * @param username The username to check
     * @return A query that returns true if the user is in temporary stop monitoring
     */
    public IsInTempStopMonitoringQuery createIsInTempStopMonitoringQuery(String username) {
        return new IsInTempStopMonitoringQuery(username);
    }
}