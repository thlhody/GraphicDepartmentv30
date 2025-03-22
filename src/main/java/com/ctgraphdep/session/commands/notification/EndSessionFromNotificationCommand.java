package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.commands.EndDayCommand;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDateTime;

/**
 * Command to end a session from a notification.
 * This handles notification-specific logic and then delegates to the core EndDayCommand.
 */
public class EndSessionFromNotificationCommand extends BaseNotificationCommand<Boolean> {
    private final Integer finalMinutes;

    /**
     * Creates a new command to end a session from a notification
     *
     * @param username The username
     * @param userId The user ID
     * @param finalMinutes The final minutes to record for the session
     */
    public EndSessionFromNotificationCommand(String username, Integer userId, Integer finalMinutes) {
        super(username, userId);
        this.finalMinutes = finalMinutes;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Ending session from notification for user %s", username));

            // Get standardized time values using the validation system
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);

            // Get current session to validate it's still active
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);
            if (session == null || !WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                warn(String.format("Cannot end session from notification - user %s is not online", username));
                return false;
            }

            // Cancel any pending notification backup tasks
            ctx.getBackupService().cancelBackupTask(username);

            LocalDateTime currentTime = timeValues.getCurrentTime();

            // Use the core EndDayCommand to perform the actual session end with explicit time
            EndDayCommand endDayCommand = ctx.getCommandFactory().createEndDayCommand(username, userId, finalMinutes, currentTime);
            ctx.executeCommand(endDayCommand);

            // Clear monitoring state
            ctx.getSessionMonitorService().clearMonitoring(username);

            // If we reach this point, the end was successful
            info(String.format("Successfully ended session from notification for user %s", username));

            return true;
        });
    }
}