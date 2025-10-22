package com.ctgraphdep.notification.api;

import java.time.LocalDateTime;

/**
 * Main interface for notification operations.
 * This provides high-level notification functionality to other parts of the application.
 */
public interface NotificationService {

    /**
     * Shows a schedule completion warning
     *
     * @param username The username
     * @param userId The user ID
     * @param finalMinutes The final minutes worked
     * @return true if notification was successfully displayed
     */
    boolean showScheduleEndNotification(String username, Integer userId, Integer finalMinutes);

    /**
     * Shows an hourly overtime warning
     *
     * @param username The username
     * @param userId The user ID
     * @param finalMinutes The final minutes worked
     * @return true if notification was successfully displayed
     */
    boolean showHourlyWarning(String username, Integer userId, Integer finalMinutes);

    /**
     * Shows a temporary stop duration warning
     *
     * @param username The username
     * @param userId The user ID
     * @param tempStopStart When the temporary stop started
     */
    void showTempStopWarning(String username, Integer userId, LocalDateTime tempStopStart);

    /**
     * Shows a work day start reminder
     *
     * @param username The username
     * @param userId The user ID
     */
    void showStartDayReminder(String username, Integer userId);

    /**
     * Shows a worktime resolution reminder
     *
     * @param username The username
     * @param userId The user ID
     * @param title The notification title
     * @param message The notification message
     * @param trayMessage The tray notification message
     * @param timeoutPeriod The timeout period in milliseconds
     */
    void showResolutionReminder(String username, Integer userId, String title, String message, String trayMessage, Integer timeoutPeriod);

    /**
     * Records when a notification is displayed for rate-limiting purposes
     *
     * @param username The username
     * @param notificationType The type of notification
     */
    void recordNotificationTime(String username, String notificationType);

    /**
     * Checks if a notification can be shown based on rate limiting
     *
     * @param username The username
     * @param notificationType The type of notification
     * @param intervalMinutes The minimum interval between notifications
     * @return true if the notification can be shown, false otherwise
     */
    boolean canShowNotification(String username, String notificationType, Integer intervalMinutes);

    /**
     * Resets the notification service state
     */
    void resetService();

    /**
     * Cancels any pending backup tasks for a user
     * @param username The username to cancel backup tasks for
     */
    void cancelNotificationBackup(String username);

    boolean showTestNotification();
}