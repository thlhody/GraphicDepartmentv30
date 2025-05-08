package com.ctgraphdep.notification.events;

import com.ctgraphdep.config.WorkCode;
import lombok.Getter;

/**
 * Event for mockup notifications that can mimic different notification types
 * For demonstration purposes only
 */
@Getter
public class MockupNotificationEvent extends NotificationEvent {
    private final String title;
    private final String message;
    private final String trayMessage;
    private final String mockupType;

    public MockupNotificationEvent(String username, String title, String message, String trayMessage, String mockupType) {
        super(username, null);
        this.title = title;
        this.message = message;
        this.trayMessage = trayMessage;
        this.mockupType = mockupType;
        setPriority(10); // Highest priority
    }

    @Override
    public int getTimeoutPeriod() {
        return WorkCode.ON_FOR_TEN_SECONDS;
    }

    @Override
    public String getNotificationType() {
        return WorkCode.MOCKUP_TYPE; // Reuse test type to avoid adding new constants
    }
}