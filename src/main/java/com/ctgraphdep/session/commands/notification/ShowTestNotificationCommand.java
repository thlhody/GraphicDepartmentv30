package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import java.awt.TrayIcon;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

import com.ctgraphdep.session.SessionContext;

/**
 * Command to show a test notification dialog
 */
public class ShowTestNotificationCommand extends BaseNotificationCommand<Boolean> {

    public ShowTestNotificationCommand(String username) {
        super(username, null);  // userId is not needed for this command
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info("Executing test notification command for user: " + username);

            // Create atomic boolean for tracking user response and dialog display
            final AtomicBoolean testResponded = new AtomicBoolean(false);
            final AtomicBoolean dialogDisplayed = new AtomicBoolean(false);

            // Dispatch UI operations to the EDT
            SwingUtilities.invokeLater(() -> {
                try {
                    info("Showing test notification dialog on EDT thread: " + SwingUtilities.isEventDispatchThread());

                    // Check if system tray is available
                    if (ctx.getNotificationService().getSystemTray().getTrayIcon() == null) {
                        warn("System tray icon not available for notification");
                        return;
                    }

                    // Only show dialog if system is not headless
                    if (!GraphicsEnvironment.isHeadless()) {
                        // Use the SystemNotificationService to create and show dialog
                        ctx.getNotificationService().showTestDialogWithButtons(username, testResponded, dialogDisplayed);
                    } else {
                        info("Running in headless mode, skipping dialog notification");
                    }

                    // Fall back to tray notification if dialog wasn't displayed
                    if (!dialogDisplayed.get() && ctx.getNotificationService().getSystemTray().getTrayIcon() != null) {
                        info("Falling back to tray notification");

                        ctx.getNotificationService().getSystemTray().getTrayIcon().displayMessage(
                                WorkCode.TEST_NOTICE_TITLE,
                                WorkCode.TEST_MESSAGE_TRAY,
                                TrayIcon.MessageType.INFO);

                        info("Displayed test tray notification");
                    }
                } catch (Exception e) {
                    error("Error showing test notification dialog: " + e.getMessage(), e);
                }
            });

            // Return true to indicate we've dispatched the test notification
            return true;
        });
    }
}