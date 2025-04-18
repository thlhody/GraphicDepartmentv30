package com.ctgraphdep.notification.events;
import com.ctgraphdep.config.WorkCode;

/**
 * Event for start day reminder
 */
public class StartDayReminderEvent extends NotificationEvent {

    public StartDayReminderEvent(String username, Integer userId) {
        super(username, userId);
        setPriority(9);
    }

    @Override
    public int getTimeoutPeriod() {
        return WorkCode.ON_FOR_TWELVE_HOURS;
    }

    @Override
    public String getNotificationType() {
        return WorkCode.START_DAY_TYPE;
    }
}

