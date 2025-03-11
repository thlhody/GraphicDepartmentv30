package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Query to check if a notification can be shown based on time interval.
 */
public class CanShowNotificationQuery implements SessionQuery<Boolean> {
    private final String username;
    private final String notificationType;
    private final Integer intervalMinutes;
    private final Map<String, LocalDateTime> lastNotificationTimes;

    /**
     * Creates a new query to check if a notification can be shown
     *
     * @param username The username
     * @param notificationType The notification type
     * @param intervalMinutes The minimum interval between notifications in minutes
     * @param lastNotificationTimes The map of last notification times
     */
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
        // Get standardized time values
        GetSessionTimeValuesQuery timeQuery = new GetSessionTimeValuesQuery();
        GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

        // First get the notification key
        GetNotificationKeyQuery keyQuery = new GetNotificationKeyQuery(username, notificationType);
        String key = keyQuery.execute(context);

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