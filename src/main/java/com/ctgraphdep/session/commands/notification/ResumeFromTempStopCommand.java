package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.commands.ResumeFromTemporaryStopCommand;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Command to resume work from temporary stop via a notification.
 * This handles notification-specific logic for resuming from temporary stop.
 */
public class ResumeFromTempStopCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;

    /**
     * Creates a new command to resume from temporary stop via notification
     *
     * @param username The username
     * @param userId The user ID
     */
    public ResumeFromTempStopCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Resuming from temporary stop for user %s via notification", username));

            // Cancel any pending notification backup tasks
            context.getBackupService().cancelBackupTask(username);

            // Use the core ResumeFromTemporaryStopCommand to perform the actual resumption
            ResumeFromTemporaryStopCommand resumeCommand = context.getCommandFactory().createResumeFromTemporaryStopCommand(username, userId);
            context.executeCommand(resumeCommand);

            LoggerUtil.info(this.getClass(), String.format("Successfully resumed from temporary stop for user %s via notification", username));

            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error resuming from temporary stop for user %s: %s", username, e.getMessage()), e);
            return false;
        }
    }
}