package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.DialogComponents;
import com.ctgraphdep.model.NotificationButton;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.notification.*;
import com.ctgraphdep.session.query.CanShowNotificationQuery;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;
import com.ctgraphdep.tray.CTTTSystemTray;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.NotificationBackgroundUtility;
import lombok.Getter;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
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
public class SystemNotificationService {
    // Add a getter for systemTray
    @Getter
    private final CTTTSystemTray systemTray;
    private final AtomicBoolean userResponded;
    private final PathConfig pathConfig;
    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;

    // This is used by queries to determine if a notification can be shown
    @Getter
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

    // Shows schedule completion warning to the user
    @SuppressWarnings("UnusedReturnValue")
    public boolean showSessionWarning(String username, Integer userId, Integer finalMinutes) {
        // Create and execute the command instead of direct implementation
        ShowSessionWarningCommand command = commandFactory.createShowSessionWarningCommand(username, userId, finalMinutes);
        return commandService.executeCommand(command);
    }

    // Shows hourly overtime warning to the user
    @SuppressWarnings("UnusedReturnValue")
    public boolean showHourlyWarning(String username, Integer userId, Integer finalMinutes) {
        ShowHourlyWarningCommand command = commandFactory.createShowHourlyWarningCommand(username, userId, finalMinutes);
        return commandService.executeCommand(command);
    }

    // Shows temporary stop duration warning to the user
    @SuppressWarnings("UnusedReturnValue")
    public boolean showLongTempStopWarning(String username, Integer userId, LocalDateTime tempStopStart) {
        ShowTempStopWarningCommand command = commandFactory.createShowTempStopWarningCommand(username, userId, tempStopStart);
        return commandService.executeCommand(command);
    }

    // Shows work day start reminder to the user
    @SuppressWarnings("UnusedReturnValue")
    public boolean showStartDayReminder(String username, Integer userId) {
        ShowStartDayReminderCommand command = commandFactory.createShowStartDayReminderCommand(username, userId);
        return commandService.executeCommand(command);
    }

    // Shows a Worktime entry resolution reminder to the user
    @SuppressWarnings("UnusedReturnValue")
    public boolean showResolutionReminder(String username, Integer userId, String title, String message, String trayMessage, Integer timeoutPeriod) {
        // Create and execute the command instead of direct implementation
        ShowResolutionReminderCommand command = commandFactory.createShowResolutionReminderCommand(username, userId, title, message, trayMessage, timeoutPeriod);
        return commandService.executeCommand(command);
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
                    // Create fallback mechanism
                    startFallbackResponseTimer(username);
                }
            });

            // Return true since we've dispatched the notification request
            // The actual result will be handled asynchronously
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in showNotificationWithFallback: " + e.getMessage());
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
                LoggerUtil.info(this.getClass(),
                        String.format("Auto-dismissing notification for %s after timeout (%d ms)",
                                username, timeoutPeriod));
                dialog.dispose();
            }
        });
        autoCloseTimer.setRepeats(false);
        autoCloseTimer.start();

        LoggerUtil.debug(this.getClass(),
                String.format("Set auto-dismiss timer for %s, %d ms", username, timeoutPeriod));
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
        BufferedImage notificationImage = NotificationBackgroundUtility.createNotificationBackground(title, message);

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

        // Open Website Button
        class OpenWebsiteButton extends NotificationButton {
            OpenWebsiteButton() {
                super(WorkCode.OPEN_WEBSITE, new Color(51, 122, 183), respondedFlag);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();
                systemTray.openApplication();
                LoggerUtil.info(SystemNotificationService.this.getClass(),
                        "User chose to open website from test notification");
            }
        }

        // Dismiss Button
        class DismissButton extends NotificationButton {
            DismissButton() {
                super(WorkCode.DISMISS_BUTTON, new Color(108, 117, 125), respondedFlag);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();
                LoggerUtil.info(SystemNotificationService.this.getClass(),
                        "User dismissed test notification");
            }
        }

        buttonsPanel.add(new OpenWebsiteButton().create());
        buttonsPanel.add(new DismissButton().create());
    }

    // Adds standard notification buttons (Continue Working and End Session)
    public void addStandardButtons(DialogComponents components, String username, Integer userId, Integer finalMinutes, boolean isHourly) {
        JPanel buttonsPanel = components.buttonsPanel();
        JDialog dialog = components.dialog();

        // Continue Working Button
        class ContinueWorkingButton extends NotificationButton {
            ContinueWorkingButton() {
                super(WorkCode.CONTINUE_WORKING, new Color(0, 153, 51), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();

                // Create and execute continue working command
                ContinueWorkingCommand command = commandFactory.createContinueWorkingCommand(username, isHourly);
                commandService.executeCommand(command);

                LoggerUtil.info(SystemNotificationService.this.getClass(), String.format("User %s chose to continue working - continuation point recorded", username));
            }
        }

        // End Session Button
        class EndSessionButton extends NotificationButton {
            EndSessionButton() {
                super(WorkCode.END_SESSION, new Color(204, 51, 0), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();

                try {
                    // Use the command factory to create the command
                    EndSessionFromNotificationCommand command = commandFactory.createEndSessionFromNotificationCommand(username, userId, finalMinutes);

                    // Execute the command through the command service
                    boolean success = commandService.executeCommand(command);

                    if (success) {
                        LoggerUtil.info(SystemNotificationService.this.getClass(), String.format("User chose to end session for user %s", username));
                    } else {
                        LoggerUtil.warn(SystemNotificationService.this.getClass(), "Failed to end session from notification");
                    }
                } catch (Exception ex) {
                    LoggerUtil.error(SystemNotificationService.this.getClass(), "Error ending session from notification: " + ex.getMessage());
                }
            }
        }

        buttonsPanel.add(new ContinueWorkingButton().create());
        buttonsPanel.add(new EndSessionButton().create());
    }

    // Adds temporary stop specific buttons
    public void addTempStopButtons(DialogComponents components, String username, Integer userId) {
        JPanel buttonsPanel = components.buttonsPanel();
        JDialog dialog = components.dialog();

        // Continue Break Button
        class ContinueBreakButton extends NotificationButton {
            ContinueBreakButton() {
                super(WorkCode.CONTINUE_BREAK, new Color(0, 153, 51), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();

                // Create and execute continue temp stop command
                ContinueTempStopCommand command = commandFactory.createContinueTempStopCommand(
                        username, userId);
                commandService.executeCommand(command);

                LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to continue temporary stop");
            }
        }

        // Resume Work Button
        class ResumeWorkButton extends NotificationButton {
            ResumeWorkButton() {
                super(WorkCode.RESUME_WORK, new Color(51, 122, 183), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();

                // Create and execute resume from temp stop command
                ResumeFromTempStopCommand command = commandFactory.createResumeFromTempStopCommand(
                        username, userId);
                commandService.executeCommand(command);

                LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to resume work from temporary stop");
            }
        }

        // End Session Button
        class EndSessionButton extends NotificationButton {
            EndSessionButton() {
                super(WorkCode.END_SESSION, new Color(204, 51, 0), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();

                // First resume from temp stop, then end session - use commands for both
                ResumeFromTempStopCommand resumeCommand = commandFactory.createResumeFromTempStopCommand(
                        username, userId);
                commandService.executeCommand(resumeCommand);

                EndSessionFromNotificationCommand endCommand = commandFactory.createEndSessionFromNotificationCommand(
                        username, userId, null);
                commandService.executeCommand(endCommand);

                LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to end session from temporary stop");
            }
        }

        buttonsPanel.add(new ContinueBreakButton().create());
        buttonsPanel.add(new ResumeWorkButton().create());
        buttonsPanel.add(new EndSessionButton().create());
    }

    // Adds start day dialog specific buttons
    public void addStartDayButtons(DialogComponents components, String username, Integer userId) {
        JPanel buttonsPanel = components.buttonsPanel();
        JDialog dialog = components.dialog();

        // Start Work Button
        class StartWorkButton extends NotificationButton {
            StartWorkButton() {
                super(WorkCode.START_WORK, new Color(0, 153, 51), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();

                // Use command factory to create the command
                StartWorkDayCommand command = commandFactory.createStartWorkDayCommand(username, userId);
                // Execute the command through the command service
                commandService.executeCommand(command);

                LoggerUtil.info(SystemNotificationService.this.getClass(),
                        "User chose to start work day through notification");
            }
        }

        // Skip Button
        class SkipButton extends NotificationButton {
            SkipButton() {
                super(WorkCode.SKIP_BUTTON, new Color(204, 51, 0), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();
                LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to skip start day reminder");
            }
        }

        buttonsPanel.add(new StartWorkButton().create());
        buttonsPanel.add(new SkipButton().create());
    }

    // Adds resolution reminder specific buttons
    public void addResolutionButtons(DialogComponents components) {
        JPanel buttonsPanel = components.buttonsPanel();
        JDialog dialog = components.dialog();

        // Resolve Session Button
        class ResolveSessionButton extends NotificationButton {
            ResolveSessionButton() {
                super(WorkCode.RESOLVE_SESSION_BUTTON, new Color(0, 153, 51), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();
                // Open the resolution page in the browser
                systemTray.openApplication();
                LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to resolve session from notification");
            }
        }

        // Dismiss button
        class DismissButton extends NotificationButton {
            DismissButton() {
                super(WorkCode.DISMISS_BUTTON, new Color(204, 51, 0), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();
                LoggerUtil.info(SystemNotificationService.this.getClass(), "User dismissed resolution reminder");
            }
        }

        buttonsPanel.add(new ResolveSessionButton().create());
        buttonsPanel.add(new DismissButton().create());
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

    // Functional interface for providing buttons to dialogs
    @FunctionalInterface
    public interface ButtonsProvider {
        void addButtons(DialogComponents components, String username, Integer userId, Integer finalMinutes);
    }
}