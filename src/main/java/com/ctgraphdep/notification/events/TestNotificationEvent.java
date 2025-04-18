package com.ctgraphdep.notification.events;


import com.ctgraphdep.config.WorkCode;

/**
 * Event for test notification
 */
public class TestNotificationEvent extends NotificationEvent {

    public TestNotificationEvent(String username) {
        super(username, null);
        setPriority(10); // Highest priority
    }

    @Override
    public int getTimeoutPeriod() {
        return WorkCode.ON_FOR_TEN_SECONDS;
    }

    @Override
    public String getNotificationType() {
        return "TEST";
    }
}
