package com.ctgraphdep.session;

import com.ctgraphdep.session.commands.*;
import com.ctgraphdep.session.commands.notification.*;
import com.ctgraphdep.session.query.*;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

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

    // Creates a command to record a temporary stop continuation
    public RecordTempStopContinuationCommand createRecordTempStopContinuationCommand(String username, Integer userId, LocalDateTime continuationTime) {
        return new RecordTempStopContinuationCommand(username, userId, continuationTime);
    }

    //========
    // Work Session Calculations Commands
    //========

    // Creates a command to update session calculations
    public UpdateSessionCalculationsCommand createUpdateSessionCalculationsCommand(WorkUsersSessionsStates session,LocalDateTime explicitEndTime) {
        return new UpdateSessionCalculationsCommand(session,explicitEndTime);
    }

    // Creates a command to handle a session from a previous day
    public HandlePreviousDaySessionCommand createHandlePreviousDaySessionCommand(WorkUsersSessionsStates session) {
        return new HandlePreviousDaySessionCommand(session);
    }

    // Creates a command to create a worktime entry
    public CreateWorktimeEntryCommand createWorktimeEntryCommand(String username, Integer userId, WorkUsersSessionsStates session, String operatingUsername) {
        return new CreateWorktimeEntryCommand(username, userId, session, operatingUsername);
    }

    // Creates a command to update session activity timestamp
    public UpdateSessionActivityCommand createUpdateSessionActivityCommand(String username, Integer userId) {
        return new UpdateSessionActivityCommand(username, userId);
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

    // Creates a command to show temporary stop warning
    public ShowTempStopWarningCommand createShowTempStopWarningCommand(String username, Integer userId, LocalDateTime tempStopStart) {
        return new ShowTempStopWarningCommand(username, userId, tempStopStart);
    }

    // Creates a command to show start day reminder
    public ShowStartDayReminderCommand createShowStartDayReminderCommand(String username, Integer userId) {
        return new ShowStartDayReminderCommand(username, userId);
    }

    // Creates a command to show a test notification
    public ShowTestNotificationCommand createShowTestNotificationCommand(String username, Integer userId) {
        return new ShowTestNotificationCommand(username, userId);
    }

    // Creates a command to continue working
    public ContinueWorkingCommand createContinueWorkingCommand(String username, Integer userId, boolean isHourly) {
        return new ContinueWorkingCommand(username, userId, isHourly);
    }

    // Creates a command to track notification display
    public TrackNotificationDisplayCommand createTrackNotificationDisplayCommand(String username, Integer userId, int timeoutPeriod, boolean isTempStop) {
        return new TrackNotificationDisplayCommand(username, userId, timeoutPeriod, isTempStop);
    }

    // Creates a command to activate hourly monitoring for a user
    public ActivateHourlyMonitoringCommand createActivateHourlyMonitoringCommand(String username) {
        return new ActivateHourlyMonitoringCommand(username);
    }

    // Creates a command to record a continuation point
    public RecordContinuationPointCommand createRecordContinuationPointCommand(String username, Integer userId, LocalDateTime continuationTime, boolean isHourly) {
        return new RecordContinuationPointCommand(username, userId, continuationTime, isHourly);
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

    // Creates a query to get standardized time values (central source of time values for all commands)
    public GetSessionTimeValuesQuery getSessionTimeValuesQuery() {
        return new GetSessionTimeValuesQuery();
    }

    // Creates a query to get the current session
    public GetCurrentSessionQuery createGetCurrentSessionQuery(String username, Integer userId) {
        return new GetCurrentSessionQuery(username, userId);
    }

    // Creates a query to resolve which session to use
    public ResolveSessionQuery createResolveSessionQuery(String username, Integer userId) {
        return new ResolveSessionQuery(username, userId);
    }

    // Creates a query to check if a session is from a previous day
    public IsPreviousDaySessionQuery createIsPreviousDaySessionQuery(WorkUsersSessionsStates session) {
        return new IsPreviousDaySessionQuery(session);
    }

    // Creates a query to check if a user has a completed session for today
    public HasCompletedSessionForTodayQuery createHasCompletedSessionForTodayQuery(String username, Integer userId) {
        return new HasCompletedSessionForTodayQuery(username, userId);
    }

    // Creates a query to validate authentication and get user
    public AuthenticatedUserQuery createAuthenticatedUserQuery(UserDetails userDetails) {
        return new AuthenticatedUserQuery(userDetails);
    }

    // Creates a query to check for unresolved sessions
    public UnresolvedSessionQuery createUnresolvedSessionQuery(String username, Integer userId) {
        return new UnresolvedSessionQuery(username, userId);
    }

    // Creates a query to determine navigation context
    public NavigationContextQuery createNavigationContextQuery(User user) {
        return new NavigationContextQuery(user);
    }

    // Creates a query to extract username from session filename
    public ExtractUsernameFromSessionFileQuery createExtractUsernameFromSessionFileQuery(String filename) {
        return new ExtractUsernameFromSessionFileQuery(filename);
    }

    // Creates a query to generate a notification key
    public GetNotificationKeyQuery createGetNotificationKeyQuery(String username, String notificationType) {
        return new GetNotificationKeyQuery(username, notificationType);
    }

    // Creates a query to check if a notification can be shown
    public CanShowNotificationQuery createCanShowNotificationQuery(String username, String notificationType, Integer intervalMinutes, Map<String, LocalDateTime> lastNotificationTimes) {
        return new CanShowNotificationQuery(username, notificationType, intervalMinutes, lastNotificationTimes);
    }

    // Creates a query to check if a user is in temporary stop
    public IsInTemporaryStopQuery createIsInTemporaryStopQuery(String username, Integer userId) {
        return new IsInTemporaryStopQuery(username, userId);
    }

    // Creates a query to check if a user has any unresolved sessions
    public HasUnresolvedSessionQuery createHasUnresolvedSessionQuery(String username, Integer userId) {
        return new HasUnresolvedSessionQuery(username, userId);
    }
}