package com.ctgraphdep.tray;

import com.ctgraphdep.notification.api.NotificationEventPublisher;
import com.ctgraphdep.notification.service.NotificationConfigService;
import com.ctgraphdep.notification.service.NotificationDisplayService;
import com.ctgraphdep.notification.service.NotificationCheckerService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.net.URI;
import java.io.IOException;

@Component
public class CTTTSystemTray {

    @Value("${app.url}")
    private String appUrl;

    @Value("${app.url.backup}")
    private String appUrlBackup;

    @Value("${app.title}")
    private String appTitle;

    @Value("${server.port}")
    private String serverPort;

    private final NotificationConfigService notificationConfigService;
    private final NotificationDisplayService notificationDisplayService;
    private final NotificationCheckerService notificationCheckerService;
    private final NotificationEventPublisher eventPublisher;

    private volatile boolean isInitialized = false;
    private final Object trayLock = new Object();
    private TrayIcon trayIcon;
    private volatile long lastOpenTime = 0;
    private static final long DEBOUNCE_DELAY = 500; // milliseconds

    private CheckboxMenuItem notificationsMenuItem;

    public CTTTSystemTray(NotificationConfigService notificationConfigService,
                          @Lazy NotificationDisplayService notificationDisplayService,
                          @Lazy NotificationCheckerService notificationCheckerService,
                          NotificationEventPublisher eventPublisher) {
        this.notificationConfigService = notificationConfigService;
        this.notificationDisplayService = notificationDisplayService;
        this.notificationCheckerService = notificationCheckerService;
        this.eventPublisher = eventPublisher;
    }

    public synchronized void initialize() {
        if (isInitialized) {
            return;
        }

        if (GraphicsEnvironment.isHeadless()) {
            LoggerUtil.info(this.getClass(), "Running in headless mode - system tray disabled");
            isInitialized = true;
            return;
        }

        try {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeAndWait(this::initializeOnEDT);
            } else {
                initializeOnEDT();
            }
            isInitialized = true;
            LoggerUtil.info(this.getClass(), "System tray initialized successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to initialize system tray: " + e.getMessage(), e);
            isInitialized = true;
        }
    }

    private void initializeOnEDT() {
        try {
            if (!SystemTray.isSupported()) {
                LoggerUtil.info(this.getClass(), "System tray is not supported on this platform");
                return;
            }

            // Create default popup menu
            PopupMenu defaultPopupMenu = createPopupMenu();

            // Load and create tray icon
            Image iconImage = loadTrayIcon();
            if (iconImage == null) {
                LoggerUtil.error(this.getClass(), "Failed to load tray icon");
                return;
            }

            // Create and configure tray icon with default menu
            trayIcon = new TrayIcon(iconImage, appTitle, defaultPopupMenu);
            trayIcon.setImageAutoSize(true);

            // Add double-click behavior to open login page
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        openApplication();
                    }
                }
            });

            // Add action listener for notification clicks
            trayIcon.addActionListener(e -> {
                openApplication();
                LoggerUtil.info(this.getClass(), "Tray notification clicked, opening application");
            });

            SystemTray.getSystemTray().add(trayIcon);
            LoggerUtil.info(this.getClass(), "System tray icon created successfully");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in EDT initialization: " + e.getMessage());
        }
    }

    private PopupMenu createPopupMenu() {
        PopupMenu popup = new PopupMenu();

        // Open item - opens the login page
        MenuItem openItem = new MenuItem("Open");
        openItem.addActionListener(e -> openApplication());
        popup.add(openItem);
        popup.addSeparator();

        // Notifications checkbox menu item
        notificationsMenuItem = new CheckboxMenuItem("Enable Notifications", notificationConfigService.isNotificationsEnabled());
        notificationsMenuItem.addItemListener(e -> {
            boolean isEnabled = notificationConfigService.toggleNotifications();
            notificationsMenuItem.setState(isEnabled);

            // Show a tray message to inform the user
            if (trayIcon != null) {
                trayIcon.displayMessage(
                        "Notification Settings",
                        "Notifications are now " + (isEnabled ? "enabled" : "disabled"),
                        TrayIcon.MessageType.INFO
                );
            }
        });
        popup.add(notificationsMenuItem);

        // Refresh Notification item - for fixing broken notification display on laptops
        MenuItem refreshNotificationItem = new MenuItem("Refresh Notification");
        refreshNotificationItem.addActionListener(e -> refreshActiveNotification());
        popup.add(refreshNotificationItem);

        popup.addSeparator();

        // About item
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.addActionListener(e -> openAboutPage());
        popup.add(aboutItem);
        popup.addSeparator();

        // Exit item
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            cleanup();
            System.exit(0);
        });
        popup.add(exitItem);
        return popup;
    }

    // Rest of the class remains unchanged

    private Image loadTrayIcon() {
        try {
            URL resourceUrl = getClass().getResource("/static/icons/ct3logoicon.png");
            if (resourceUrl != null) {
                return ImageIO.read(resourceUrl);
            }
            LoggerUtil.debug(this.getClass(), "No icon found at default location, creating a default one!");
            return createDefaultIcon();
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Failed to load tray icon: " + e.getMessage(), e);
            return createDefaultIcon();
        }
    }

    /**
     * Open the application
     */
    public void openApplication() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastOpenTime < DEBOUNCE_DELAY) {
            // Ignore if called within debounce period
            return;
        }
        lastOpenTime = currentTime;

        String baseUrl = getBaseUrl();
        openUrl(baseUrl + "/login");
    }

    /**
     * Open the about page
     */
    private void openAboutPage() {
        String baseUrl = getBaseUrl();
        openUrl(baseUrl + "/about");
    }

    /**
     * Get the base URL for the application, using the primary URL if available
     * or falling back to the backup URL if necessary
     *
     * @return The base URL to use for application links
     */
    private String getBaseUrl() {
        return (appUrl != null && !appUrl.isEmpty()) ? appUrl : appUrlBackup;
    }

    /**
     * Open a URL in the default browser
     *
     * @param url The URL to open
     */
    private void openUrl(String url) {
        try {
            // Make sure the URL starts with http or https
            if (!url.startsWith("http")) {
                url = "http://" + url;
            }

            URI uri = new URI(url);

            // If no port is specified in the URL, add the port from application properties
            if (uri.getPort() == -1) {
                // Get the host from the URI
                String host = uri.getHost();
                if (host != null) {
                    // Replace the host with host:port
                    url = url.replace(host, host + ":" + serverPort);
                    uri = new URI(url);
                }
            }

            LoggerUtil.info(this.getClass(), "Opening URL: " + url);
            Desktop.getDesktop().browse(uri);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to open URL: " + e.getMessage());
        }
    }
    /**
     * Create a default icon if the icon file cannot be loaded
     */
    private Image createDefaultIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(0, 120, 215));
        g2d.fillRect(0, 0, 16, 16);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 10));
        g2d.drawString("CT", 2, 12);
        g2d.dispose();
        return image;
    }

    /**
     * Clean up system tray resources
     */
    private void cleanup() {
        try {
            if (trayIcon != null && SystemTray.isSupported()) {
                SystemTray.getSystemTray().remove(trayIcon);
            }
            isInitialized = false;
            LoggerUtil.info(this.getClass(), "System tray cleanup completed");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Get the tray icon
     *
     * @return The tray icon
     */
    public TrayIcon getTrayIcon() {
        synchronized (trayLock) {
            return trayIcon;
        }
    }

    /**
     * Refreshes active notifications by completely resetting the notification system.
     * This helps fix broken notification displays on laptops where buttons disappear.
     * Particularly useful for the start day notification that displays during 05-17:00.
     * This method performs a clean refresh by:
     * 1. Unregistering the display subscriber (cleans event queue)
     * 2. Resetting the display service (cleans up UI)
     * 3. Re-registering the display subscriber (fresh subscription)
     * 4. Triggering immediate notification check (re-displays relevant notifications)
     */
    private void refreshActiveNotification() {
        try {
            LoggerUtil.info(this.getClass(), "User requested notification refresh via system tray");

            // STEP 1: Unregister the display service subscriber to clean the event queue
            // This ensures no pending events will be delivered to the old state
            eventPublisher.unregisterSubscriber(notificationDisplayService);
            LoggerUtil.info(this.getClass(), "Unregistered display service from event bus");

            // STEP 2: Reset the notification display service which will:
            // - Close current broken notifications (but preserve business logic)
            // - Clean up timers and dialogs
            // - Prepare for fresh notifications
            notificationDisplayService.resetService();
            LoggerUtil.info(this.getClass(), "Display service reset completed");

            // STEP 3: Re-register the display service subscriber with clean state
            // This creates a fresh subscription without any stale event handlers
            eventPublisher.registerSubscriber(notificationDisplayService);
            LoggerUtil.info(this.getClass(), "Re-registered display service with clean state");

            // STEP 4: Immediately trigger notification check to redisplay relevant notifications
            LoggerUtil.info(this.getClass(), "Triggering immediate notification check after refresh");

            // Run the check in a separate thread to avoid blocking the UI
            new Thread(() -> {
                try {
                    // Small delay to ensure re-registration is complete
                    Thread.sleep(500);
                    notificationCheckerService.checkForNotifications();
                    LoggerUtil.info(this.getClass(), "Immediate notification check completed - notifications should redisplay");
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error during immediate notification check: " + e.getMessage(), e);
                }
            }, "NotificationRefreshCheck").start();

            LoggerUtil.info(this.getClass(), "Notification refresh completed successfully");

            // Show success message to user
            if (trayIcon != null) {
                trayIcon.displayMessage(
                        "Notifications Refreshed",
                        "Notification system has been refreshed successfully",
                        TrayIcon.MessageType.INFO
                );
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error refreshing notifications: " + e.getMessage(), e);

            // Show error message to user
            if (trayIcon != null) {
                trayIcon.displayMessage(
                        "Notification Refresh Error",
                        "Failed to refresh notifications: " + e.getMessage(),
                        TrayIcon.MessageType.ERROR
                );
            }
        }
    }
}