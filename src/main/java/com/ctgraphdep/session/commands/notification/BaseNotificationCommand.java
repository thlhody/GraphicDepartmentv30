package com.ctgraphdep.session.commands.notification;

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

        this.username = username;
        this.userId = userId;
    }
}