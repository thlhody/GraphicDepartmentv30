package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;
import java.awt.TrayIcon;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/**
 * Command to show a test notification dialog
 */
public class ShowTestNotificationCommand implements SessionCommand<Boolean> {

    private final String username;

    public ShowTestNotificationCommand(String username) {
        this.username = username;
    }

    @Override
    public Boolean execute(SessionContext context) {
        LoggerUtil.info(this.getClass(), "Executing test notification command for user: " + username);

        // Create atomic boolean for tracking user response and dialog display
        final AtomicBoolean testResponded = new AtomicBoolean(false);
        final AtomicBoolean dialogDisplayed = new AtomicBoolean(false);

        // Dispatch UI operations to the EDT
        SwingUtilities.invokeLater(() -> {
            try {
                LoggerUtil.info(this.getClass(), "Showing test notification dialog on EDT thread: " + SwingUtilities.isEventDispatchThread());

                // Check if system tray is available
                if (context.getNotificationService().getSystemTray().getTrayIcon() == null) {
                    LoggerUtil.error(this.getClass(), "System tray icon not available for notification");
                    return;
                }

                // Only show dialog if system is not headless
                if (!GraphicsEnvironment.isHeadless()) {
                    // Use the SystemNotificationService to create and show dialog
                    context.getNotificationService().showTestDialogWithButtons(username, testResponded, dialogDisplayed);
                } else {
                    LoggerUtil.info(this.getClass(), "Running in headless mode, skipping dialog notification");
                }

                // Fall back to tray notification if dialog wasn't displayed
                if (!dialogDisplayed.get() && context.getNotificationService().getSystemTray().getTrayIcon() != null) {
                    LoggerUtil.info(this.getClass(), "Falling back to tray notification");

                    context.getNotificationService().getSystemTray().getTrayIcon().displayMessage(
                            WorkCode.TEST_NOTICE,
                            WorkCode.TEST_MESSAGE_TRAY,
                            TrayIcon.MessageType.INFO);

                    LoggerUtil.info(this.getClass(), "Displayed test tray notification");
                }
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error showing test notification dialog: " + e.getMessage(), e);
            }
        });

        // Return true to indicate we've dispatched the test notification
        return true;
    }
}