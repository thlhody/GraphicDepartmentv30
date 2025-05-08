package com.ctgraphdep.monitoring;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Centralized service for managing all monitoring state across the application.
 * This service replaces scattered state management in multiple services and
 * provides a single source of truth for monitoring modes and notification tracking.
 */
@Service
public class MonitoringStateService {
    private final TaskScheduler taskScheduler;

    // Unified user monitoring state
    private final Map<String, MonitoringState> userMonitoringStates = new ConcurrentHashMap<>();
    // Scheduled end time tasks - kept separate for clear cancellation
    private final Map<String, ScheduledFuture<?>> scheduledEndTasks = new ConcurrentHashMap<>();
    // Tracking of notification counts for limiting repeat notifications
    private final Map<String, Map<String, Integer>> notificationCountMap = new ConcurrentHashMap<>();
    // Rate limiting for notifications by type
    private final Map<String, LocalDateTime> lastNotificationTimes = new ConcurrentHashMap<>();

    // Comprehensive monitoring state object
    @Getter
    @Setter
    public static class MonitoringState {
        // Current monitoring mode
        private String monitoringMode = MonitoringMode.NONE;

        // Schedule monitoring state
        private boolean scheduleNotificationShown = false;
        private LocalDate lastStartDayCheck = null;

        // Hourly monitoring state
        private boolean continuedAfterSchedule = false;
        private LocalDateTime lastHourlyWarning = null;

        // Temporary stop state
        private LocalDateTime tempStopStart = null;
        private LocalDateTime lastTempStopNotification = null;

        // End time scheduling
        private LocalDateTime scheduledEndTime = null;
    }

    // Constants for monitoring modes
    public static class MonitoringMode {
        public static final String NONE = "NONE";
        public static final String SCHEDULE = WorkCode.SCHEDULE_END_TYPE;
        public static final String HOURLY = WorkCode.HOURLY_TYPE;
        public static final String TEMP_STOP = WorkCode.TEMP_STOP_TYPE;
    }

    public MonitoringStateService(@Qualifier("sessionMonitorScheduler") TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
        LoggerUtil.initialize(this.getClass(), null);
    }

    //=============================================
    // State Transition Methods
    //=============================================

    /**
     * Starts schedule completion monitoring for a user's session.
     * Called when a user starts a new work day or resumes from temporary stop.
     */
    public synchronized void startScheduleMonitoring(String username) {
        ensureStateExists(username);
        MonitoringState state = userMonitoringStates.get(username);

        String oldMode = state.getMonitoringMode();
        state.setMonitoringMode(MonitoringMode.SCHEDULE);
        state.setScheduleNotificationShown(false);
        state.setContinuedAfterSchedule(false);

        logStateTransition(username, oldMode, MonitoringMode.SCHEDULE);
    }

    /**
     * Transitions to hourly overtime monitoring.
     * Called when a user continues working after schedule completion.
     */
    public synchronized void transitionToHourlyMonitoring(String username, LocalDateTime timestamp) {
        ensureStateExists(username);
        MonitoringState state = userMonitoringStates.get(username);

        String oldMode = state.getMonitoringMode();
        state.setMonitoringMode(MonitoringMode.HOURLY);
        state.setScheduleNotificationShown(true);
        state.setContinuedAfterSchedule(true);
        state.setLastHourlyWarning(timestamp);

        logStateTransition(username, oldMode, MonitoringMode.HOURLY);
    }

    /**
     * Starts temporary stop monitoring.
     * Called when a user starts a temporary stop.
     */
    public synchronized void startTempStopMonitoring(String username, LocalDateTime tempStopStart) {
        ensureStateExists(username);
        MonitoringState state = userMonitoringStates.get(username);

        String oldMode = state.getMonitoringMode();
        state.setMonitoringMode(MonitoringMode.TEMP_STOP);
        state.setTempStopStart(tempStopStart);

        logStateTransition(username, oldMode, MonitoringMode.TEMP_STOP);
    }

    /**
     * Resume from temporary stop based on schedule completion status.
     * If schedule is completed, transitions to hourly monitoring, otherwise
     * transitions back to schedule monitoring.
     */
    public synchronized void resumeFromTempStop(String username, boolean scheduleCompleted) {
        ensureStateExists(username);
        MonitoringState state = userMonitoringStates.get(username);

        String oldMode = state.getMonitoringMode();

        if (scheduleCompleted) {
            // If schedule is already completed, go to hourly monitoring
            state.setMonitoringMode(MonitoringMode.HOURLY);
            state.setContinuedAfterSchedule(true);
            state.setScheduleNotificationShown(true);
            state.setLastHourlyWarning(LocalDateTime.now());
            logStateTransition(username, oldMode, MonitoringMode.HOURLY);
        } else {
            // Otherwise resume schedule monitoring
            state.setMonitoringMode(MonitoringMode.SCHEDULE);
            logStateTransition(username, oldMode, MonitoringMode.SCHEDULE);
        }

        // Clear temporary stop state
        state.setTempStopStart(null);
        state.setLastTempStopNotification(null);
    }

    /**
     * Stops all monitoring for a user.
     * Called when a session ends or at midnight reset.
     */
    public synchronized void stopMonitoring(String username) {
        MonitoringState state = userMonitoringStates.get(username);
        if (state != null) {
            String oldMode = state.getMonitoringMode();
            state.setMonitoringMode(MonitoringMode.NONE);
            logStateTransition(username, oldMode, MonitoringMode.NONE);
        }

        // Cancel any scheduled end time
        cancelScheduledEnd(username);

        // Clear state maps
        userMonitoringStates.remove(username);

        LoggerUtil.info(this.getClass(), String.format("Stopped all monitoring for user %s", username));
    }

    //=============================================
    // State Query Methods
    //=============================================

    /**
     * Checks if user is in schedule completion monitoring mode.
     */
    public boolean isInScheduleMonitoring(String username) {
        MonitoringState state = userMonitoringStates.get(username);
        return state != null && MonitoringMode.SCHEDULE.equals(state.getMonitoringMode());
    }

    /**
     * Checks if user is in hourly overtime monitoring mode.
     */
    public boolean isInHourlyMonitoring(String username) {
        MonitoringState state = userMonitoringStates.get(username);
        return state != null && MonitoringMode.HOURLY.equals(state.getMonitoringMode());
    }

    /**
     * Checks if user is in temporary stop monitoring mode.
     */
    public boolean isInTempStopMonitoring(String username) {
        MonitoringState state = userMonitoringStates.get(username);
        return state != null && MonitoringMode.TEMP_STOP.equals(state.getMonitoringMode());
    }

    /**
     * Gets the monitoring mode for a user.
     */
    public String getMonitoringMode(String username) {
        MonitoringState state = userMonitoringStates.get(username);
        return state != null ? state.getMonitoringMode() : MonitoringMode.NONE;
    }

    /**
     * Checks if schedule completion notification has been shown.
     */
    public boolean wasScheduleNotificationShown(String username) {
        MonitoringState state = userMonitoringStates.get(username);
        return state != null && state.isScheduleNotificationShown();
    }

    /**
     * Checks if user has continued working after schedule completion.
     */
    public boolean hasContinuedAfterSchedule(String username) {
        MonitoringState state = userMonitoringStates.get(username);
        return state != null && state.isContinuedAfterSchedule();
    }

    /**
     * Checks if an hourly notification is due.
     */
    public boolean isHourlyNotificationDue(String username, LocalDateTime currentTime) {
        MonitoringState state = userMonitoringStates.get(username);

        // Only check if user has continued after schedule
        if (state == null || !state.isContinuedAfterSchedule()) {
            return false;
        }

        LocalDateTime lastWarning = state.getLastHourlyWarning();
        if (lastWarning == null) {
            return false;
        }

        // Check if it's time for next hourly notification
        LocalDateTime nextHourlyTime = lastWarning.plusMinutes(WorkCode.HOURLY_INTERVAL);
        return currentTime.isAfter(nextHourlyTime);
    }

    /**
     * Checks if a temporary stop notification is due.
     */
    public boolean isTempStopNotificationDue(String username, int minutesSinceTempStop, LocalDateTime currentTime) {
        MonitoringState state = userMonitoringStates.get(username);

        if (state == null || state.getTempStopStart() == null) {
            return false;
        }

        // Get last notification time for this type
        String key = getNotificationKey(username, WorkCode.TEMP_STOP_TYPE);
        LocalDateTime lastNotification = lastNotificationTimes.get(key);

        // If enough time has passed since last notification and total time is a multiple of hourly interval
        return (lastNotification == null ||
                currentTime.isAfter(lastNotification.plusMinutes(WorkCode.HOURLY_INTERVAL - 2))) &&
                (minutesSinceTempStop >= WorkCode.HOURLY_INTERVAL) &&
                (minutesSinceTempStop % WorkCode.HOURLY_INTERVAL <= 5);
    }

    /**
     * Checks if a start day check was already done today.
     */
    public boolean wasStartDayCheckedToday(String username, LocalDate today) {
        MonitoringState state = userMonitoringStates.get(username);
        return state != null &&
                state.getLastStartDayCheck() != null &&
                state.getLastStartDayCheck().equals(today);
    }

    /**
     * Checks if a notification can be shown based on rate limiting.
     */
    public boolean canShowNotification(String username, String notificationType, int intervalMinutes) {
        String key = getNotificationKey(username, notificationType);
        LocalDateTime lastTime = lastNotificationTimes.get(key);
        LocalDateTime now = LocalDateTime.now();

        // If no previous notification, allow
        if (lastTime == null) {
            return true;
        }

        // Calculate minutes since last notification
        long minutesSinceLastNotification = ChronoUnit.MINUTES.between(lastTime, now);

        // Check if enough time has passed
        return minutesSinceLastNotification >= intervalMinutes;
    }

    //=============================================
    // Recording Methods
    //=============================================

    /**
     * Records that a schedule notification was shown.
     */
    public void markScheduleNotificationShown(String username) {
        ensureStateExists(username);
        MonitoringState state = userMonitoringStates.get(username);
        state.setScheduleNotificationShown(true);

        // Initialize notification count map if needed
        ensureNotificationCountMap(username);

        // Update schedule notification count
        Map<String, Integer> userCounts = notificationCountMap.get(username);
        int currentCount = userCounts.getOrDefault(WorkCode.SCHEDULE_END_TYPE, 0);
        userCounts.put(WorkCode.SCHEDULE_END_TYPE, currentCount + 1);

        LoggerUtil.info(this.getClass(),
                String.format("Marked schedule notification shown for user %s", username));
    }

    /**
     * Records an hourly notification timestamp.
     */
    public void recordHourlyNotification(String username, LocalDateTime timestamp) {
        ensureStateExists(username);
        MonitoringState state = userMonitoringStates.get(username);
        state.setLastHourlyWarning(timestamp);

        LoggerUtil.debug(this.getClass(),
                String.format("Recorded hourly notification for user %s at %s", username, timestamp));
    }

    /**
     * Records a temporary stop notification timestamp.
     */
    public void recordTempStopNotification(String username, LocalDateTime timestamp) {
        ensureStateExists(username);
        MonitoringState state = userMonitoringStates.get(username);
        state.setLastTempStopNotification(timestamp);

        LoggerUtil.debug(this.getClass(),
                String.format("Recorded temp stop notification for user %s at %s", username, timestamp));
    }

    /**
     * Records that start day was checked on a specific date.
     */
    public void recordStartDayCheck(String username, LocalDate date) {
        ensureStateExists(username);
        MonitoringState state = userMonitoringStates.get(username);
        state.setLastStartDayCheck(date);

        LoggerUtil.debug(this.getClass(),
                String.format("Recorded start day check for user %s on %s", username, date));
    }

    /**
     * Records the time a notification was shown (for rate limiting)
     */
    public void recordNotificationTime(String username, String notificationType) {
        String key = getNotificationKey(username, notificationType);
        lastNotificationTimes.put(key, LocalDateTime.now());

        LoggerUtil.debug(this.getClass(),
                String.format("Recorded notification time for %s - %s", username, notificationType));
    }

    /**
     * Increments and returns the notification count for a specific user and type
     * @param username The username
     * @param notificationType The type of notification
     * @param maxCount Maximum count allowed
     * @return The updated count after increment
     */
    public int incrementNotificationCount(String username, String notificationType, int maxCount) {
        // Initialize the map structure if it doesn't exist
        ensureNotificationCountMap(username);

        // Get the user's count map
        Map<String, Integer> userCounts = notificationCountMap.get(username);

        // Get current count
        int currentCount = userCounts.getOrDefault(notificationType, 0);

        // Increment count if below max
        if (currentCount < maxCount) {
            currentCount++;
            userCounts.put(notificationType, currentCount);
            LoggerUtil.debug(this.getClass(),
                    String.format("Incremented notification count for %s-%s to %d",
                            username, notificationType, currentCount));
        }

        return currentCount;
    }

    /**
     * Gets the notification count for a specific user and type.
     */
    public int getNotificationCount(String username, String notificationType) {
        if (!notificationCountMap.containsKey(username)) {
            return 0;
        }

        return notificationCountMap.get(username).getOrDefault(notificationType, 0);
    }

    //=============================================
    // End Time Scheduling Methods
    //=============================================

    /**
     * Schedules an automatic end time for a user's session.
     * @param username The username
     * @param endTime The scheduled end time
     * @param endAction The action to execute at end time
     * @return true if scheduling was successful
     */
    public boolean scheduleAutomaticEnd(String username, LocalDateTime endTime, Runnable endAction) {
        try {
            // Cancel any existing scheduled task
            cancelScheduledEnd(username);

            // Store the scheduled end time
            ensureStateExists(username);
            MonitoringState state = userMonitoringStates.get(username);
            state.setScheduledEndTime(endTime);

            // Calculate delay until end time
            LocalDateTime now = LocalDateTime.now();
            long delayMillis = ChronoUnit.MILLIS.between(now, endTime);

            if (delayMillis <= 0) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Cannot schedule end time in the past: %s (now: %s)",
                                endTime, now));
                state.setScheduledEndTime(null);
                return false;
            }

            // Schedule the end day task
            ScheduledFuture<?> task = taskScheduler.schedule(() -> {
                try {
                    LoggerUtil.info(this.getClass(),
                            String.format("Executing scheduled end session for user %s at %s",
                                    username, endTime));

                    // Execute the provided end action
                    endAction.run();

                    // Clean up after execution
                    MonitoringState updatedState = userMonitoringStates.get(username);
                    if (updatedState != null) {
                        updatedState.setScheduledEndTime(null);
                    }
                    scheduledEndTasks.remove(username);

                    LoggerUtil.info(this.getClass(),
                            String.format("Successfully executed scheduled end session for user %s",
                                    username));
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(),
                            String.format("Error in scheduled end for user %s: %s",
                                    username, e.getMessage()), e);
                }
            }, Instant.now().plusMillis(delayMillis)); // Use Instant instead of Duration

            // Store the task for potential cancellation
            scheduledEndTasks.put(username, task);

            LoggerUtil.info(this.getClass(),
                    String.format("Successfully scheduled end session for user %s at %s (in %d minutes)",
                            username, endTime, delayMillis / 60000));

            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error scheduling end for user %s: %s",
                            username, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Cancels a scheduled end time for a user.
     *
     * @param username The username
     * @return true if cancellation was successful or nothing was scheduled
     */
    public boolean cancelScheduledEnd(String username) {
        try {
            // Get the scheduled task
            ScheduledFuture<?> task = scheduledEndTasks.remove(username);

            // Cancel if it exists
            if (task != null && !task.isDone() && !task.isCancelled()) {
                task.cancel(false);
                LoggerUtil.info(this.getClass(),
                        String.format("Cancelled scheduled end for user %s", username));
            }

            // Remove the scheduled time
            MonitoringState state = userMonitoringStates.get(username);
            if (state != null) {
                state.setScheduledEndTime(null);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error cancelling scheduled end for user %s: %s",
                            username, e.getMessage()), e);
        }
        return false;
    }

    /**
     * Gets the scheduled end time for a user if any.
     * @param username The username
     * @return The scheduled end time or null if none
     */
    public LocalDateTime getScheduledEndTime(String username) {
        MonitoringState state = userMonitoringStates.get(username);
        return state != null ? state.getScheduledEndTime() : null;
    }

    //=============================================
    // Maintenance Methods
    //=============================================

    /**
     * Clears all monitoring state for a user.
     * Comprehensive method that ensures all state is properly reset.
     */
    public synchronized void clearUserState(String username) {
        // Clear main monitoring state
        userMonitoringStates.remove(username);

        // Clear notification counts
        notificationCountMap.remove(username);

        // Clear rate limiting
        lastNotificationTimes.entrySet().removeIf(entry -> entry.getKey().startsWith(username + "_"));

        // Cancel any scheduled tasks
        cancelScheduledEnd(username);

        LoggerUtil.info(this.getClass(), String.format("Cleared all monitoring state for user %s", username));
    }

    /**
     * Periodic state verification to detect and log inconsistencies.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void verifyConsistentState() {
        LoggerUtil.debug(this.getClass(), "Performing monitoring state verification");

        userMonitoringStates.forEach((username, state) -> {
            try {
                // Log current state for diagnostics
                LoggerUtil.debug(this.getClass(), String.format(
                        "Current monitoring state for %s: mode=%s, continuedAfterSchedule=%s, " +
                                "scheduleNotificationShown=%s, lastHourlyWarning=%s",
                        username, state.getMonitoringMode(),
                        state.isContinuedAfterSchedule(),
                        state.isScheduleNotificationShown(),
                        state.getLastHourlyWarning()));

                // Check for inconsistencies
                checkStateConsistency(username, state);

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(),
                        String.format("Error verifying state for user %s: %s",
                                username, e.getMessage()), e);
            }
        });
    }

    /**
     * Checks for and logs state inconsistencies.
     */
    private void checkStateConsistency(String username, MonitoringState state) {
        // Check hourly monitoring consistency
        if (MonitoringMode.HOURLY.equals(state.getMonitoringMode())) {
            if (!state.isContinuedAfterSchedule()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "State inconsistency for %s: In HOURLY mode but continuedAfterSchedule is false",
                        username));
            }
            if (!state.isScheduleNotificationShown()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "State inconsistency for %s: In HOURLY mode but scheduleNotificationShown is false",
                        username));
            }
        }

        // Check temp stop consistency
        if (MonitoringMode.TEMP_STOP.equals(state.getMonitoringMode()) && state.getTempStopStart() == null) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "State inconsistency for %s: In TEMP_STOP mode but tempStopStart is null",
                    username));
        }

        // Check for orphaned scheduled end times
        if (state.getScheduledEndTime() != null && !scheduledEndTasks.containsKey(username)) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "State inconsistency for %s: Has scheduledEndTime but no scheduled task",
                    username));
        }
    }

    //=============================================
    // Helper Methods
    //=============================================

    /**
     * Ensures that a monitoring state exists for a user.
     */
    private void ensureStateExists(String username) {
        userMonitoringStates.computeIfAbsent(username, k -> new MonitoringState());
    }

    /**
     * Ensures that a notification count map exists for a user.
     */
    private void ensureNotificationCountMap(String username) {
        notificationCountMap.computeIfAbsent(username, k -> new ConcurrentHashMap<>());
    }

    /**
     * Gets a unique key for a notification based on username and type.
     */
    private String getNotificationKey(String username, String notificationType) {
        return username + "_" + notificationType;
    }

    /**
     * Logs a state transition for a user.
     */
    private void logStateTransition(String username, String fromState, String toState) {
        if (!fromState.equals(toState)) {
            LoggerUtil.info(this.getClass(), String.format(
                    "Monitoring state transition for %s: %s → %s",
                    username, fromState, toState));
        }
    }
}