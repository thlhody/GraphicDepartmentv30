package com.ctgraphdep.notification.events;
import com.ctgraphdep.config.WorkCode;
import lombok.Getter;

/**
 * Event for worktime resolution reminder
 */
@Getter
public class ResolutionReminderEvent extends NotificationEvent {
    private final String title;
    private final String message;
    private final String trayMessage;
    private final Integer timeoutPeriod;

    public ResolutionReminderEvent(String username, Integer userId,
                                   String title, String message,
                                   String trayMessage, Integer timeoutPeriod) {
        super(username, userId);
        this.title = title;
        this.message = message;
        this.trayMessage = trayMessage;
        this.timeoutPeriod = timeoutPeriod;
        setPriority(8);
    }

    @Override
    public int getTimeoutPeriod() {
        return timeoutPeriod != null ? timeoutPeriod : WorkCode.ON_FOR_TWELVE_HOURS;
    }

    @Override
    public String getNotificationType() {
        return WorkCode.RESOLUTION_REMINDER_TYPE;
    }
}

