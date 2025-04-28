package com.ctgraphdep.notification.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.api.NotificationEventSubscriber;
import com.ctgraphdep.notification.events.*;
import com.ctgraphdep.notification.model.NotificationRequest;
import com.ctgraphdep.notification.model.NotificationResponse;
import com.ctgraphdep.notification.model.NotificationType;
import com.ctgraphdep.notification.ui.ButtonFactory;
import com.ctgraphdep.notification.ui.DialogComponents;
import com.ctgraphdep.notification.ui.NotificationBackgroundFactory;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.notification.*;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;
import com.ctgraphdep.tray.CTTTSystemTray;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for displaying notification dialogs and managing UI presentation.
 * It handles both the UI display of notifications and tracking user responses.
 */
@Service
public class NotificationDisplayService implements NotificationEventSubscriber {

    private final CTTTSystemTray systemTray;
    private final AtomicBoolean userResponded = new AtomicBoolean(false);
    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final TimeValidationService timeValidationService;
    private final NotificationMonitorService monitorService;
    private final NotificationBackupService backupService;
    private final SchedulerHealthMonitor healthMonitor;
    private final DataAccessService dataAccessService;

    private final Map<String, JDialog> activeDialogs = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    private static final int NOTIFICATION_WIDTH = 600;
    private static final int NOTIFICATION_HEIGHT = 400;
    private static final int BUTTONS_PANEL_HEIGHT = 50;
    private static final int BUTTON_SPACING = 20;

    // Constructor with lazy-loaded dependencies
    public NotificationDisplayService(CTTTSystemTray systemTray,
                                      @Lazy SessionCommandService commandService, @Lazy SessionCommandFactory commandFactory, TimeValidationService timeValidationService,
                                      NotificationMonitorService monitorService, NotificationBackupService backupService,
                                      SchedulerHealthMonitor healthMonitor, DataAccessService dataAccessService) {

        this.systemTray = systemTray;
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.timeValidationService = timeValidationService;
        this.monitorService = monitorService;
        this.backupService = backupService;
        this.healthMonitor = healthMonitor;
        this.dataAccessService = dataAccessService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        // Register with health monitor for automatic recovery if needed
        healthMonitor.registerTask(
                "notification-display-service",
                5, // Expected to run heartbeat every 5 minutes
                status -> {
                    // Recovery action - clean up any stale resources
                    LoggerUtil.warn(this.getClass(), "Attempting to recover unhealthy notification display service");
                    resetService();
                }
        );

        // Record initial execution
        healthMonitor.recordTaskExecution("notification-display-service");
        LoggerUtil.info(this.getClass(), "Notification display service initialized");
    }

    @Override
    public void onNotificationEvent(NotificationEvent event) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Received notification event: %s for user: %s", event.getClass().getSimpleName(), event.getUsername()));

            // Handle based on event type
            if (event instanceof ScheduleEndEvent) {
                showScheduleEndNotification((ScheduleEndEvent) event);
            } else if (event instanceof HourlyWarningEvent) {
                showHourlyWarningNotification((HourlyWarningEvent) event);
            } else if (event instanceof TempStopWarningEvent) {
                showTempStopWarningNotification((TempStopWarningEvent) event);
            } else if (event instanceof StartDayReminderEvent) {
                showStartDayReminderNotification((StartDayReminderEvent) event);
            } else if (event instanceof ResolutionReminderEvent) {
                showResolutionReminderNotification((ResolutionReminderEvent) event);
            } else if (event instanceof TestNotificationEvent) {
                showTestNotification((TestNotificationEvent) event);
            } else {
                LoggerUtil.warn(this.getClass(), "Unhandled notification event type: " + event.getClass().getName());
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error handling notification event %s: %s", event.getClass().getSimpleName(), e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-display-service", e.getMessage());
        }
    }

    @Override
    public boolean canHandle(Class<? extends NotificationEvent> eventType) {
        // This subscriber can handle all notification event types
        return NotificationEvent.class.isAssignableFrom(eventType);
    }

    /**
     * Shows a notification using a request model
     */
    public NotificationResponse showNotification(NotificationRequest request) {
        String notificationId = UUID.randomUUID().toString();

        try {
            LoggerUtil.info(this.getClass(), String.format("Showing notification: %s, Type: %s, User: %s", notificationId, request.getType(), request.getUsername()));

            // Record health check execution
            healthMonitor.recordTaskExecution("notification-display-service");

            // Reset the userResponded flag before showing new notification
            userResponded.set(false);

            // Always use invokeLater to avoid blocking and potential deadlocks
            final boolean[] dialogDisplayed = new boolean[1];
            final boolean[] trayDisplayed = new boolean[1];

            String existingDialogKey = findExistingDialogKey(request.getUsername(), request.getType().getTypeId());
            if (existingDialogKey != null) {
                JDialog existingDialog = activeDialogs.get(existingDialogKey);
                if (existingDialog != null && existingDialog.isDisplayable()) {
                    // If dialog exists but isn't visible, make it visible again
                    if (!existingDialog.isVisible()) {
                        existingDialog.setVisible(true);
                        existingDialog.toFront();
                        existingDialog.requestFocus();
                        LoggerUtil.info(this.getClass(),
                                String.format("Restoring existing %s notification for %s instead of creating new one", request.getType().getTypeId(), request.getUsername()));
                    } else {
                        LoggerUtil.debug(this.getClass(), String.format("Notification %s already active for %s, not creating duplicate", request.getType().getTypeId(), request.getUsername()));
                    }
                    // Return a success response for the existing dialog
                    return NotificationResponse.success(existingDialogKey, true, false);
                }
            }

            SwingUtilities.invokeLater(() -> {
                try {
                    // Check if we're on the EDT now
                    LoggerUtil.debug(this.getClass(), "UI thread: " + Thread.currentThread().getName() + ", is EDT: " + SwingUtilities.isEventDispatchThread());

                    // Try to show dialog first
                    dialogDisplayed[0] = showNotificationDialog(request, notificationId);

                    // ONLY attempt tray notification if dialog fails
                    if (!dialogDisplayed[0] && systemTray.getTrayIcon() != null) {
                        LoggerUtil.info(this.getClass(), "Dialog display failed, attempting to display tray notification for user: " + request.getUsername());
                        systemTray.getTrayIcon().displayMessage(request.getTitle(), request.getTrayMessage(), TrayIcon.MessageType.INFO);
                        trayDisplayed[0] = true;
                        LoggerUtil.info(this.getClass(), "Tray notification displayed as fallback");

                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error showing notification: " + e.getMessage(), e);
                    healthMonitor.recordTaskFailure("notification-display-service", e.getMessage());
                }
            });

            // Wait a short time for the notification to display
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Return success if either dialog or tray was displayed
            if (dialogDisplayed[0] || trayDisplayed[0]) {
                return NotificationResponse.success(notificationId, dialogDisplayed[0], trayDisplayed[0]);
            } else {
                return NotificationResponse.failure("Failed to display notification");
            }



        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in showNotification: " + e.getMessage(), e);
            healthMonitor.recordTaskFailure("notification-display-service", e.getMessage());
            return NotificationResponse.failure(e.getMessage());
        }
    }

    private String findExistingDialogKey(String username, String notificationType) {
        for (Map.Entry<String, JDialog> entry : activeDialogs.entrySet()) {
            JDialog dialog = entry.getValue();
            if (dialog.isDisplayable()) {
                Object dialogUsername = dialog.getRootPane().getClientProperty("username");
                Object dialogType = dialog.getRootPane().getClientProperty("notificationType");

                if (username.equals(dialogUsername) && notificationType.equals(dialogType)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }


    // Notification-specific display methods

    private void showScheduleEndNotification(ScheduleEndEvent event) {
        try {
            // First register backup action
            backupService.registerScheduleEndNotification(event.getUsername(), event.getUserId());

            // Create notification request
            NotificationRequest request = NotificationRequest.builder(NotificationType.SCHEDULE_END, event.getUsername(), event.getUserId())
                    .finalMinutes(event.getFinalMinutes())
                    .title(WorkCode.END_SCHEDULE_TITLE)
                    .message(WorkCode.SESSION_WARNING_MESSAGE)
                    .trayMessage(WorkCode.SESSION_WARNING_TRAY)
                    .timeoutPeriod(event.getTimeoutPeriod())
                    .priority(event.getPriority())
                    .build();

            // Show notification
            NotificationResponse response = showNotification(request);

            if (response.isSuccess()) {
                // Record notification display
                monitorService.markScheduleNotificationShown(event.getUsername());
                monitorService.recordNotificationTime(event.getUsername(), event.getNotificationType());

                // Track notification display for backup service
                TrackNotificationDisplayCommand command = commandFactory.createTrackNotificationDisplayCommand(event.getUsername(), event.getUserId(), false);
                commandService.executeCommand(command);

                LoggerUtil.info(this.getClass(), String.format("Schedule end notification shown for user %s", event.getUsername()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Failed to show schedule end notification for user %s: %s", event.getUsername(), response.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing schedule end notification for user %s: %s", event.getUsername(), e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-display-service", e.getMessage());
        }
    }

    private void showHourlyWarningNotification(HourlyWarningEvent event) {
        try {
            // First register backup action
            backupService.registerHourlyWarningNotification(event.getUsername(), event.getUserId());

            // Create notification request
            NotificationRequest request = NotificationRequest.builder(NotificationType.HOURLY_WARNING, event.getUsername(), event.getUserId())
                    .finalMinutes(event.getFinalMinutes())
                    .title(WorkCode.OVERTIME_TITLE)
                    .message(WorkCode.HOURLY_WARNING_MESSAGE)
                    .trayMessage(WorkCode.HOURLY_WARNING_TRAY)
                    .timeoutPeriod(event.getTimeoutPeriod())
                    .priority(event.getPriority())
                    .build();

            // Show notification
            NotificationResponse response = showNotification(request);

            if (response.isSuccess()) {
                // Record notification
                monitorService.recordHourlyNotification(event.getUsername(), getStandardCurrentTime());
                monitorService.recordNotificationTime(event.getUsername(), event.getNotificationType());

                // Track notification display for backup service
                TrackNotificationDisplayCommand command = commandFactory.createTrackNotificationDisplayCommand(event.getUsername(), event.getUserId(), false);
                commandService.executeCommand(command);
                LoggerUtil.info(this.getClass(), String.format("Hourly warning notification shown for user %s", event.getUsername()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Failed to show hourly warning notification for user %s: %s", event.getUsername(), response.getErrorMessage()));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing hourly warning notification for user %s: %s", event.getUsername(), e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-display-service", e.getMessage());
        }
    }

    private void showTempStopWarningNotification(TempStopWarningEvent event) {
        try {
            // First register backup action
            backupService.registerTempStopNotification(event.getUsername(), event.getUserId(), event.getTempStopStart());

            // Calculate minutes in temporary stop for the message
            int minutesSinceTempStop = (int) ChronoUnit.MINUTES.between(event.getTempStopStart(), getStandardCurrentTime());
            int hours = minutesSinceTempStop / 60;
            int minutes = minutesSinceTempStop % 60;

            // Format messages with the calculated duration
            String formattedMessage = String.format(WorkCode.LONG_TEMP_STOP_WARNING, hours, minutes);
            String trayMessage = String.format(WorkCode.LONG_TEMP_STOP_WARNING_TRAY, hours, minutes);

            // Create notification request
            NotificationRequest request = NotificationRequest.builder(NotificationType.TEMP_STOP, event.getUsername(), event.getUserId())
                    .tempStopStart(event.getTempStopStart())
                    .title(WorkCode.TEMPORARY_STOP_TITLE)
                    .message(formattedMessage)
                    .trayMessage(trayMessage)
                    .timeoutPeriod(event.getTimeoutPeriod())
                    .priority(event.getPriority())
                    .build();

            // Show notification
            NotificationResponse response = showNotification(request);

            if (response.isSuccess()) {
                // Record notification
                monitorService.recordTempStopNotification(event.getUsername(), getStandardCurrentTime());
                monitorService.recordNotificationTime(event.getUsername(), event.getNotificationType());

                // Track notification display for backup service
                TrackNotificationDisplayCommand command = commandFactory.createTrackNotificationDisplayCommand(event.getUsername(), event.getUserId(), true);
                commandService.executeCommand(command);

                LoggerUtil.info(this.getClass(), String.format("Temp stop warning notification shown for user %s", event.getUsername()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Failed to show temp stop warning notification for user %s: %s", event.getUsername(), response.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing temp stop warning notification for user %s: %s", event.getUsername(), e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-display-service", e.getMessage());
        }
    }

    private void showStartDayReminderNotification(StartDayReminderEvent event) {
        try {
            // Create notification request
            NotificationRequest request = NotificationRequest.builder(NotificationType.START_DAY, event.getUsername(), event.getUserId())
                    .title(WorkCode.START_DAY_TITLE)
                    .message(WorkCode.START_DAY_MESSAGE)
                    .trayMessage(WorkCode.START_DAY_MESSAGE_TRAY)
                    .timeoutPeriod(event.getTimeoutPeriod())
                    .priority(event.getPriority())
                    .build();

            // Show notification
            NotificationResponse response = showNotification(request);

            if (response.isSuccess()) {
                // Record notification time
                monitorService.recordNotificationTime(event.getUsername(), event.getNotificationType());
                LoggerUtil.info(this.getClass(), String.format("Start day reminder notification shown for user %s", event.getUsername()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Failed to show start day reminder notification for user %s: %s", event.getUsername(), response.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing start day reminder notification for user %s: %s", event.getUsername(), e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-display-service", e.getMessage());
        }
    }

    private void showResolutionReminderNotification(ResolutionReminderEvent event) {
        try {
            // Create notification request
            NotificationRequest request = NotificationRequest.builder(NotificationType.RESOLUTION, event.getUsername(), event.getUserId())
                    .title(event.getTitle())
                    .message(event.getMessage())
                    .trayMessage(event.getTrayMessage())
                    .timeoutPeriod(event.getTimeoutPeriod())
                    .priority(event.getPriority())
                    .build();

            // Show notification
            NotificationResponse response = showNotification(request);

            if (response.isSuccess()) {
                // Record notification time
                monitorService.recordNotificationTime(event.getUsername(), event.getNotificationType());
                LoggerUtil.info(this.getClass(), String.format("Resolution reminder notification shown for user %s", event.getUsername()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Failed to show resolution reminder notification for user %s: %s", event.getUsername(), response.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing resolution reminder notification for user %s: %s", event.getUsername(), e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-display-service", e.getMessage());
        }
    }

    private void showTestNotification(TestNotificationEvent event) {
        try {
            LoggerUtil.info(this.getClass(), "Attempting to show test notification for user: " + event.getUsername());

            // Use the standard notification flag for synchronization
            final boolean[] dialogDisplayed = new boolean[1];
            final boolean[] trayDisplayed = new boolean[1];

            SwingUtilities.invokeLater(() -> {
                try {
                    // Try to create and show dialog first
                    DialogComponents components = createDialog(WorkCode.TEST_NOTICE_TITLE, WorkCode.TEST_MESSAGE);

                    // Add test-specific buttons
                    addTestButtons(components, userResponded);

                    try {
                        showDialog(components.dialog(), null, null);
                        dialogDisplayed[0] = true;

                        // Store dialog for cleanup
                        activeDialogs.put("test_" + event.getUsername(), components.dialog());

                        LoggerUtil.info(this.getClass(), "Test dialog displayed successfully");

                        // Add auto-close timer
                        setupAutoCloseTimer(components.dialog(), event.getUsername(), event.getTimeoutPeriod(), event.getNotificationType(), userResponded);

                    } catch (Exception e) {
                        LoggerUtil.error(this.getClass(), "Failed to display test dialog: " + e.getMessage());
                        dialogDisplayed[0] = false;

                        // ONLY attempt tray notification if dialog fails
                        if (systemTray.getTrayIcon() != null) {
                            LoggerUtil.info(this.getClass(), "Attempting to display test tray notification as fallback");
                            systemTray.getTrayIcon().displayMessage(WorkCode.TEST_NOTICE_TITLE, WorkCode.TEST_MESSAGE_TRAY, TrayIcon.MessageType.INFO);
                            trayDisplayed[0] = true;
                            LoggerUtil.info(this.getClass(), "Test tray notification displayed as fallback");
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error preparing test notification: " + e.getMessage());
                }
            });

            // Record notification
            monitorService.recordNotificationTime(event.getUsername(), event.getNotificationType());
            LoggerUtil.debug(this.getClass(), "Normal dialog displayed:" + Arrays.toString(dialogDisplayed) + ".Tray dialog displayed:" + Arrays.toString(trayDisplayed));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing test notification for user %s: %s", event.getUsername(), e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-display-service", e.getMessage());
        }
    }

    // Core display methods

    /**
     * Shows a notification dialog based on the request
     */
    private boolean showNotificationDialog(NotificationRequest request, String notificationId) {
        // Sanity check - make sure we're on EDT
        if (!SwingUtilities.isEventDispatchThread()) {
            LoggerUtil.error(this.getClass(), "showNotificationDialog called from non-EDT thread!");
            return false;
        }

        // Guard clauses
        if (systemTray.getTrayIcon() == null) {
            LoggerUtil.error(this.getClass(), "System tray icon not available");
            return false;
        }

        if (GraphicsEnvironment.isHeadless()) {
            LoggerUtil.info(this.getClass(), "Running in headless mode, can't display dialog");
            return false;
        }

        // For temp stop notifications, we don't need additional validation
        boolean isTempStop = request.getType() == com.ctgraphdep.notification.model.NotificationType.TEMP_STOP;

        // Don't show regular notification during temp stop
        if (!isTempStop) {
            // Get current session using GetCurrentSessionQuery
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(request.getUsername(), request.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // Check if session is in temporary stop state
            boolean isInTempStop = session != null && WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus());

            if (isInTempStop) {
                LoggerUtil.debug(this.getClass(), "Skipping regular notification during temp stop");
                return false;
            }
        }

        userResponded.set(false);
        boolean dialogDisplayed;

        try {
            // Create the dialog
            DialogComponents components = createDialog(request.getTitle(), request.getMessage());

            // Add buttons based on notification type
            addButtonsForNotificationType(components, request);

            // Show the dialog
            showDialog(components.dialog(), request.getUsername(), request.getType().getTypeId());
            dialogDisplayed = true;

            // Store dialog for cleanup
            activeDialogs.put(notificationId, components.dialog());

            // Set up auto-close timer
            setupAutoCloseTimer(components.dialog(), request.getUsername(), request.getTimeoutPeriod(), request.getType().getTypeId(), userResponded);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to display notification dialog: " + e.getMessage(), e);
            dialogDisplayed = false;
        }

        return dialogDisplayed;
    }

    /**
     * Creates a dialog with the specified title and message
     */
    private DialogComponents createDialog(String title, String message) {
        BufferedImage notificationImage = NotificationBackgroundFactory.createNotificationBackground(title, message);

        JDialog dialog = new JDialog();
        dialog.setUndecorated(true);
        dialog.setAlwaysOnTop(true);
        dialog.setType(Window.Type.UTILITY);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setLayout(new BorderLayout());

        JPanel contentPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.drawImage(notificationImage, 0, 0, this);
            }

            @Override
            public boolean isOpaque() {
                return false;
            }
        };

        contentPanel.setPreferredSize(new Dimension(NOTIFICATION_WIDTH, NOTIFICATION_HEIGHT - BUTTONS_PANEL_HEIGHT));

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setOpaque(false);
        buttonsPanel.setPreferredSize(new Dimension(NOTIFICATION_WIDTH, BUTTONS_PANEL_HEIGHT));
        buttonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, BUTTON_SPACING, 10));

        dialog.add(contentPanel, BorderLayout.CENTER);
        dialog.add(buttonsPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setShape(new RoundRectangle2D.Double(0, 0, dialog.getWidth(), dialog.getHeight(), 20, 20));

        return new DialogComponents(dialog, buttonsPanel);
    }

    /**
     * Positions and displays the dialog on screen
     */
    private void showDialog(JDialog dialog, String username, String notificationType) {
        dialog.setBackground(new Color(0, 0, 0, 0));

        // Store username and notification type as client properties for later reference
        dialog.getRootPane().putClientProperty("username", username);
        dialog.getRootPane().putClientProperty("notificationType", notificationType);
        dialog.getRootPane().putClientProperty("creationTime", System.currentTimeMillis());


        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation(
                screenSize.width - dialog.getWidth() - 20,
                screenSize.height - dialog.getHeight() - 50
        );

        dialog.setVisible(true);
        dialog.toFront();
        // Force dialog to be on top of all windows
        dialog.setAlwaysOnTop(true);

        // Add visual confirmation of dialog display
        LoggerUtil.debug(this.getClass(), String.format("Dialog shown at position X: %d, Y: %d",
                screenSize.width - dialog.getWidth() - 20,
                screenSize.height - dialog.getHeight() - 50));

        // Add check to verify dialog is visible on screen
        if (!dialog.isShowing()) {
            LoggerUtil.error(this.getClass(), "Dialog set to visible but not showing on screen");
        }
    }

    /**
     * Enhances notification dialog visibility for long-duration notifications
     * This method keeps a dialog visible during business hours
     */
    private void enhanceLongDurationVisibility(JDialog dialog, String username, String notificationType) {
        // Only apply to START_DAY_TYPE and RESOLUTION_REMINDER_TYPE
        if (!WorkCode.START_DAY_TYPE.equals(notificationType) &&
                !WorkCode.RESOLUTION_REMINDER_TYPE.equals(notificationType)) {
            return;
        }

        // Create a background task to periodically check and maintain dialog visibility
        Timer visibilityTimer = new Timer(30000, e -> { // Check every 30 seconds (increased frequency from 60s)
            try {
                // Only proceed if dialog still exists
                if (dialog == null || !dialog.isDisplayable()) {
                    return;
                }

                LocalDateTime currentTime = getStandardCurrentTime();
                int hour = currentTime.getHour();
                int dayOfWeek = currentTime.getDayOfWeek().getValue();

                // Check if within business hours (5-17) and weekday (1-5)
                boolean withinBusinessHours = hour >= WorkCode.WORK_START_HOUR &&
                        hour < WorkCode.WORK_END_HOUR;
                boolean isWeekday = dayOfWeek >= 1 && dayOfWeek <= 5;

                if (withinBusinessHours && isWeekday) {
                    // FIXED: More aggressive visibility enforcement

                    // Ensure dialog is visible
                    if (!dialog.isVisible()) {
                        dialog.setVisible(true);
                        LoggerUtil.info(this.getClass(),
                                String.format("Restoring visibility for %s notification: %s",
                                        notificationType, username));
                    }

                    // Always bring to front with more forceful approach
                    dialog.toFront();
                    dialog.setAlwaysOnTop(true);

                    // FIXED: Request focus to make notification more noticeable
                    dialog.requestFocus();

                    // Force repaint
                    dialog.repaint();

                    // Log visibility check at info level to track this issue
                    LoggerUtil.info(this.getClass(),
                            String.format("Enhanced visibility maintenance for %s notification: %s",
                                    notificationType, username));
                } else {
                    // Outside business hours - hide notification until next business hour
                    if (dialog.isVisible()) {
                        LoggerUtil.info(this.getClass(),
                                String.format("Outside business hours (%d:00) - hiding %s notification for %s",
                                        hour, notificationType, username));
                        dialog.setVisible(false);
                    }
                }
            } catch (Exception ex) {
                LoggerUtil.error(this.getClass(),
                        String.format("Error in visibility enhancement for %s: %s",
                                username, ex.getMessage()), ex);
            }
        });

        // FIXED: Mark dialog as persistent to prevent cleanup
        dialog.getRootPane().putClientProperty("isPersistentNotification", Boolean.TRUE);

        visibilityTimer.setRepeats(true);
        visibilityTimer.start();

        // Store timer reference for cleanup
        String timerKey = username + "_visibility_" + notificationType;
        timers.put(timerKey, visibilityTimer);

        LoggerUtil.info(this.getClass(), String.format("Enhanced visibility enabled for %s notification (%s)", notificationType, username));
    }


    /**
     * Sets up an auto-close timer for a notification dialog
     */
    private void setupAutoCloseTimer(JDialog dialog, String username, int timeoutPeriod,
                                     String notificationType, AtomicBoolean respondedFlag) {

        if (timeoutPeriod == WorkCode.ON_FOR_TWELVE_HOURS) {
            // Setup enhanced visibility for long-duration notifications
            enhanceLongDurationVisibility(dialog, username, notificationType);

            // Log that we're setting up enhanced visibility mode
            LoggerUtil.info(this.getClass(),
                    String.format("Long-duration notification detected (%s) - enabling enhanced visibility",
                            notificationType));
        }

        if (timeoutPeriod <= 0) {
            LoggerUtil.debug(this.getClass(), "No auto-close timer needed for " + username + " (timeoutPeriod: " + timeoutPeriod + ")");
            return; // No timer needed
        }

        // Get current notification count
        int currentCount = monitorService.getNotificationCount(username, notificationType);

        // Max notification count (stop after 8 notifications)
        final int MAX_NOTIFICATION_COUNT = 8;

        Timer autoCloseTimer = new Timer(timeoutPeriod, e -> {
            if (!respondedFlag.get() && dialog.isDisplayable()) {
                LoggerUtil.info(this.getClass(), String.format("Auto-dismissing %s notification for %s after timeout (%d ms), count: %d/%d",
                        notificationType, username, timeoutPeriod, currentCount + 1, MAX_NOTIFICATION_COUNT));

                try {
                    // Check if we've reached the max notification count
                    if (currentCount >= MAX_NOTIFICATION_COUNT - 1) {
                        LoggerUtil.info(this.getClass(), String.format("Reached maximum notification count (%d) for %s - stopping notification cycle", MAX_NOTIFICATION_COUNT, username));

                        // Just close dialog without activating next cycle
                        dialog.dispose();

                        // Remove from active dialogs
                        activeDialogs.entrySet().removeIf(entry -> entry.getValue() == dialog);

                        return;
                    }

                    // Handle notification-specific auto-dismiss logic
                    handleNotificationAutoDismiss(username, notificationType);

                } catch (Exception ex) {
                    LoggerUtil.error(this.getClass(), String.format("Error handling auto-close for %s notification: %s", notificationType, ex.getMessage()), ex);

                } finally {
                    // Clean up resources when auto-closing
                    cleanupNotificationResources(dialog);
                }
            }
        });

        autoCloseTimer.setRepeats(false);
        autoCloseTimer.start();

        LoggerUtil.debug(this.getClass(), String.format("Set auto-dismiss timer for %s notification, user: %s, timeout: %d ms, count: %d/%d",
                notificationType, username, timeoutPeriod, currentCount + 1, MAX_NOTIFICATION_COUNT));
    }

    /**
     * Handles notification auto-dismiss based on type
     */
    private void handleNotificationAutoDismiss(String username, String notificationType) {
        try {
            // Special handling for long-duration notifications
            if (WorkCode.START_DAY_TYPE.equals(notificationType) ||
                    WorkCode.RESOLUTION_REMINDER_TYPE.equals(notificationType)) {

                // For these notifications, we don't actually dismiss, just log
                LoggerUtil.info(this.getClass(), String.format("Long-duration notification (%s) will remain visible during business hours", notificationType));

                // Return early - dialog remains visible due to enhanceLongDurationVisibility
                return;
            }
            switch (notificationType) {
                case WorkCode.SCHEDULE_END_TYPE:
                    // For end of schedule notifications, activate hourly monitoring
                    ActivateHourlyMonitoringCommand command = commandFactory.createActivateHourlyMonitoringCommand(username);
                    boolean result = commandService.executeCommand(command);

                    if (result) {
                        LoggerUtil.info(this.getClass(), String.format("Successfully activated hourly monitoring for user %s after notification timeout", username));

                        // Update monitor service
                        monitorService.markHourlyMonitoringActive(username, LocalDateTime.now());

                    } else {
                        LoggerUtil.warn(this.getClass(), String.format("Failed to activate hourly monitoring for user %s after notification timeout", username));
                    }
                    break;
                case WorkCode.OVERTIME_TYPE:
                    // For hourly warnings, continue the hourly monitoring cycle
                    LoggerUtil.info(this.getClass(), String.format("Auto-continuing hourly monitoring for user %s", username));
                    break;
                case WorkCode.TEMP_STOP_TYPE:
                    // For temporary stop warnings, continue temporary stop monitoring
                    LoggerUtil.info(this.getClass(), String.format("Auto-continuing temporary stop monitoring for user %s", username));
                    break;
                case "TEST":
                    // For test notifications, log that we're completing the test
                    LoggerUtil.info(this.getClass(), String.format("Auto-dismissing test notification for user %s", username));
                    break;
                default:
                    LoggerUtil.warn(this.getClass(), String.format("Unknown notification type for auto-dismiss: %s for user %s", notificationType, username));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in handleNotificationAutoDismiss for %s: %s", username, e.getMessage()));
        }
    }

    /**
     * Adds buttons based on notification type
     */
    private void addButtonsForNotificationType(DialogComponents components, NotificationRequest request) {
        switch (request.getType()) {
            case SCHEDULE_END:
                addStandardButtons(components, request.getUsername(), request.getUserId(), request.getFinalMinutes(), false);
                break;
            case HOURLY_WARNING:
                addStandardButtons(components, request.getUsername(), request.getUserId(), request.getFinalMinutes(), true);
                break;
            case TEMP_STOP:
                addTempStopButtons(components, request.getUsername(), request.getUserId());
                break;
            case START_DAY:
                addStartDayButtons(components, request.getUsername(), request.getUserId());
                break;
            case RESOLUTION:
                addResolutionButtons(components);
                break;
            case TEST:
                addTestButtons(components, userResponded);
                break;
        }
    }

    // Methods to add specific buttons to notifications

    /**
     * Adds test dialog specific buttons
     */
    private void addTestButtons(DialogComponents components, AtomicBoolean respondedFlag) {
        JPanel buttonsPanel = components.buttonsPanel();
        JDialog dialog = components.dialog();

        // Open Website Button using ButtonFactory
        JButton openWebsiteButton = ButtonFactory.createButton(
                WorkCode.OPEN_WEBSITE,
                ButtonFactory.BUTTON_SECONDARY,
                ButtonFactory.STYLE_FILLED,
                e -> {
                    dialog.dispose();
                    systemTray.openApplication();
                    LoggerUtil.info(this.getClass(), "User chose to open website from test notification");
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                respondedFlag
        );

        // Dismiss Button using ButtonFactory
        JButton dismissButton = ButtonFactory.createButton(
                WorkCode.DISMISS_BUTTON,
                ButtonFactory.BUTTON_NEUTRAL,
                ButtonFactory.STYLE_FILLED,
                e -> {
                    dialog.dispose();
                    LoggerUtil.info(this.getClass(), "User dismissed test notification");
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                respondedFlag
        );

        buttonsPanel.add(openWebsiteButton);
        buttonsPanel.add(dismissButton);
    }

    /**
     * Adds standard notification buttons (Continue Working and End Session)
     */
    private void addStandardButtons(DialogComponents components, String username, Integer userId,
                                    Integer finalMinutes, boolean isHourly) {
        JPanel buttonsPanel = components.buttonsPanel();
        JDialog dialog = components.dialog();

        // Continue Working Button using ButtonFactory
        JButton continueWorkingButton = ButtonFactory.createButton(
                WorkCode.CONTINUE_WORKING,
                ButtonFactory.BUTTON_PRIMARY,
                ButtonFactory.STYLE_FILLED,
                e -> {
                    dialog.dispose();
                    // Create and execute continue working command
                    ContinueWorkingCommand command = commandFactory.createContinueWorkingCommand(username, isHourly);
                    commandService.executeCommand(command);
                    // Cancel backup task since user responded
                    backupService.cancelBackupTask(username);
                    LoggerUtil.info(this.getClass(), String.format("User %s chose to continue working.", username));
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                userResponded
        );

        // End Session Button using ButtonFactory
        JButton endSessionButton = ButtonFactory.createButton(
                WorkCode.END_SESSION,
                ButtonFactory.BUTTON_DANGER,
                ButtonFactory.STYLE_FILLED,
                e -> {
                    dialog.dispose();
                    try {
                        // Use the command factory to create the command
                        EndSessionFromNotificationCommand command = commandFactory.createEndSessionFromNotificationCommand(username, userId, finalMinutes);
                        // Execute the command through the command service
                        boolean success = commandService.executeCommand(command);
                        if (success) {
                            LoggerUtil.info(this.getClass(), String.format("User chose to end session for user %s", username));
                        } else {
                            LoggerUtil.warn(this.getClass(), "Failed to end session from notification");
                        }
                    } catch (Exception ex) {
                        LoggerUtil.error(this.getClass(), "Error ending session from notification: " + ex.getMessage());
                    } finally {
                        //Clean up resources
                        cleanupNotificationResources(dialog);
                    }
                },
                userResponded
        );

        buttonsPanel.add(continueWorkingButton);
        buttonsPanel.add(endSessionButton);
    }

    /**
     * Adds temporary stop specific buttons
     */
    private void addTempStopButtons(DialogComponents components, String username, Integer userId) {
        JPanel buttonsPanel = components.buttonsPanel();
        JDialog dialog = components.dialog();

        // Continue Break Button using ButtonFactory
        JButton continueBreakButton = ButtonFactory.createButton(
                WorkCode.CONTINUE_BREAK,
                ButtonFactory.BUTTON_PRIMARY,
                ButtonFactory.STYLE_FILLED,
                e -> {
                    dialog.dispose();
                    // Create and execute continue temp stop command
                    ContinueTempStopCommand command = commandFactory.createContinueTempStopCommand(username, userId);
                    commandService.executeCommand(command);
                    // Cancel backup task since user responded
                    backupService.cancelBackupTask(username);
                    LoggerUtil.info(this.getClass(), "User chose to continue temporary stop");
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                userResponded
        );

        // Resume Work Button using ButtonFactory
        JButton resumeWorkButton = ButtonFactory.createButton(
                WorkCode.RESUME_WORK,
                ButtonFactory.BUTTON_SECONDARY,
                ButtonFactory.STYLE_FILLED,
                e -> {
                    dialog.dispose();
                    // Create and execute resume from temp stop command
                    ResumeFromTempStopCommand command = commandFactory.createResumeFromTempStopCommand(username, userId);
                    commandService.executeCommand(command);
                    LoggerUtil.info(this.getClass(), "User chose to resume work from temporary stop");
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                userResponded
        );

        // End Session Button using ButtonFactory
        JButton endSessionButton = ButtonFactory.createButton(
                WorkCode.END_SESSION,
                ButtonFactory.BUTTON_DANGER,
                ButtonFactory.STYLE_FILLED,
                e -> {
                    dialog.dispose();
                    // First resume from temp stop, then end session - use commands for both
                    ResumeFromTempStopCommand resumeCommand = commandFactory.createResumeFromTempStopCommand(username, userId);
                    commandService.executeCommand(resumeCommand);
                    EndSessionFromNotificationCommand endCommand = commandFactory.createEndSessionFromNotificationCommand(username, userId, null);
                    commandService.executeCommand(endCommand);
                    LoggerUtil.info(this.getClass(), "User chose to end session from temporary stop");
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                userResponded
        );

        buttonsPanel.add(continueBreakButton);
        buttonsPanel.add(resumeWorkButton);
        buttonsPanel.add(endSessionButton);
    }

    /**
     * Adds start day dialog specific buttons
     */
    private void addStartDayButtons(DialogComponents components, String username, Integer userId) {
        JPanel buttonsPanel = components.buttonsPanel();
        JDialog dialog = components.dialog();

        // Start Work Button using ButtonFactory
        JButton startWorkButton = ButtonFactory.createButton(
                WorkCode.START_WORK,
                ButtonFactory.BUTTON_PRIMARY,
                ButtonFactory.STYLE_FILLED,
                e -> {
                    dialog.dispose();
                    StartWorkDayCommand command = commandFactory.createStartWorkDayCommand(username, userId);
                    commandService.executeCommand(command);
                    LoggerUtil.info(this.getClass(), "User chose to start work day through notification");
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                userResponded
        );

        // Skip Button using ButtonFactory
        JButton skipButton = ButtonFactory.createButton(
                WorkCode.SKIP_BUTTON,
                ButtonFactory.BUTTON_DANGER,
                ButtonFactory.STYLE_FILLED,
                e -> {
                    dialog.dispose();
                    LoggerUtil.info(this.getClass(), "User chose to skip start day reminder");
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                userResponded
        );

        buttonsPanel.add(startWorkButton);
        buttonsPanel.add(skipButton);
    }

    /**
     * Adds resolution reminder specific buttons
     */
    private void addResolutionButtons(DialogComponents components) {
        JPanel buttonsPanel = components.buttonsPanel();
        JDialog dialog = components.dialog();

        // Resolve Session Button using ButtonFactory
        JButton resolveSessionButton = ButtonFactory.createButton(
                WorkCode.RESOLVE_SESSION_BUTTON,
                ButtonFactory.BUTTON_PRIMARY,
                ButtonFactory.STYLE_FILLED,
                e -> {
                    dialog.dispose();
                    // Open the resolution page in the browser
                    systemTray.openApplication();
                    LoggerUtil.info(this.getClass(), "User chose to resolve session from notification");
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                userResponded
        );

        // Dismiss button using ButtonFactory
        JButton dismissButton = ButtonFactory.createButton(
                WorkCode.DISMISS_BUTTON,
                ButtonFactory.BUTTON_DANGER,
                ButtonFactory.STYLE_FILLED,
                e -> {
                    dialog.dispose();
                    LoggerUtil.info(this.getClass(), "User dismissed resolution reminder");
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                userResponded
        );

        buttonsPanel.add(resolveSessionButton);
        buttonsPanel.add(dismissButton);
    }

    /**
     * Performs a focused cleanup of notification UI resources
     */
    private void cleanupNotificationResources(JDialog dialog) {
        try {
            // Make sure the dialog is disposed
            if (dialog.isDisplayable()) {
                dialog.dispose();
            }

            // Remove from active dialogs
            activeDialogs.entrySet().removeIf(entry -> entry.getValue() == dialog);

            // Cleanup visibility timers associated with this dialog
            // Get dialog identifier from client properties if available
            Object usernameObj = dialog.getRootPane().getClientProperty("username");
            Object typeObj = dialog.getRootPane().getClientProperty("notificationType");

            if (usernameObj instanceof String username && typeObj instanceof String notificationType) {

                // Remove and cancel timer
                String timerKey = username + "_visibility_" + notificationType;
                Timer timer = timers.remove(timerKey);
                if (timer != null) {
                    timer.stop();
                    LoggerUtil.debug(this.getClass(),
                            String.format("Stopped visibility timer for %s notification (%s)",
                                    notificationType, username));
                }
            }


            // Give a hint to garbage collector (optional)
            if (Math.random() < 0.25) { // Only trigger occasionally to avoid performance impact
                System.gc();
            }

            LoggerUtil.debug(this.getClass(), "Notification resources cleaned up");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during notification resource cleanup: " + e.getMessage());
        }
    }

    /**
     * Performs a comprehensive cleanup of notification UI resources
     */
    private void cleanupUIResources() {
        SwingUtilities.invokeLater(() -> {
            try {
                // Dispose any visible dialogs
                for (JDialog dialog : activeDialogs.values()) {
                    if (dialog.isDisplayable()) {
                        dialog.dispose();
                    }
                }

                // Clear the map
                activeDialogs.clear();

                // Force a garbage collection hint (optional, system may ignore)
                System.gc();

                LoggerUtil.debug(this.getClass(), "UI resources cleanup completed");

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error during UI resource cleanup: " + e.getMessage());
            }
        });
    }

    /**
     * Resets the service state (for health monitor recovery)
     */
    public void resetService() {
        try {
            LoggerUtil.info(this.getClass(), "Resetting notification display service after detected unhealthy state");

            // Clear any pending notifications
            userResponded.set(false);

            // Clean up UI resources, but preserve long-duration notifications during business hours
            LocalDateTime currentTime = getStandardCurrentTime();
            int hour = currentTime.getHour();
            int dayOfWeek = currentTime.getDayOfWeek().getValue();

            boolean withinBusinessHours = hour >= WorkCode.WORK_START_HOUR &&
                    hour < WorkCode.WORK_END_HOUR;
            boolean isWeekday = dayOfWeek >= 1 && dayOfWeek <= 5;

            if (withinBusinessHours && isWeekday) {
                // During business hours, only clean up non-long-duration notifications
                cleanupNonLongDurationNotifications();
            } else {
                // Outside business hours, clean up all notifications
                cleanupUIResources();
            }


            // Mark service as healthy again
            healthMonitor.recordTaskExecution("notification-display-service");

            LoggerUtil.info(this.getClass(), "Notification display service reset completed");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error resetting notification display service: " + e.getMessage());
        }
    }

    /**
     * Heartbeat to ensure the service is still functioning
     */
    @Scheduled(fixedRate = 240000) // Run every 4 minutes
    public void heartbeat() {
        try {
            // Record that the service is still alive
            healthMonitor.recordTaskExecution("notification-display-service");

            // Check for any hung dialogs that need cleanup
            checkAndCleanupHungDialogs();

            LoggerUtil.debug(this.getClass(), "Notification display service heartbeat recorded");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in notification display service heartbeat: " + e.getMessage());
            healthMonitor.recordTaskFailure("notification-display-service", e.getMessage());
        }
    }

    /**
     * Check for hung dialogs that need cleanup
     */
    private void checkAndCleanupHungDialogs() {
        // Get standardized time
        LocalDateTime standardDateTime = getStandardCurrentTime();

        // Convert to milliseconds since epoch
        long currentTime = standardDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // Remove dialogs from the active dialogs map based on specific criteria
        activeDialogs.entrySet().removeIf(entry -> {
            JDialog dialog = entry.getValue();

            // Check if dialog is still displayed
            if (dialog.isDisplayable()) {
                // Get dialog creation time and notification type from client properties
                Object creationTimeObj = dialog.getRootPane().getClientProperty("creationTime");
                Object typeObj = dialog.getRootPane().getClientProperty("notificationType");

                long creationTime = (creationTimeObj instanceof Long) ? (Long) creationTimeObj : 0;
                String notificationType = (typeObj instanceof String) ? (String) typeObj : null;

                // FIXED: Don't clean up long-duration notifications during business hours
                boolean isLongDurationNotification =
                        WorkCode.START_DAY_TYPE.equals(notificationType) ||
                                WorkCode.RESOLUTION_REMINDER_TYPE.equals(notificationType);

                if (isLongDurationNotification) {
                    // For long-duration notifications, check if we're in business hours
                    int hour = standardDateTime.getHour();
                    int dayOfWeek = standardDateTime.getDayOfWeek().getValue();

                    boolean withinBusinessHours = hour >= WorkCode.WORK_START_HOUR &&
                            hour < WorkCode.WORK_END_HOUR;
                    boolean isWeekday = dayOfWeek >= 1 && dayOfWeek <= 5;

                    // During business hours on weekdays, don't clean up these notifications
                    if (withinBusinessHours && isWeekday) {
                        LoggerUtil.debug(this.getClass(),
                                String.format("Preserving long-duration notification (%s) during business hours",
                                        notificationType));
                        return false;
                    }
                }

                // For regular notifications or outside business hours:
                // If no creation time or dialog has been showing for more than 30 minutes
                if (creationTime == 0 || (currentTime - creationTime > 30 * 60 * 1000)) {
                    LoggerUtil.info(this.getClass(), "Cleaning up hung dialog: " + dialog.getTitle() + " (ID: " + entry.getKey() + ")");
                    dialog.dispose();
                    return true;
                }
            } else {
                // Dialog is no longer displayed
                return true;
            }

            return false;
        });
    }

    private void cleanupNonLongDurationNotifications() {
        SwingUtilities.invokeLater(() -> {
            try {
                // Keep track of removed dialogs
                int removedCount = 0;

                // Check each dialog
                for (Map.Entry<String, JDialog> entry : activeDialogs.entrySet()) {
                    JDialog dialog = entry.getValue();

                    if (dialog.isDisplayable()) {
                        // Get notification type if available
                        Object typeObj = dialog.getRootPane().getClientProperty("notificationType");
                        String notificationType = (typeObj instanceof String) ? (String) typeObj : null;

                        // Remove only if not a long-duration notification
                        // Is not a long-duration notification type
                        boolean isNotLongDuration = (!WorkCode.START_DAY_TYPE.equals(notificationType) &&
                                !WorkCode.RESOLUTION_REMINDER_TYPE.equals(notificationType));

                        if (isNotLongDuration) {
                            dialog.dispose();
                            removedCount++;
                        }

                    } else {
                        // Dialog no longer displayed
                        removedCount++;
                    }
                }

                // Remove disposed dialogs from map
                activeDialogs.entrySet().removeIf(entry -> !entry.getValue().isDisplayable());

                LoggerUtil.debug(this.getClass(), String.format("Cleaned up %d non-long-duration dialogs, preserved long-duration dialogs", removedCount));

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error during selective UI resource cleanup: " + e.getMessage());
            }
        });
    }

    private LocalDateTime getStandardCurrentTime() {
        // Get standardized time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        return timeValues.getCurrentTime();
    }
}