package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.commands.BaseSessionCommand;

/**
 * Base class for notification-related session commands.
 *
 * @param <T> The command result type
 */
public abstract class BaseNotificationCommand<T> extends BaseSessionCommand<T> {

    protected final String username;
    protected final Integer userId;

    /**
     * Creates a new notification command with username and user ID.
     *
     * @param username The username
     * @param userId The user ID
     */
    protected BaseNotificationCommand(String username, Integer userId) {
        validateUsername(username);

        this.username = username;
        this.userId = userId;
    }

    /**
     * Cancels any pending notification backup tasks for the user.
     *
     * @param context The session context
     */
    protected void cancelBackupTasks(SessionContext context) {
        try {
            context.getBackupService().cancelBackupTask(username);
            debug("Canceled backup tasks for user: " + username);
        } catch (Exception e) {
            warn("Failed to cancel backup tasks: " + e.getMessage());
        }
    }

    /**
     * Records notification display in the tracking system.
     *
     * @param context The session context
     * @param notificationType The type of notification
     */
    protected void recordNotificationDisplay(SessionContext context, String notificationType) {
        try {
            context.getNotificationService().recordNotificationTime(username, notificationType);
            debug("Recorded notification display for user: " + username + ", type: " + notificationType);
        } catch (Exception e) {
            warn("Failed to record notification display: " + e.getMessage());
        }
    }
}