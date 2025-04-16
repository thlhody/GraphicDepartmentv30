package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.utils.ValidationUtil;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.HashMap;

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
     * @param notificationType The type of notification
     * @param intervalMinutes Minimum interval between notifications
     * @param lastNotificationTimes Map of last notification times
     */
    public CanShowNotificationQuery(
            String username,
            String notificationType,
            Integer intervalMinutes,
            Map<String, LocalDateTime> lastNotificationTimes) {

        ValidationUtil.validateNotEmpty(username, "Username");
        ValidationUtil.validateNotEmpty(notificationType, "Notification Type");
        ValidationUtil.validatePositive(intervalMinutes, "Interval Minutes");

        // Use an empty map if null is provided
        this.lastNotificationTimes = lastNotificationTimes != null
                ? new HashMap<>(lastNotificationTimes)
                : new HashMap<>();

        this.username = username;
        this.notificationType = notificationType;
        this.intervalMinutes = intervalMinutes;
    }

    @Override
    public Boolean execute(SessionContext context) {
        // Get standardized time values using the validation system
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();

        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);

        // Generate a unique notification key
        String key = String.format("%s_%s", username, notificationType);

        LocalDateTime lastTime = lastNotificationTimes.get(key);
        LocalDateTime now = timeValues.getCurrentTime();

        // If no previous notification, allow
        if (lastTime == null) {
            lastNotificationTimes.put(key, now);
            return true;
        }

        // Calculate minutes since last notification
        long minutesSinceLastNotification = ChronoUnit.MINUTES.between(lastTime, now);

        // Check if enough time has passed
        if (minutesSinceLastNotification >= intervalMinutes) {
            lastNotificationTimes.put(key, now);
            return true;
        }

        return false;
    }
}