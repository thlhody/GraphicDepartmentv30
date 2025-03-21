package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;


// Query to check if a notification can be shown based on time interval.
public class CanShowNotificationQuery implements SessionQuery<Boolean> {
    private final String username;
    private final String notificationType;
    private final Integer intervalMinutes;
    private final Map<String, LocalDateTime> lastNotificationTimes;

    // Creates a new query to check if a notification can be shown
    public CanShowNotificationQuery(
            String username,
            String notificationType,
            Integer intervalMinutes,
            Map<String, LocalDateTime> lastNotificationTimes) {
        this.username = username;
        this.notificationType = notificationType;
        this.intervalMinutes = intervalMinutes;
        this.lastNotificationTimes = lastNotificationTimes;
    }

    @Override
    public Boolean execute(SessionContext context) {
        // Get standardized time values using the new validation system
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);

        // First get the notification key - now using the standard underscore format
        String key = username + "_" + notificationType;

        LocalDateTime lastTime = lastNotificationTimes.get(key);

        if (lastTime == null) {
            lastNotificationTimes.put(key, LocalDateTime.now());
            return true;
        }

        LocalDateTime now = timeValues.getCurrentTime();
        long minutesSinceLastNotification = ChronoUnit.MINUTES.between(lastTime, now);

        if (minutesSinceLastNotification >= intervalMinutes) {
            lastNotificationTimes.put(key, now);
            return true;
        }
        return false;
    }
}
