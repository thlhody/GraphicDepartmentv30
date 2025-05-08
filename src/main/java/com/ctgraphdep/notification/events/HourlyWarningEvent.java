package com.ctgraphdep.notification.events;
import com.ctgraphdep.config.WorkCode;
import lombok.Getter;

/**
 * Event for hourly overtime warning
 */
@Getter
public class HourlyWarningEvent extends NotificationEvent {
    private final Integer finalMinutes;

    public HourlyWarningEvent(String username, Integer userId, Integer finalMinutes) {
        super(username, userId);
        this.finalMinutes = finalMinutes;
        setPriority(6);
    }

    @Override
    public int getTimeoutPeriod() {
        return WorkCode.ON_FOR_FIVE_MINUTES;
    }

    @Override
    public String getNotificationType() {
        return WorkCode.HOURLY_TYPE;
    }
}
