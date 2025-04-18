package com.ctgraphdep.notification.events;
import com.ctgraphdep.config.WorkCode;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Event for temporary stop warning
 */
@Getter
public class TempStopWarningEvent extends NotificationEvent {
    private final LocalDateTime tempStopStart;

    public TempStopWarningEvent(String username, Integer userId, LocalDateTime tempStopStart) {
        super(username, userId);
        this.tempStopStart = tempStopStart;
        setPriority(7);
    }

    @Override
    public int getTimeoutPeriod() {
        return WorkCode.ON_FOR_FIVE_MINUTES;
    }

    @Override
    public String getNotificationType() {
        return WorkCode.TEMP_STOP_TYPE;
    }
}
