package com.ctgraphdep.notification.events;

import com.ctgraphdep.config.WorkCode;
import lombok.Getter;

/**
 * Event for schedule completion notification
 */
@Getter
public class ScheduleEndEvent extends NotificationEvent {
    private final Integer finalMinutes;

    public ScheduleEndEvent(String username, Integer userId, Integer finalMinutes) {
        super(username, userId);
        this.finalMinutes = finalMinutes;
        setPriority(7); // High priority
    }

    @Override
    public int getTimeoutPeriod() {
        return WorkCode.ON_FOR_TEN_MINUTES;
    }

    @Override
    public String getNotificationType() {
        return WorkCode.SCHEDULE_END_TYPE;
    }
}
