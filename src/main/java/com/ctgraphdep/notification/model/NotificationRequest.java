package com.ctgraphdep.notification.model;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Model class representing a request to show a notification
 */
@Getter
public class NotificationRequest {

    private final NotificationType type;
    private final String username;
    private final Integer userId;
    private Integer finalMinutes;
    private String title;
    private String message;
    private String trayMessage;
    private Integer timeoutPeriod;
    private LocalDateTime tempStopStart;
    private int priority;

    private NotificationRequest(NotificationType type, String username, Integer userId) {
        this.type = type;
        this.username = username;
        this.userId = userId;
        this.priority = type.getDefaultPriority();
    }

    /**
     * Creates a new notification request builder
     */
    public static Builder builder(NotificationType type, String username, Integer userId) {
        return new Builder(type, username, userId);
    }

    /**
     * Builder for notification requests
     */
    public static class Builder {
        private final NotificationRequest request;

        private Builder(NotificationType type, String username, Integer userId) {
            this.request = new NotificationRequest(type, username, userId);
        }

        public Builder finalMinutes(Integer finalMinutes) {
            request.finalMinutes = finalMinutes;
            return this;
        }

        public Builder title(String title) {
            request.title = title;
            return this;
        }

        public Builder message(String message) {
            request.message = message;
            return this;
        }

        public Builder trayMessage(String trayMessage) {
            request.trayMessage = trayMessage;
            return this;
        }

        public Builder timeoutPeriod(Integer timeoutPeriod) {
            request.timeoutPeriod = timeoutPeriod;
            return this;
        }

        public Builder tempStopStart(LocalDateTime tempStopStart) {
            request.tempStopStart = tempStopStart;
            return this;
        }

        public Builder priority(int priority) {
            request.priority = priority;
            return this;
        }

        public NotificationRequest build() {
            // Validation logic can be added here
            return request;
        }
    }
}