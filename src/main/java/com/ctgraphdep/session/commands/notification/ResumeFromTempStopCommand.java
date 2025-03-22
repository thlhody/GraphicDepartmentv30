package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.commands.ResumeFromTemporaryStopCommand;

/**
 * Command to resume work from temporary stop via a notification.
 * This handles notification-specific logic for resuming from temporary stop.
 */
public class ResumeFromTempStopCommand extends BaseNotificationCommand<Boolean> {

    /**
     * Creates a new command to resume from temporary stop via notification
     *
     * @param username The username
     * @param userId The user ID
     */
    public ResumeFromTempStopCommand(String username, Integer userId) {
        super(username, userId);
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Resuming from temporary stop for user %s via notification", username));

            // Cancel any pending notification backup tasks
            ctx.getBackupService().cancelBackupTask(username);

            // Use the core ResumeFromTemporaryStopCommand to perform the actual resumption
            ResumeFromTemporaryStopCommand resumeCommand = ctx.getCommandFactory().createResumeFromTemporaryStopCommand(username, userId);
            ctx.executeCommand(resumeCommand);

            info(String.format("Successfully resumed from temporary stop for user %s via notification", username));

            return true;
        });
    }
}