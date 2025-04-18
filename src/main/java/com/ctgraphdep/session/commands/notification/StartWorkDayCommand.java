package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.commands.StartDayCommand;

/**
 * Command to start a work day from a notification.
 * This command delegates to the core StartDayCommand after performing notification-specific logic.
 */
public class StartWorkDayCommand extends BaseNotificationCommand<Boolean> {

    /**
     * Creates a new command to start a work day from a notification
     *
     * @param username The username
     * @param userId The user ID
     */
    public StartWorkDayCommand(String username, Integer userId) {
        super(username, userId);
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Starting work day from notification for user %s", username));

            // Use the core StartDayCommand to perform the actual day start
            StartDayCommand startDayCommand = ctx.getCommandFactory().createStartDayCommand(username, userId);
            ctx.executeCommand(startDayCommand);

            // If we reach this point, the start was successful
            info(String.format("Successfully started work day for user %s through notification", username));

            return true;
        });
    }
}