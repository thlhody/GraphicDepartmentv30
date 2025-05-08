package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDateTime;

/**
 * Command for continuing work after a notification
 */
public class ContinueWorkingCommand extends BaseNotificationCommand<Boolean> {
    private final boolean isHourly;
    private final String username;

    /**
     * Creates a new command for continuing work
     *
     * @param username The username
     * @param isHourly Whether this is from an hourly notification
     */
    public ContinueWorkingCommand(String username, boolean isHourly) {
        super(username, null);
        this.username = username;
        this.isHourly = isHourly;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            info(String.format("Handling continue working for user %s (isHourly: %b)", username, isHourly));

            // Get current standardized time
            GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);
            LocalDateTime now = timeValues.getCurrentTime();
            // IMPORTANT: Cancel any backup tasks before activating hourly monitoring
            // This prevents the backup from showing another notification after transition
            context.getNotificationService().cancelNotificationBackup(username);

            // DIRECT APPROACH: Update monitoring service directly
            boolean success;
            if (isHourly) {
                // For hourly notifications, just update the last warning time
                success = context.getSessionMonitorService().activateExplicitHourlyMonitoring(username, now);
                info(String.format("Directly activated hourly monitoring for user %s: %b", username, success));
            } else {
                // For schedule completion, mark that user continued and start hourly monitoring
                success = context.getSessionMonitorService().activateExplicitHourlyMonitoring(username, now);
                info(String.format("Directly activated post-schedule hourly monitoring for user %s: %b", username, success));
            }

            return success;
        } catch (Exception e) {
            error(String.format("Error in continue working command for user %s: %s", username, e.getMessage()), e);
            return false;
        }
    }
}