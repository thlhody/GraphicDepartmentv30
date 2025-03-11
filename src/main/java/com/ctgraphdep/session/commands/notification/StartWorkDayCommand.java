package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.commands.StartDayCommand;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Command to start a work day from a notification.
 * This command delegates to the core StartDayCommand after performing notification-specific logic.
 */
public class StartWorkDayCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;

    /**
     * Creates a new command to start a work day from a notification
     *
     * @param username The username
     * @param userId The user ID
     */
    public StartWorkDayCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting work day from notification for user %s", username));

            // Cancel any pending notification backup tasks
            context.getBackupService().cancelBackupTask(username);

            // Use the core StartDayCommand to perform the actual day start
            StartDayCommand startDayCommand = context.getCommandFactory().createStartDayCommand(username, userId);
            context.executeCommand(startDayCommand);

            // If we reach this point, the start was successful
            LoggerUtil.info(this.getClass(), String.format("Successfully started work day for user %s through notification", username));

            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error starting work day through notification for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}