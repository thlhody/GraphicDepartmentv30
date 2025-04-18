package com.ctgraphdep.notification.events;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all notification events
 */
@Getter
public abstract class NotificationEvent {

    private final String eventId;
    private final LocalDateTime timestamp;
    private final String username;
    private final Integer userId;

    @Setter
    private int priority = 5; // Default priority - higher values processed first

    protected NotificationEvent(String username, Integer userId) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.username = username;
        this.userId = userId;
    }

    /**
     * Gets the timeout period in milliseconds for auto-dismissal
     */
    public abstract int getTimeoutPeriod();

    /**
     * Gets the notification type identifier
     */
    public abstract String getNotificationType();
}