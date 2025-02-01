package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.event.SessionEndEvent;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.tray.CTTTSystemTray;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.NotificationBackgroundUtility;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SystemNotificationService {
    private final CTTTSystemTray systemTray;
    private final SessionMonitorService sessionMonitorService;
    private final UserSessionService userSessionService;
    private final AtomicBoolean userResponded;

    private static final int NOTIFICATION_WIDTH = 600;
    private static final int NOTIFICATION_HEIGHT = 400;
    private static final int BUTTONS_PANEL_HEIGHT = 50;
    private static final Dimension BUTTON_SIZE = new Dimension(160, 35);
    private static final int BUTTON_SPACING = 20;

    public SystemNotificationService(CTTTSystemTray systemTray, @Lazy SessionMonitorService sessionMonitorService, UserSessionService userSessionService) {
        this.systemTray = systemTray;
        this.sessionMonitorService = sessionMonitorService;
        this.userSessionService = userSessionService;
        this.userResponded = new AtomicBoolean(false);
        LoggerUtil.initialize(this.getClass(), null);
    }

    public void showSessionWarning(String username, Integer userId, Integer finalMinutes) {
        if (checkTrayIcon()) return;
        showNotificationDialog(
                username,
                userId,
                finalMinutes,
                WorkCode.NOTICE_TITLE,
                WorkCode.SESSION_WARNING_MESSAGE,
                WorkCode.ON_FOR_TEN_MINUTES,
                false,
                false
        );
    }

    public void showHourlyWarning(String username, Integer userId, Integer finalMinutes) {
        if (checkTrayIcon()) return;
        showNotificationDialog(
                username,
                userId,
                finalMinutes,
                WorkCode.NOTICE_TITLE,
                WorkCode.HOURLY_WARNING_MESSAGE,
                WorkCode.ON_FOR_FIVE_MINUTES,
                true,
                false
        );
    }

    public void showLongTempStopWarning(String username, Integer userId, LocalDateTime tempStopStart) {
        if (checkTrayIcon()) return;

        int stopMinutes = (int) java.time.Duration.between(tempStopStart, LocalDateTime.now()).toMinutes();
        int hours = stopMinutes / 60;
        int minutes = stopMinutes % 60;

        String formattedMessage = String.format(
                WorkCode.LONG_TEMP_STOP_WARNING,
                hours,
                minutes
        );

        showNotificationDialog(
                username,
                userId,
                null,
                WorkCode.NOTICE_TITLE,
                formattedMessage,
                WorkCode.ON_FOR_FIVE_MINUTES,
                false,
                true
        );
    }

    private void showNotificationDialog(
            String username,
            Integer userId,
            Integer finalMinutes,
            String title,
            String message,
            int timeoutPeriod,
            boolean isHourly,
            boolean isTempStop) {
        userResponded.set(false);
        SwingUtilities.invokeLater(() -> {
            DialogComponents components = createDialog(title, message);
            if (isTempStop) {
                addTempStopButtons(components, username, userId);
            } else {
                addButtons(components, username, userId, finalMinutes, isHourly);
            }
            showDialog(components.dialog);
            startAutoCloseTimer(components.dialog, username, userId, finalMinutes, timeoutPeriod, isTempStop);
        });
    }

    private static class DialogComponents {
        final JDialog dialog;
        final JPanel buttonsPanel;

        DialogComponents(JDialog dialog, JPanel buttonsPanel) {
            this.dialog = dialog;
            this.buttonsPanel = buttonsPanel;
        }
    }

    private DialogComponents createDialog(String title, String message) {
        BufferedImage notificationImage = NotificationBackgroundUtility.createNotificationBackground(
                title,
                message
        );

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

    private void addTempStopButtons(DialogComponents components, String username, Integer userId) {
        JPanel buttonsPanel = components.buttonsPanel;

        // Continue Break Button
        JButton continueButton = createButton("Continue Break", new Color(0, 153, 51));
        continueButton.addActionListener(e -> {
            userResponded.set(true);
            components.dialog.dispose();
            sessionMonitorService.continueTempStop(username, userId);
            LoggerUtil.info(this.getClass(), "User chose to continue temporary stop");
        });

        // Resume Work Button
        JButton resumeButton = createButton("Resume Work", new Color(51, 122, 183));
        resumeButton.addActionListener(e -> {
            userResponded.set(true);
            components.dialog.dispose();
            sessionMonitorService.resumeFromTempStop(username, userId);
            LoggerUtil.info(this.getClass(), "User chose to resume work from temporary stop");
        });

        // End Session Button
        JButton endButton = createButton("End Session", new Color(204, 51, 0));
        endButton.addActionListener(e -> {
            userResponded.set(true);
            components.dialog.dispose();
            sessionMonitorService.endSession(username, userId);
            LoggerUtil.info(this.getClass(), "User chose to end session from temporary stop");
        });

        buttonsPanel.add(continueButton);
        buttonsPanel.add(resumeButton);
        buttonsPanel.add(endButton);
    }

    private void addButtons(DialogComponents components, String username, Integer userId, Integer finalMinutes, boolean isHourly) {
        JPanel buttonsPanel = components.buttonsPanel;

        // Continue Working Button
        JButton continueButton = createButton("Continue Working", new Color(0, 153, 51));
        continueButton.addActionListener(e -> {
            userResponded.set(true);
            components.dialog.dispose();
            if (isHourly) {
                sessionMonitorService.markSessionContinued(username, userId);
            }
            LoggerUtil.info(this.getClass(), "User chose to continue working");
        });

        // End Session Button
        JButton endButton = createButton("End Session", new Color(204, 51, 0));
        endButton.addActionListener(e -> {
            userResponded.set(true);
            publishEndSession(username, userId, finalMinutes);
            components.dialog.dispose();
            LoggerUtil.info(this.getClass(), "User chose to end session");
        });

        buttonsPanel.add(continueButton);
        buttonsPanel.add(endButton);
    }

    private JButton createButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setPreferredSize(BUTTON_SIZE);
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);

        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 2, true),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.darker());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });

        return button;
    }

    private boolean checkTrayIcon() {
        if (systemTray.getTrayIcon() == null) {
            LoggerUtil.error(this.getClass(), "System tray icon not available");
            return true;
        }
        return false;
    }

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

    private void startAutoCloseTimer(
            JDialog dialog,
            String username,
            Integer userId,
            Integer finalMinutes,
            int timeoutPeriod,
            boolean isTempStop) {
        Timer timer = new Timer(timeoutPeriod, e -> {
            if (!userResponded.get()) {
                dialog.dispose();
                if (!isTempStop) {
                    publishEndSession(username, userId, finalMinutes);
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

    private void publishEndSession(String username, Integer userId, Integer finalMinutes) {
        try {
            userSessionService.endDay(username, userId, finalMinutes);
            LoggerUtil.info(this.getClass(),
                    String.format("Successfully ended session through notification for user %s", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Failed to end session through notification: " + e.getMessage());
        }
    }
}