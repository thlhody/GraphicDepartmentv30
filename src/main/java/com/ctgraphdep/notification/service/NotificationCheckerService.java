package com.ctgraphdep.notification.service;

import com.ctgraphdep.calculations.CalculationCommandFactory;
import com.ctgraphdep.calculations.CalculationCommandService;
import com.ctgraphdep.calculations.queries.CalculateMinutesBetweenQuery;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.session.query.WorkScheduleQuery;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.query.SessionStatusQuery;
import com.ctgraphdep.session.query.WorktimeResolutionQuery;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.IsWeekdayCommand;
import com.ctgraphdep.validation.commands.IsWorkingHoursCommand;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service that periodically checks for conditions that require notifications
 * and triggers them independently of the SessionMonitorService.
 */
@Service
public class NotificationCheckerService {

    private final NotificationService notificationService;
    private final NotificationMonitorService monitorService;
    private final CalculationCommandService calculationService;
    private final CalculationCommandFactory calculationFactory;
    private final SessionCommandService sessionCommandService;
    private final SessionCommandFactory sessionCommandFactory;
    private final TimeValidationService timeValidationService;
    private final DataAccessService dataAccessService;
    private final SchedulerHealthMonitor healthMonitor;

    // Flag to indicate if the service is initialized
    private volatile boolean isInitialized = false;

    public NotificationCheckerService(
            NotificationService notificationService,
            NotificationMonitorService monitorService,
            CalculationCommandService calculationService,
            CalculationCommandFactory calculationFactory,
            @Lazy SessionCommandService sessionCommandService,
            @Lazy SessionCommandFactory sessionCommandFactory,
            TimeValidationService timeValidationService,
            DataAccessService dataAccessService,
            SchedulerHealthMonitor healthMonitor) {

        this.notificationService = notificationService;
        this.monitorService = monitorService;
        this.calculationService = calculationService;
        this.calculationFactory = calculationFactory;
        this.sessionCommandService = sessionCommandService;
        this.sessionCommandFactory = sessionCommandFactory;
        this.timeValidationService = timeValidationService;
        this.dataAccessService = dataAccessService;
        this.healthMonitor = healthMonitor;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        // Register with health monitor
        healthMonitor.registerTask(
                "notification-checker",
                2, // Expected to run every 2 minutes
                status -> {
                    // Recovery action: reset service if unhealthy
                    LoggerUtil.warn(this.getClass(), "Attempting to recover notification checker service");
                    resetService();
                }
        );

        // Schedule initialization with a delay to ensure other services are ready
        new Thread(() -> {
            try {
                // Sleep for 10 seconds to allow other services to initialize
                Thread.sleep(10000);
                isInitialized = true;

                // Record initial execution
                healthMonitor.recordTaskExecution("notification-checker");
                LoggerUtil.info(this.getClass(), "Notification checker service initialized");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LoggerUtil.error(this.getClass(), "Interrupted during initialization", e);
            }
        }).start();
    }

    /**
     * Main check method that runs every 2 minutes
     * This is more frequent than the session monitor (which runs every 5/30 minutes)
     * and allows notifications to be shown more promptly
     */
    @Scheduled(fixedRate = 120000) // Run every 2 minutes
    public void checkForNotifications() {
        if (!isInitialized) {
            return;
        }

        try {
            // Record execution
            healthMonitor.recordTaskExecution("notification-checker");

            // NEW CODE: Quick pre-check for weekend or non-working hours
            IsWeekdayCommand weekdayCommand = timeValidationService.getValidationFactory().createIsWeekdayCommand();
            IsWorkingHoursCommand workingHoursCommand = timeValidationService.getValidationFactory().createIsWorkingHoursCommand();

            boolean isWeekday = timeValidationService.execute(weekdayCommand);
            boolean isWorkingHours = timeValidationService.execute(workingHoursCommand);

            // If it's not a weekday or not working hours, just log and exit early
            if (!isWeekday || !isWorkingHours) {
                // Calculate time until next business hours check
                GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
                GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);

                LocalDateTime now = timeValues.getCurrentTime();
                LocalDateTime nextCheck;

                if (!isWeekday) {
                    // If weekend, calculate time until Monday 5 AM
                    int daysUntilMonday = 8 - now.getDayOfWeek().getValue(); // Monday is 1, so 8-day = days until next Monday
                    if (daysUntilMonday > 7) daysUntilMonday -= 7; // Make sure we're getting the next Monday

                    nextCheck = now.plusDays(daysUntilMonday)
                            .withHour(WorkCode.WORK_START_HOUR)
                            .withMinute(0)
                            .withSecond(0);

                    LoggerUtil.debug(this.getClass(), String.format("Weekend detected, skipping notification checks until %s", nextCheck.format(DateTimeFormatter.ISO_DATE_TIME)));
                } else {
                    // Not working hours but weekday - calculate until next working hours start
                    if (now.getHour() < WorkCode.WORK_START_HOUR) {
                        // Before start of day - wait until 5 AM
                        nextCheck = now.withHour(WorkCode.WORK_START_HOUR)
                                .withMinute(0)
                                .withSecond(0);
                    } else {
                        // After end of day - wait until tomorrow 5 AM
                        nextCheck = now.plusDays(1)
                                .withHour(WorkCode.WORK_START_HOUR)
                                .withMinute(0)
                                .withSecond(0);
                    }

                    LoggerUtil.debug(this.getClass(),
                            String.format("Outside working hours, skipping notification checks until %s",
                                    nextCheck.format(DateTimeFormatter.ISO_DATE_TIME)));
                }

                return;
            }


            // Get the current active user
            User user = getCurrentActiveUser();
            if (user == null) {
                return;
            }

            String username = user.getUsername();
            Integer userId = user.getUserId();

            LoggerUtil.debug(this.getClass(), String.format("Checking notifications for user %s", username));

            // Always check for resolution reminder first (highest priority)
            checkForResolutionReminder(username, userId);

            // Next check start day reminder if necessary
            checkForStartDayReminder(username, userId);

            // Get current session and skip if null
            WorkUsersSessionsStates session = dataAccessService.readLocalSessionFile(username, userId);
            if (session == null) {
                return;
            }

            // Check various notification types based on session state
            if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                // Online session - check for schedule completion and hourly warnings
                checkScheduleCompletion(session, user);
                checkHourlyWarning(session);

            } else if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                // Temporary stop - check for temp stop notifications
                checkTempStopDuration(session);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking for notifications: " + e.getMessage(), e);
            healthMonitor.recordTaskFailure("notification-checker", e.getMessage());
        }
    }

    /**
     * Checks if worktime resolution reminder should be shown
     */
    private void checkForResolutionReminder(String username, Integer userId) {
        try {
            // Check for unresolved worktime entries
            WorktimeResolutionQuery resolutionQuery = sessionCommandFactory.createWorktimeResolutionQuery(username);
            WorktimeResolutionQuery.ResolutionStatus resolutionStatus = sessionCommandService.executeQuery(resolutionQuery);

            // If there are unresolved entries, show resolution notification
            if (resolutionStatus.isNeedsResolution()) {
                // Only show if rate limiting allows
                if (notificationService.canShowNotification(username, WorkCode.RESOLUTION_REMINDER_TYPE, WorkCode.CHECK_INTERVAL)) {
                    LoggerUtil.info(this.getClass(), String.format("User %s has unresolved worktime entries - showing resolution notification", username));

                    // Show resolution notification
                    notificationService.showResolutionReminder(
                            username,
                            userId,
                            WorkCode.RESOLUTION_TITLE,
                            WorkCode.RESOLUTION_MESSAGE,
                            WorkCode.RESOLUTION_MESSAGE_TRAY,
                            WorkCode.ON_FOR_TWELVE_HOURS
                    );
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking resolution reminder for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Checks if a start day reminder should be shown
     */
    private void checkForStartDayReminder(String username, Integer userId) {
        try {

            // Check if notification can be shown (rate limiting)
            if (!notificationService.canShowNotification(username, WorkCode.START_DAY_TYPE, WorkCode.ONCE_PER_DAY_TIMER)) {
                return;
            }

            // Check session status
            SessionStatusQuery statusQuery = sessionCommandFactory.createSessionStatusQuery(username, userId);
            SessionStatusQuery.SessionStatus status = sessionCommandService.executeQuery(statusQuery);

            // If session is active (online or in temporary stop), don't show start day reminder
            if (status.isOnline() || status.isInTemporaryStop()) {
                return;
            }

            // Check if user has completed a session today
            if (status.isHasCompletedSessionToday()) {
                return;
            }

            // Show start day reminder
            LoggerUtil.info(this.getClass(), String.format("User %s has offline session - showing start day reminder", username));

            notificationService.showStartDayReminder(username, userId);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking start day reminder for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Checks if schedule completion notification should be shown
     */
    private void checkScheduleCompletion(WorkUsersSessionsStates session, User user) {
        // Check if notification has already been shown
        if (monitorService.wasScheduleNotificationShownToday(session.getUsername())) {
            return;
        }

        try {
            // Use WorkScheduleQuery to get schedule info
            WorkScheduleQuery query = sessionCommandFactory.createWorkScheduleQuery(session.getDayStartTime().toLocalDate(), user.getSchedule());
            WorkScheduleQuery.ScheduleInfo scheduleInfo = sessionCommandService.executeQuery(query);

            int workedMinutes = session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0;

            // Only show notification if not already shown for this session and schedule is completed
            if (scheduleInfo.isScheduleCompleted(workedMinutes)) {
                notificationService.showScheduleEndNotification(session.getUsername(), session.getUserId(), session.getFinalWorkedMinutes());
                LoggerUtil.info(this.getClass(), String.format("Schedule completion notification shown for user %s (worked: %d minutes)", session.getUsername(), workedMinutes));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking schedule completion for user %s: %s", session.getUsername(), e.getMessage()), e);
        }
    }

    /**
     * Checks if hourly warning should be shown
     */
    private void checkHourlyWarning(WorkUsersSessionsStates session) {
        String username = session.getUsername();

        // Only check if user has already continued after schedule
        if (!monitorService.isHourlyNotificationDue(username, LocalDateTime.now())) {
            return;
        }

        try {
            // Check if rate limiting allows showing hourly notification
            if (!notificationService.canShowNotification(username, WorkCode.HOURLY_TYPE, WorkCode.HOURLY_INTERVAL)) {
                return;
            }

            // Show hourly warning notification
            notificationService.showHourlyWarning(username, session.getUserId(), session.getFinalWorkedMinutes());
            LoggerUtil.info(this.getClass(), String.format("Hourly warning notification shown for user %s", username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking hourly warning for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Checks if temporary stop duration warning should be shown
     */
    private void checkTempStopDuration(WorkUsersSessionsStates session) {
        String username = session.getUsername();
        LocalDateTime tempStopStart = session.getLastTemporaryStopTime();

        if (tempStopStart == null) {
            return;
        }

        try {
            // Calculate temporary stop duration
            CalculateMinutesBetweenQuery minutesQuery = calculationFactory.createCalculateMinutesBetweenQuery(tempStopStart, getStandardCurrentTime());
            int minutesSinceTempStop = calculationService.executeQuery(minutesQuery);

            // Check if temporary stop notification should be shown
            if (monitorService.isTempStopNotificationDue(username, minutesSinceTempStop, getStandardCurrentTime())) {

                // Show temporary stop warning
                notificationService.showTempStopWarning(username, session.getUserId(), tempStopStart);
                LoggerUtil.info(this.getClass(), String.format("Temp stop warning notification shown for user %s (%d minutes)", username, minutesSinceTempStop));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking temp stop duration for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Gets the currently active user from local users file
     */
    private User getCurrentActiveUser() {
        try {
            // Get the list of local users from data access service
            List<User> localUsers = dataAccessService.readLocalUser();

            // Return the first user (since it's a single-user application)
            if (!localUsers.isEmpty()) {
                return localUsers.get(0);
            }

            LoggerUtil.warn(this.getClass(), "No local user found");
            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting current user: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Resets the service state (for health monitor recovery)
     */

    private void resetService() {
        try {
            LoggerUtil.info(this.getClass(), "Resetting notification checker service");

            // Re-initialize the service
            isInitialized = true;

            // Record successful reset
            healthMonitor.recordTaskExecution("notification-checker");

            LoggerUtil.info(this.getClass(), "Notification checker service reset completed");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error resetting notification checker service: " + e.getMessage());
            healthMonitor.recordTaskFailure("notification-checker", e.getMessage());
        }
    }

    private LocalDateTime getStandardCurrentTime() {
        // Get standardized time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        return timeValues.getCurrentTime();
    }
}