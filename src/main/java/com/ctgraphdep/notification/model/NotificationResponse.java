package com.ctgraphdep.notification.model;

import lombok.Getter;

/**
 * Model class representing a response from showing a notification
 */
@Getter
public class NotificationResponse {

    private final boolean success;
    private final String notificationId;
    private final String errorMessage;
    private final boolean dialogDisplayed;
    private final boolean trayDisplayed;

    private NotificationResponse(boolean success, String notificationId, String errorMessage,
                                 boolean dialogDisplayed, boolean trayDisplayed) {
        this.success = success;
        this.notificationId = notificationId;
        this.errorMessage = errorMessage;
        this.dialogDisplayed = dialogDisplayed;
        this.trayDisplayed = trayDisplayed;
    }

    /**
     * Creates a successful response
     */
    public static NotificationResponse success(String notificationId, boolean dialogDisplayed, boolean trayDisplayed) {
        return new NotificationResponse(true, notificationId, null, dialogDisplayed, trayDisplayed);
    }

    /**
     * Creates a failure response
     */
    public static NotificationResponse failure(String errorMessage) {
        return new NotificationResponse(false, null, errorMessage, false, false);
    }

}