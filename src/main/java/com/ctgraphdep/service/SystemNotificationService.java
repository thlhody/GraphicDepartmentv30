package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.DialogComponents;
import com.ctgraphdep.model.NotificationButton;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.tray.CTTTSystemTray;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.NotificationBackgroundUtility;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for displaying system notifications to users
 * Handles both dialog and system tray notifications with fallback mechanisms
 */
@Service
public class SystemNotificationService {
    private final CTTTSystemTray systemTray;
    private final UserSessionService userSessionService;
    private final AtomicBoolean userResponded;
    private final PathConfig pathConfig;
    private final SessionMonitorService sessionMonitorService;

    private static final int NOTIFICATION_WIDTH = 600;
    private static final int NOTIFICATION_HEIGHT = 400;
    private static final int BUTTONS_PANEL_HEIGHT = 50;
    private static final int BUTTON_SPACING = 20;
    private final Map<String, LocalDateTime> lastNotificationTimes = new ConcurrentHashMap<>();

    public SystemNotificationService(CTTTSystemTray systemTray, UserSessionService userSessionService, PathConfig pathConfig,
                                     SessionMonitorService sessionMonitorService) {
        this.systemTray = systemTray;
        this.userSessionService = userSessionService;
        this.pathConfig = pathConfig;
        this.sessionMonitorService = sessionMonitorService;

        this.userResponded = new AtomicBoolean(false);
        LoggerUtil.initialize(this.getClass(), null);
    }

    //Shows schedule completion warning to the user
    public void showSessionWarning(String username, Integer userId, Integer finalMinutes) {
        // Only show schedule end warning once
        if (canShowNotification(username, WorkCode.SCHEDULE_END_TYPE, 24 * 60)) { // Once per day
            showNotificationWithFallback(
                    username, userId,
                    WorkCode.NOTICE_TITLE,
                    WorkCode.SESSION_WARNING_MESSAGE,
                    WorkCode.SESSION_WARNING_TRAY,
                    WorkCode.ON_FOR_TEN_MINUTES,
                    false, false, finalMinutes,
                    (components, u, id, minutes) -> addStandardButtons(components, u, id, minutes, false),
                    TrayIcon.MessageType.WARNING
            );
        }
    }

    //Shows hourly overtime warning to the user
    public void showHourlyWarning(String username, Integer userId, Integer finalMinutes) {
        // Show overtime warning hourly
        if (canShowNotification(username, WorkCode.OVERTIME_TYPE, WorkCode.CHECK_INTERVAL)) { // Every hour
            showNotificationWithFallback(
                    username, userId,
                    WorkCode.NOTICE_TITLE,
                    WorkCode.HOURLY_WARNING_MESSAGE,
                    WorkCode.HOURLY_WARNING_TRAY,
                    WorkCode.ON_FOR_FIVE_MINUTES,
                    true, false, finalMinutes,
                    (components, u, id, minutes) -> addStandardButtons(components, u, id, minutes, true),
                    TrayIcon.MessageType.WARNING
            );
        }
    }

    //Shows temporary stop duration warning to the user
    public void showLongTempStopWarning(String username, Integer userId, LocalDateTime tempStopStart) {
        // Show temp stop warning hourly
        if (canShowNotification(username, WorkCode.TEMP_STOP_TYPE, WorkCode.HOURLY_INTERVAL)) { // Every hour
            try {
                int stopMinutes = (int) java.time.Duration.between(tempStopStart, LocalDateTime.now()).toMinutes();
                int hours = stopMinutes / 60;
                int minutes = stopMinutes % 60;

                String formattedMessage = String.format(WorkCode.LONG_TEMP_STOP_WARNING, hours, minutes);
                String trayMessage = String.format(WorkCode.LONG_TEMP_STOP_WARNING_TRAY, hours, minutes);
                showNotificationWithFallback(
                        username, userId,
                        WorkCode.NOTICE_TITLE,
                        formattedMessage,
                        trayMessage,
                        WorkCode.ON_FOR_FIVE_MINUTES,
                        false, true, null,
                        (components, u, id, min) -> addTempStopButtons(components, u, id),
                        TrayIcon.MessageType.WARNING
                );
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error showing temporary stop notification: " + e.getMessage());
            }
        }
    }

    //Shows work day start reminder to the user
    public void showStartDayReminder(String username, Integer userId) {
        // Only show start day reminder once per day
        if (canShowNotification(username, WorkCode.START_DAY_TYPE, WorkCode.ONCE_PER_DAY_TIMER)) { // Once per day
            showNotificationWithFallback(
                    username, userId,
                    WorkCode.START_DAY_TITLE,
                    WorkCode.START_DAY_MESSAGE,
                    WorkCode.START_DAY_MESSAGE_TRAY,
                    WorkCode.ON_FOR_TWELVE_HOURS,
                    false, false, null,
                    (components, u, id, min) -> addStartDayButtons(components, u, id),
                    TrayIcon.MessageType.INFO
            );
        }
    }

    //Shows a test notification dialog for system verification
    public void showTestNotificationDialog() {
        LoggerUtil.debug(this.getClass(), "Showing test notification dialog");

        // Create atomic boolean for tracking user response and dialog display
        final AtomicBoolean testResponded = new AtomicBoolean(false);
        final AtomicBoolean dialogDisplayed = new AtomicBoolean(false);

        SwingUtilities.invokeLater(() -> {
            try {
                // Check if system tray is available
                if (systemTray.getTrayIcon() == null) {
                    LoggerUtil.error(this.getClass(), "System tray icon not available for notifications");
                    return;
                }

                // Only show dialog if system is not headless
                if (!GraphicsEnvironment.isHeadless()) {
                    DialogComponents components = createDialog(
                            WorkCode.TEST_NOTICE,
                            WorkCode.TEST_MESSAGE
                    );

                    // Add test-specific buttons
                    JPanel buttonsPanel = components.buttonsPanel;

                    class OpenWebsiteButton extends NotificationButton {
                        public OpenWebsiteButton() {
                            super(WorkCode.OPEN_WEBSITE, new Color(51, 122, 183), testResponded);
                        }

                        @Override
                        protected void handleAction(ActionEvent e) {
                            components.dialog.dispose();
                            systemTray.openApplication();
                            LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to open website from test notification");
                        }
                    }

                    class DismissButton extends NotificationButton {
                        public DismissButton() {
                            super(WorkCode.DISMISS_BUTTON, new Color(108, 117, 125), testResponded);
                        }

                        @Override
                        protected void handleAction(ActionEvent e) {
                            components.dialog.dispose();
                            LoggerUtil.info(SystemNotificationService.this.getClass(), "User dismissed test notification");
                        }
                    }

                    buttonsPanel.add(new OpenWebsiteButton().create());
                    buttonsPanel.add(new DismissButton().create());

                    try {
                        showDialog(components.dialog);
                        dialogDisplayed.set(true);

                        // Auto-close timer (10 seconds)
                        Timer timer = new Timer(WorkCode.ON_FOR_TEN_SECONDS, e -> {
                            if (!testResponded.get()) {
                                components.dialog.dispose();
                                LoggerUtil.info(this.getClass(), "Test notification auto-dismissed after timeout");
                            }
                        });
                        timer.setRepeats(false);
                        timer.start();
                    } catch (Exception e) {
                        LoggerUtil.error(this.getClass(), "Failed to display dialog notification: " + e.getMessage());
                        dialogDisplayed.set(false);
                    }
                } else {
                    LoggerUtil.info(this.getClass(), "Running in headless mode, skipping dialog notification");
                }

                // Fall back to tray notification if dialog wasn't displayed
                if (!dialogDisplayed.get() && systemTray.getTrayIcon() != null) {
                    systemTray.getTrayIcon().addActionListener(e -> {
                        systemTray.openApplication();
                        LoggerUtil.info(this.getClass(), "User clicked on tray notification to open application");
                    });

                    systemTray.getTrayIcon().displayMessage(WorkCode.TEST_NOTICE, WorkCode.TEST_MESSAGE_TRAY, TrayIcon.MessageType.INFO);

                    LoggerUtil.info(this.getClass(), "Displayed tray notification since dialog couldn't be shown");
                }
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error showing test notification dialog: " + e.getMessage(), e);
            }
        });
    }

// =========================================================================
// NOTIFICATION DISPLAY HELPER METHODS
// =========================================================================

    // Shows notification with fallback to tray if dialog can't be shown
    private void showNotificationWithFallback(String username, Integer userId, String title, String message, String trayMessage,
                                              int timeoutPeriod, boolean isHourly, boolean isTempStop, Integer finalMinutes, ButtonsProvider buttonsProvider, TrayIcon.MessageType messageType) {

        try {
            // Try showing dialog first
            boolean dialogDisplayed = showNotificationDialog(
                    username, userId, finalMinutes, title, message,
                    timeoutPeriod, isHourly, isTempStop, buttonsProvider);

            // Fall back to taskbar notification if dialog fails
            if (!dialogDisplayed && systemTray.getTrayIcon() != null) {
                systemTray.getTrayIcon().displayMessage(title, trayMessage, messageType);
                LoggerUtil.info(this.getClass(), "Displayed tray notification for: " + title);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error showing notification: " + e.getMessage());

            // Even if both notification methods fail, the backup service will still execute
            // the appropriate action after its timeout
            LoggerUtil.info(this.getClass(), "Backup service will handle notification failure");
        }
    }

    // Shows a notification dialog with customizable buttons
    private boolean showNotificationDialog(String username, Integer userId, Integer finalMinutes, String title, String message,
                                           Integer timeoutPeriod, boolean isHourly, boolean isTempStop, ButtonsProvider buttonsProvider) {

        LoggerUtil.debug(this.getClass(), String.format("Showing notification - isHourly: %b, isTempStop: %b", isHourly, isTempStop));

        // Guard clauses
        if (systemTray.getTrayIcon() == null) {
            LoggerUtil.error(this.getClass(), "System tray icon not available");
            return false;
        }

        if (GraphicsEnvironment.isHeadless()) {
            LoggerUtil.info(this.getClass(), "Running in headless mode, can't display dialog");
            return false;
        }

        // Don't show regular notifications during temp stop
        if (!isTempStop) {
            WorkUsersSessionsStates currentSession = userSessionService.getCurrentSession(username, userId);
            if (WorkCode.WORK_TEMPORARY_STOP.equals(currentSession.getSessionStatus())) {
                LoggerUtil.debug(this.getClass(), "Skipping regular notification during temp stop");
                return false;
            }
        }

        userResponded.set(false);
        final AtomicBoolean dialogDisplayed = new AtomicBoolean(false);

        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    DialogComponents components = createDialog(title, message);
                    buttonsProvider.addButtons(components, username, userId, finalMinutes);
                    showDialog(components.dialog);
                    dialogDisplayed.set(true);
                    startAutoCloseTimer(components.dialog, username, userId, finalMinutes, timeoutPeriod, isTempStop);
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Failed to display notification dialog: " + e.getMessage());
                    dialogDisplayed.set(false);
                }
            });
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error invoking dialog on EDT: " + e.getMessage());
            return false;
        }

        return dialogDisplayed.get();
    }

    //Creates a dialog with the specified title and message
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
    }

    // Starts an auto-close timer for the notification dialog
    private void startAutoCloseTimer(JDialog dialog, String username, Integer userId, Integer finalMinutes, Integer timeoutPeriod, boolean isTempStop) {
        Timer timer = new Timer(timeoutPeriod, e -> {
            if (!userResponded.get()) {
                dialog.dispose();
                if (!isTempStop) {
                    publishEndSession(username, userId, finalMinutes);
                    sessionMonitorService.clearMonitoring(username);
                    LoggerUtil.info(this.getClass(), "Auto-ending session due to no response");
                } else {
                    sessionMonitorService.continueTempStop(username, userId);
                    LoggerUtil.info(this.getClass(), "Auto-continuing temporary stop due to no response");
                }
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

// =========================================================================
// BUTTON CONFIGURATION METHODS
// =========================================================================

    // Adds standard notification buttons (Continue Working and End Session)
    private void addStandardButtons(DialogComponents components, String username, Integer userId, Integer finalMinutes, boolean isHourly) {
        JPanel buttonsPanel = components.buttonsPanel;
        JDialog dialog = components.dialog;

        // Continue Working Button
        class ContinueWorkingButton extends NotificationButton {
            ContinueWorkingButton() {
                super(WorkCode.CONTINUE_WORKING, new Color(0, 153, 51), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();
                if (!isHourly) {
                    sessionMonitorService.activateHourlyMonitoring(username);
                }
                LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to continue working");
            }
        }

        // End Session Button
        class EndSessionButton extends NotificationButton {
            EndSessionButton() {
                super(WorkCode.END_SESSION, new Color(204, 51, 0), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                publishEndSession(username, userId, finalMinutes);
                dialog.dispose();
                LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to end session");
            }
        }

        buttonsPanel.add(new ContinueWorkingButton().create());
        buttonsPanel.add(new EndSessionButton().create());
    }

    // Adds temporary stop specific buttons
    private void addTempStopButtons(DialogComponents components, String username, Integer userId) {
        JPanel buttonsPanel = components.buttonsPanel;
        JDialog dialog = components.dialog;

        // Continue Break Button
        class ContinueBreakButton extends NotificationButton {
            ContinueBreakButton() {
                super(WorkCode.CONTINUE_BREAK, new Color(0, 153, 51), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();
                sessionMonitorService.continueTempStop(username, userId);
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
                sessionMonitorService.resumeFromTempStop(username, userId);
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
                // First resume from temp stop, then end session
                sessionMonitorService.resumeFromTempStop(username, userId);
                sessionMonitorService.endSession(username, userId);
                LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to end session from temporary stop");
            }
        }

        buttonsPanel.add(new ContinueBreakButton().create());
        buttonsPanel.add(new ResumeWorkButton().create());
        buttonsPanel.add(new EndSessionButton().create());
    }

    // Adds start day dialog specific buttons
    private void addStartDayButtons(DialogComponents components, String username, Integer userId) {
        JPanel buttonsPanel = components.buttonsPanel;
        JDialog dialog = components.dialog;

        // Start Work Button
        class StartWorkButton extends NotificationButton {
            StartWorkButton() {
                super(WorkCode.START_WORK, new Color(0, 153, 51), SystemNotificationService.this.userResponded);
            }

            @Override
            protected void handleAction(ActionEvent e) {
                dialog.dispose();
                startWorkDay(username, userId);
                LoggerUtil.info(SystemNotificationService.this.getClass(), "User chose to start work day");
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

// =========================================================================
// UTILITY METHODS
// =========================================================================

    //Starts the work day for the specified user
    private void startWorkDay(String username, Integer userId) {
        try {
            userSessionService.startDay(username, userId);
            LoggerUtil.info(this.getClass(), String.format("Started work day for user %s through start day notification", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to start work day through notification: " + e.getMessage());
        }
    }

    //Publishes the end session event to user session service
    private void publishEndSession(String username, Integer userId, Integer finalMinutes) {
        try {
            // Get username from session file
            Path sessionPath = pathConfig.getLocalSessionPath(username, userId);
            String sessionFilename = sessionPath.getFileName().toString();
            String extractedUsername = extractUsernameFromSessionFile(sessionFilename);

            if (extractedUsername != null) {
                userSessionService.endDay(extractedUsername, userId, finalMinutes);
                sessionMonitorService.clearMonitoring(username);
                LoggerUtil.info(this.getClass(), String.format("Successfully ended session through notification for user %s", extractedUsername));
            } else {
                LoggerUtil.error(this.getClass(), "Failed to extract username from session file: " + sessionFilename);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to end session through notification: " + e.getMessage());
        }
    }

    //Extracts username from session filename
    private String extractUsernameFromSessionFile(String filename) {
        try {
            String[] parts = filename.replace("session_", "").replace(".json", "").split("_");
            if (parts.length >= 2) {
                return parts[0];
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error extracting username from filename: " + filename);
        }
        return null;
    }

    // Creates a unique notification key for rate limiting
    private String getNotificationKey(String username, String notificationType) {
        return username + "_" + notificationType;
    }

    // Checks if a notification can be shown based on time interval
    public boolean canShowNotification(String username, String notificationType, Integer intervalMinutes) {
        String key = getNotificationKey(username, notificationType);
        LocalDateTime lastTime = lastNotificationTimes.get(key);

        if (lastTime == null) {
            lastNotificationTimes.put(key, LocalDateTime.now());
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        long minutesSinceLastNotification = ChronoUnit.MINUTES.between(lastTime, now);

        if (minutesSinceLastNotification >= intervalMinutes) {
            lastNotificationTimes.put(key, now);
            return true;
        }
        return false;
    }

    //Functional interface for providing buttons to dialogs
    @FunctionalInterface
    private interface ButtonsProvider {
        void addButtons(DialogComponents components, String username, Integer userId, Integer finalMinutes);
    }
}