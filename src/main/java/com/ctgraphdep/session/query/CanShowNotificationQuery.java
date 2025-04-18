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
    /**
     * Creates a new query to check if a notification can be shown
     *
     * @param username The username
     * @param notificationType The type of notification
     * @param intervalMinutes Minimum interval between notifications
     */
    public CanShowNotificationQuery(
            String username,
            String notificationType,
            Integer intervalMinutes) {

        ValidationUtil.validateNotEmpty(username, "Username");
        ValidationUtil.validateNotEmpty(notificationType, "Notification Type");
        ValidationUtil.validatePositive(intervalMinutes, "Interval Minutes");

        this.username = username;
        this.notificationType = notificationType;
        this.intervalMinutes = intervalMinutes;
    }

    public Boolean execute(SessionContext context) {
        return context.getNotificationService().canShowNotification(
                username,
                notificationType,
                intervalMinutes
        );
    }
}