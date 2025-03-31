package com.ctgraphdep.model.dto;

import com.ctgraphdep.enums.NotificationType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Class representing a queued notification
 */
@Getter
@Setter
@RequiredArgsConstructor
public class QueuedNotification {
    private final String id = java.util.UUID.randomUUID().toString();
    private final NotificationType type;
    private final String username;
    private final Integer userId;
    private final Integer finalMinutes;
    private int priority; // Higher values = higher priority

    private final LocalDateTime createdAt = LocalDateTime.now();
    private int retryCount = 0;
    private final int maxRetries = 3;
    private boolean processed = false;
    private String lastError;

    // Optional fields for specific notification types
    private LocalDateTime tempStopStart;
    private String title;
    private String message;
    private String trayMessage;
    private Integer timeoutPeriod;

    public QueuedNotification(NotificationType type, String username, Integer userId,
                              Integer finalMinutes, int priority) {
        this.type = type;
        this.username = username;
        this.userId = userId;
        this.finalMinutes = finalMinutes;
        this.priority = priority;
    }
    /**
     * Increment the retry count and return the new value
     * @return The updated retry count
     */
    public int incrementRetryCount() {
        return ++retryCount;
    }
}