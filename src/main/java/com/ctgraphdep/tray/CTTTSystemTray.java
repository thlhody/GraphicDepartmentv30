package com.ctgraphdep.tray;

import com.ctgraphdep.model.User;
import com.ctgraphdep.service.DataAccessService;
import com.ctgraphdep.service.AutoLoginService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.net.URI;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class CTTTSystemTray {

    @Value("${app.url}")
    private String appUrl;

    @Value("${app.url.backup}")
    private String appUrlBackup;

    @Value("${app.url.dashboard}")
    private String dashboardUrl;

    @Value("${app.title:CTTT}")
    private String appTitle;

    @Autowired
    private DataAccessService dataAccessService;

    @Autowired
    private AutoLoginService autoLoginService;

    private volatile boolean isInitialized = false;
    private final Object trayLock = new Object();
    private TrayIcon trayIcon;
    private String lastAutoLoginUsername = null;


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

            // Add double-click behavior
            trayIcon.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        openDashboardWithAutoLogin();
                    }
                }
            });

            SystemTray.getSystemTray().add(trayIcon);
            LoggerUtil.info(this.getClass(), "System tray icon created successfully");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in EDT initialization: " + e.getMessage());
        }
    }

    private PopupMenu createPopupMenu() {
        PopupMenu popup = new PopupMenu();

        // Open item
        MenuItem openItem = new MenuItem("Open");
        openItem.addActionListener(e -> openApplication());
        popup.add(openItem);

        // Auto Login Dashboard Item
        MenuItem dashboardItem = new MenuItem("Open Dashboard");
        dashboardItem.addActionListener(e -> openDashboardWithAutoLogin());
        popup.add(dashboardItem);

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

    private Image loadTrayIcon() {
        try {
            URL resourceUrl = getClass().getResource("/static/icons/ct3logoicon.png");
            if (resourceUrl != null) {
                return ImageIO.read(resourceUrl);
            }
            LoggerUtil.debug(this.getClass(), "No icon found at, creating a default one!");
            return createDefaultIcon();
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Failed to load tray icon: " + e.getMessage(), e);
            return createDefaultIcon();
        }
    }

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

    private void openApplication() {
        String urlToOpen = appUrl != null ? appUrl : appUrlBackup;
        openUrl(urlToOpen);
    }

    private void openDashboardWithAutoLogin() {
        try {
            Optional<User> autoLoginUser = findAutoLoginUser();

            if (autoLoginUser.isPresent()) {
                User user = autoLoginUser.get();
                lastAutoLoginUsername = user.getUsername();
                String autoLoginToken = autoLoginService.generateAutoLoginToken(user);
                String url = (dashboardUrl != null ? dashboardUrl : appUrl) +
                        "/autologin?token=" + autoLoginToken;

                openUrl(url);
                LoggerUtil.info(this.getClass(),
                        String.format("Auto-login initiated for user: %s", user.getUsername()));
            } else {
                // Fallback to normal login if no suitable user found
                openApplication();
                LoggerUtil.info(this.getClass(), "No suitable user for auto-login, opening standard login page");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error during auto-login attempt: " + e.getMessage());
            // Fallback to normal application open
            openApplication();
        }
    }

    private Optional<User> findAutoLoginUser() {
        try {
            List<User> localUsers = dataAccessService.readLocalUsers();

            // First try to find the last successful user
            if (lastAutoLoginUsername != null) {
                Optional<User> lastUser = localUsers.stream()
                        .filter(user -> lastAutoLoginUsername.equals(user.getUsername())
                                && !user.isAdmin() && user.getPassword() != null)
                        .findFirst();

                if (lastUser.isPresent()) {
                    return lastUser;
                }
            }

            // Filter: non-admin users with stored credentials
            return localUsers.stream()
                    .filter(user -> !user.isAdmin() && user.getPassword() != null)
                    .findFirst();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error finding auto-login user: " + e.getMessage());
            return Optional.empty();
        }
    }

    private void openUrl(String url) {
        try {
            URI uri = new URI(url);

            // If no port is specified and it's localhost, add the correct port
            if (uri.getPort() == -1 && "localhost".equals(uri.getHost())) {
                url = url.replace("localhost", "localhost:8443");
                uri = new URI(url);
            }

            // Add trailing slash to ensure proper redirection
            if (!url.endsWith("/") && !url.contains("?")) {
                url += "/";
                uri = new URI(url);
            }

            LoggerUtil.info(this.getClass(), "Opening URL: " + url);
            Desktop.getDesktop().browse(uri);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to open URL: " + e.getMessage());
        }
    }

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

    public TrayIcon getTrayIcon() {
        synchronized (trayLock) {
            return trayIcon;
        }
    }
}