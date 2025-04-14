package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.ui.DialogComponents;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.notification.*;
import com.ctgraphdep.session.query.CanShowNotificationQuery;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;
import com.ctgraphdep.tray.CTTTSystemTray;
import com.ctgraphdep.ui.ButtonFactory;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.ui.NotificationBackgroundFactory;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for displaying system notifications to users
 * Handles both dialog and system tray notifications with fallback mechanisms
 */
@Service
@Getter
public class SystemNotificationService {

    private final CTTTSystemTray systemTray;
    private final AtomicBoolean userResponded;
    private final PathConfig pathConfig;
    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    @Autowired
    private SchedulerHealthMonitor healthMonitor;

    private final Map<String, LocalDateTime> lastNotificationTimes = new ConcurrentHashMap<>();
    private static final int NOTIFICATION_WIDTH = 600;
    private static final int NOTIFICATION_HEIGHT = 400;
    private static final int BUTTONS_PANEL_HEIGHT = 50;
    private static final int BUTTON_SPACING = 20;

    public SystemNotificationService(CTTTSystemTray systemTray, PathConfig pathConfig, @Lazy SessionCommandService commandService, @Lazy SessionCommandFactory commandFactory) {
        this.systemTray = systemTray;
        this.pathConfig = pathConfig;
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.userResponded = new AtomicBoolean(false);
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        // Register with health monitor including recovery action
        healthMonitor.registerTask(
                "notification-service",
                5, // Check every 5 minutes
                status -> {
                    // Recovery action when notification service is unhealthy
                    LoggerUtil.warn(this.getClass(), String.format("Attempting to recover unhealthy notification service. Last execution: %s, Minutes since: %d",
                                    status.getLastExecutionTime(), status.getMinutesSinceLastExecution()));
                    resetService();
                }
        );

        // Record initial execution to establish baseline
        healthMonitor.recordTaskExecution("notification-service");
        LoggerUtil.info(this.getClass(), "Notification service initialized and registered with health monitor");
    }

    // Shows schedule completion warning to the user
    public boolean showSessionWarning(String username, Integer userId, Integer finalMinutes) {
        try {
            // Create and execute the command instead of direct implementation
            ShowSessionWarningCommand command = commandFactory.createShowSessionWarningCommand(username, userId, finalMinutes);
            boolean result = commandService.executeCommand(command);

            // Record success
            if (result) {
                healthMonitor.recordTaskExecution("notification-service");
                LoggerUtil.debug(this.getClass(), "Recorded notification service execution for session warning");
            }

            return result;
        } catch (Exception e) {
            // Record failure
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            LoggerUtil.error(this.getClass(), "Error in notification service: " + e.getMessage(), e);
            return false;
        }
    }

    @Scheduled(fixedRate = 240000) // Run every 4 minutes
    public void heartbeat() {
        try {
            // Simply record that the service is still alive
            healthMonitor.recordTaskExecution("notification-service");
            LoggerUtil.debug(this.getClass(), "Notification service heartbeat recorded");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in notification service heartbeat: " + e.getMessage());
        }
    }

    // Shows hourly overtime warning to the user
    public boolean showHourlyWarning(String username, Integer userId, Integer finalMinutes) {
        try {
            ShowHourlyWarningCommand command = commandFactory.createShowHourlyWarningCommand(username, userId, finalMinutes);
            boolean result = commandService.executeCommand(command);

            // Record success
            if (result) {
                healthMonitor.recordTaskExecution("notification-service");
                LoggerUtil.debug(this.getClass(), "Recorded notification service execution for hourly warning");
            }

            return result;
        } catch (Exception e) {
            // Record failure
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            LoggerUtil.error(this.getClass(), "Error in notification service: " + e.getMessage(), e);
            return false;
        }
    }

    // Shows temporary stop duration warning to the user
    public boolean showLongTempStopWarning(String username, Integer userId, LocalDateTime tempStopStart) {
        try {
            ShowTempStopWarningCommand command = commandFactory.createShowTempStopWarningCommand(username, userId, tempStopStart);
            boolean result = commandService.executeCommand(command);

            // Record success
            if (result) {
                healthMonitor.recordTaskExecution("notification-service");
                LoggerUtil.debug(this.getClass(), "Recorded notification service execution for temp stop warning");
            }

            return result;
        } catch (Exception e) {
            // Record failure
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            LoggerUtil.error(this.getClass(), "Error in notification service: " + e.getMessage(), e);
            return false;
        }
    }

    // Shows work day start reminder to the user
    public boolean showStartDayReminder(String username, Integer userId) {
        try {
            ShowStartDayReminderCommand command = commandFactory.createShowStartDayReminderCommand(username, userId);
            boolean result = commandService.executeCommand(command);

            // Record success
            if (result) {
                healthMonitor.recordTaskExecution("notification-service");
                LoggerUtil.debug(this.getClass(), "Recorded notification service execution for start day reminder");
            }

            return result;
        } catch (Exception e) {
            // Record failure
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            LoggerUtil.error(this.getClass(), "Error in notification service: " + e.getMessage(), e);
            return false;
        }
    }

    // Add resetService method for recovery
    public void resetService() {
        try {
            LoggerUtil.info(this.getClass(), "Resetting notification service after detected unhealthy state");

            // Clear any pending notifications
            userResponded.set(false);

            // Reset any internal state that might be causing issues
            lastNotificationTimes.clear();

            // Clean up UI resources
            cleanupUIResources();

            // Mark service as healthy again
            healthMonitor.recordTaskExecution("notification-service");
            LoggerUtil.info(this.getClass(), "Notification service reset completed");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error resetting notification service: " + e.getMessage(), e);
        }
    }

    // Shows a Worktime entry resolution reminder to the user
    public boolean showResolutionReminder(String username, Integer userId, String title, String message, String trayMessage, Integer timeoutPeriod) {
        try {
            // Create and execute the command instead of direct implementation
            ShowResolutionReminderCommand command = commandFactory.createShowResolutionReminderCommand(username, userId, title, message, trayMessage, timeoutPeriod);
            boolean result = commandService.executeCommand(command);

            // Record success
            if (result) {
                healthMonitor.recordTaskExecution("notification-service");
                LoggerUtil.debug(this.getClass(), "Recorded notification service execution for resolution reminder");
            }

            return result;
        } catch (Exception e) {
            // Record failure
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            LoggerUtil.error(this.getClass(), "Error in notification service: " + e.getMessage(), e);
            return false;
        }
    }

    // Shows a test dialog with buttons (used by the test notification command)
    public void showTestDialogWithButtons(String username, AtomicBoolean testResponded, AtomicBoolean dialogDisplayed) {
        // Must be called on EDT
        if (!SwingUtilities.isEventDispatchThread()) {
            LoggerUtil.error(this.getClass(), "showTestDialogWithButtons must be called on EDT");
            return;
        }

        try {
            DialogComponents components = createDialog(WorkCode.TEST_NOTICE_TITLE, WorkCode.TEST_MESSAGE);

            // Add test-specific buttons
            addTestButtons(components, testResponded);

            try {
                showDialog(components.dialog());
                dialogDisplayed.set(true);
                LoggerUtil.info(this.getClass(), "Test dialog displayed successfully");

                // Add auto-close timer
                setupAutoCloseTimer(components.dialog(), username, WorkCode.ON_FOR_TEN_SECONDS, testResponded);

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Failed to display test dialog: " + e.getMessage());
                dialogDisplayed.set(false);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error preparing test dialog: " + e.getMessage());
        }
    }

    // Checks if a notification can be shown based on rate limiting
    public boolean canShowNotification(String username, String notificationType, Integer intervalMinutes) {
        CanShowNotificationQuery query = commandFactory.createCanShowNotificationQuery(username, notificationType, intervalMinutes, lastNotificationTimes);
        return commandService.executeQuery(query);
    }

    // Shows notification with fallback to tray if dialog can't be shown
    public boolean showNotificationWithFallback(String username, Integer userId, String title,
                                                String message, String trayMessage, int timeoutPeriod,
                                                boolean isHourly, boolean isTempStop, Integer finalMinutes,
                                                ButtonsProvider buttonsProvider, TrayIcon.MessageType messageType) {
        try {
            LoggerUtil.info(this.getClass(), "Attempting to display notification for user: " + username);
            LoggerUtil.info(this.getClass(), "Current thread: " + Thread.currentThread().getName());

            // Record attempt to show notification
            healthMonitor.recordTaskExecution("notification-service");

            // Reset the userResponded flag before showing new notification
            userResponded.set(false);

            // Always use invokeLater instead of invokeAndWait to avoid potential deadlocks
            SwingUtilities.invokeLater(() -> {
                try {
                    // Check if we're on the EDT now
                    LoggerUtil.info(this.getClass(), "UI thread: " + Thread.currentThread().getName() +
                            ", is EDT: " + SwingUtilities.isEventDispatchThread());

                    // Try showing dialog first
                    boolean dialogDisplayed = showNotificationDialogOnEDT(
                            username, userId, finalMinutes, title, message,
                            timeoutPeriod, isHourly, isTempStop, buttonsProvider);

                    // Log result
                    LoggerUtil.info(this.getClass(), String.format("Notification dialog display attempt for %s: %b",
                            username, dialogDisplayed));

                    // Fall back to taskbar notification if dialog fails
                    if (!dialogDisplayed && systemTray.getTrayIcon() != null) {
                        LoggerUtil.info(this.getClass(), "Attempting to display tray notification for user: " + username);
                        systemTray.getTrayIcon().displayMessage(title, trayMessage, messageType);
                        LoggerUtil.info(this.getClass(), "Tray notification displayed for: " + title);

                        // Add a fallback mechanism to ensure user response tracking
                        startFallbackResponseTimer(username);
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error in EDT showing notification: " + e.getMessage());
                    healthMonitor.recordTaskFailure("notification-service", e.getMessage());
                    // Create fallback mechanism
                    startFallbackResponseTimer(username);
                }
            });

            // Return true since we've dispatched the notification request
            // The actual result will be handled asynchronously
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in showNotificationWithFallback: " + e.getMessage());

            // Record failure
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());

            // Even on exception, ensure we have a fallback tracking mechanism
            LoggerUtil.info(this.getClass(), "Setting up emergency fallback for notification response tracking");
            startFallbackResponseTimer(username);
            return false;
        }
    }

    // Modified version that expects to be run directly on EDT
    private boolean showNotificationDialogOnEDT(String username, Integer userId, Integer finalMinutes,
                                                String title, String message, Integer timeoutPeriod,
                                                boolean isHourly, boolean isTempStop, ButtonsProvider buttonsProvider) {
        LoggerUtil.debug(this.getClass(), String.format("Showing notification on EDT - isHourly: %b, isTempStop: %b",
                isHourly, isTempStop));

        // Sanity check - make sure we're on EDT
        if (!SwingUtilities.isEventDispatchThread()) {
            LoggerUtil.error(this.getClass(), "showNotificationDialogOnEDT called from non-EDT thread!");
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

        // Don't show regular notification during temp stop
        // Instead of using SessionResolutionQuery, directly check the session status
        if (!isTempStop) {
            // Get current session using GetCurrentSessionQuery
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, userId);
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // Check if session is in temporary stop state
            boolean isInTempStop = session != null &&
                    WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus());

            if (isInTempStop) {
                LoggerUtil.debug(this.getClass(), "Skipping regular notification during temp stop");
                return false;
            }
        }

        userResponded.set(false);
        boolean dialogDisplayed;

        try {
            // Now we're already on EDT, so we don't need invokeAndWait
            DialogComponents components = createDialog(title, message);
            buttonsProvider.addButtons(components, username, userId, finalMinutes);
            showDialog(components.dialog());
            dialogDisplayed = true;

            // Set up auto-close timer for the notification
            setupAutoCloseTimer(components.dialog(), username, timeoutPeriod, userResponded);

            // Track notification display
            trackNotificationDisplay(username, userId, timeoutPeriod, isTempStop);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to display notification dialog on EDT: " + e.getMessage());
            dialogDisplayed = false;
        }

        return dialogDisplayed;
    }

    // Sets up an auto-close timer for a notification dialog
    private void setupAutoCloseTimer(JDialog dialog, String username, int timeoutPeriod, AtomicBoolean respondedFlag) {
        if (timeoutPeriod <= 0) {
            LoggerUtil.debug(this.getClass(), "No auto-close timer needed for " + username + " (timeoutPeriod: " + timeoutPeriod + ")");
            return; // No timer needed
        }

        Timer autoCloseTimer = new Timer(timeoutPeriod, e -> {
            if (!respondedFlag.get() && dialog.isDisplayable()) {
                LoggerUtil.info(this.getClass(), String.format("Auto-dismissing notification for %s after timeout (%d ms)", username, timeoutPeriod));
                // Clean up resources when auto-closing
                cleanupNotificationResources(dialog);
            }
        });
        autoCloseTimer.setRepeats(false);
        autoCloseTimer.start();

        LoggerUtil.debug(this.getClass(), String.format("Set auto-dismiss timer for %s, %d ms", username, timeoutPeriod));
    }

    // Method to add a response tracking mechanism
    private void startFallbackResponseTimer(String username) {
        try {
            // Create a file-based tracking mechanism
            Path trackingFile = pathConfig.getLocalPath().resolve("notification").resolve(username + "_notification.lock");
            Files.createDirectories(trackingFile.getParent());
            Files.write(trackingFile, LocalDateTime.now().toString().getBytes());
            LoggerUtil.info(this.getClass(), String.format("Created notification tracking file for %s", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to create notification tracking: " + e.getMessage());
        }
    }

    // Method to track notification display without auto-closing
    private void trackNotificationDisplay(String username, Integer userId, int timeoutPeriod, boolean isTempStop) {
        // Create and execute notification tracking command
        TrackNotificationDisplayCommand command = commandFactory.createTrackNotificationDisplayCommand(username, userId, timeoutPeriod, isTempStop);
        commandService.executeCommand(command);
        // Log that notification was displayed
        LoggerUtil.info(this.getClass(), String.format("Notification displayed for user %s - will remain visible until user responds or times out", username));
    }

    // Creates a dialog with the specified title and message
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

    // Positions and displays the dialog on screen
    private void showDialog(JDialog dialog) {
        dialog.setBackground(new Color(0, 0, 0, 0));

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
        LoggerUtil.info(this.getClass(), String.format("Dialog shown at position X: %d, Y: %d",
                screenSize.width - dialog.getWidth() - 20,
                screenSize.height - dialog.getHeight() - 50));

        // Add check to verify dialog is visible on screen
        if (!dialog.isShowing()) {
            LoggerUtil.error(this.getClass(), "Dialog set to visible but not showing on screen");
        }
    }

    // Adds test dialog specific buttons
    public void addTestButtons(DialogComponents components, AtomicBoolean respondedFlag) {
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
                    LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to open website from test notification");
                    // Clean up resources when auto-closing
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
                    LoggerUtil.info(SystemNotificationService.this.getClass(), "User dismissed test notification");
                    // Clean up resources when auto-closing
                    cleanupNotificationResources(dialog);
                },
                respondedFlag
        );

        buttonsPanel.add(openWebsiteButton);
        buttonsPanel.add(dismissButton);
    }

    // Adds standard notification buttons (Continue Working and End Session)
    public void addStandardButtons(DialogComponents components, String username, Integer userId, Integer finalMinutes, boolean isHourly) {
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
                    LoggerUtil.info(SystemNotificationService.this.getClass(), String.format("User %s chose to continue working - continuation point recorded", username));
                    // Clean up resources when auto-closing
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
                        EndSessionFromNotificationCommand command = commandFactory.createEndSessionFromNotificationCommand(
                                username, userId, finalMinutes);
                        // Execute the command through the command service
                        boolean success = commandService.executeCommand(command);
                        if (success) {
                            LoggerUtil.info(SystemNotificationService.this.getClass(), String.format("User chose to end session for user %s", username));
                        } else {
                            LoggerUtil.warn(SystemNotificationService.this.getClass(), "Failed to end session from notification");
                        }
                    } catch (Exception ex) {
                        LoggerUtil.error(SystemNotificationService.this.getClass(), "Error ending session from notification: " + ex.getMessage());
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

    // Adds temporary stop specific buttons
    public void addTempStopButtons(DialogComponents components, String username, Integer userId) {
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
                    LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to continue temporary stop");
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
                    LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to resume work from temporary stop");
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
                    LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to end session from temporary stop");
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                userResponded
        );

        buttonsPanel.add(continueBreakButton);
        buttonsPanel.add(resumeWorkButton);
        buttonsPanel.add(endSessionButton);
    }

    // Adds start day dialog specific buttons
    public void addStartDayButtons(DialogComponents components, String username, Integer userId) {
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

    // Adds resolution reminder specific buttons
    public void addResolutionButtons(DialogComponents components) {
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
                    LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to resolve session from notification");
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
                    LoggerUtil.info(SystemNotificationService.this.getClass(), "User dismissed resolution reminder");
                    // Clean up resources
                    cleanupNotificationResources(dialog);
                },
                userResponded
        );

        buttonsPanel.add(resolveSessionButton);
        buttonsPanel.add(dismissButton);
    }

    // Records the time a notification was shown
    // This is used for rate limiting notifications
    public void recordNotificationTime(String username, String notificationType) {
        String key = getNotificationKey(username, notificationType);
        lastNotificationTimes.put(key, LocalDateTime.now());
        LoggerUtil.debug(this.getClass(), String.format("Recorded notification time for %s - %s", username, notificationType));
    }

    // Gets a unique key for a notification based on username and type
    private String getNotificationKey(String username, String notificationType) {
        return username + "_" + notificationType;
    }

    /**
     * Performs a focused cleanup of notification UI resources
     * This is called after a notification is dismissed to ensure proper resource release
     */
    private void cleanupNotificationResources(JDialog dialog) {
        try {
            // 1. Make sure the dialog is disposed
            if (dialog.isDisplayable()) {
                dialog.dispose();
            }

            // 2. Give a hint to garbage collector (optional)
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
                // 1. Dispose any visible dialogs
                Window[] windows = Window.getWindows();
                for (Window window : windows) {
                    if (window instanceof JDialog dialog) {
                        if (dialog.isDisplayable() && dialog.getTitle() != null &&
                                (dialog.getTitle().contains("Warning") ||
                                        dialog.getTitle().contains("Schedule") ||
                                        dialog.getTitle().contains("Session"))) {
                            LoggerUtil.debug(this.getClass(), "Disposing dialog: " + dialog.getTitle());
                            dialog.dispose();
                        }
                    }
                }

                // 2. Force a garbage collection hint (optional, system may ignore)
                System.gc();

                LoggerUtil.debug(this.getClass(), "UI resources cleanup completed");
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error during UI resource cleanup: " + e.getMessage());
            }
        });
    }

    // Functional interface for providing buttons to dialogs
    @FunctionalInterface
    public interface ButtonsProvider {
        void addButtons(DialogComponents components, String username, Integer userId, Integer finalMinutes);
    }
}