package com.ctgraphdep.notification.service;

import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Service for managing notification display configuration settings.
 * Reads settings from config/display-settings.properties file.
 */
@Service
@PropertySource(value = "file:./config/display-settings.properties", ignoreResourceNotFound = true)
public class NotificationConfigService {

    private static final String CONFIG_PATH = "./display-settings.properties";

    // Spring-injected values (used as defaults)
    @Value("${notification.monitor:default}")
    private String defaultMonitor;

    @Value("${notification.screen.pos:bottom-right}")
    private String defaultScreenPosition;

    @Value("${notification.enabled:true}")
    private boolean defaultNotificationsEnabled;

    @Value("${notification.width:600}")
    private int defaultNotificationWidth;

    @Value("${notification.height:400}")
    private int defaultNotificationHeight;

    @Value("${notification.opacity:0.0}")
    private float defaultNotificationOpacity;

    @Value("${notification.always.center:false}")
    private boolean defaultAlwaysCenter;

    @Value("${notification.taskbar.fix:true}")
    private boolean defaultTaskbarFix;

    // Getters and setters
    // Runtime values
    @Getter
    private String monitor;
    @Getter
    private String screenPosition;
    @Getter
    private boolean notificationsEnabled;
    @Getter
    private int notificationWidth;
    @Getter
    private int notificationHeight;
    @Getter
    private float notificationOpacity;
    @Getter
    private boolean alwaysCenter;
    @Getter
    private boolean taskbarFix;

    @PostConstruct
    public void init() {
        // Create default config file if it doesn't exist
        createDefaultConfigIfNeeded();

        // Load initial values
        loadConfigValues();

        LoggerUtil.info(this.getClass(), String.format(
                "Notification display settings loaded: monitor=%s, position=%s, enabled=%s",
                monitor, screenPosition, notificationsEnabled));
    }

    /**
     * Load configuration values from file
     */
    private void loadConfigValues() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_PATH)) {
            props.load(in);

            // Load properties with defaults from Spring-injected values
            monitor = props.getProperty("notification.monitor", defaultMonitor);
            screenPosition = props.getProperty("notification.screen.pos", defaultScreenPosition);
            notificationsEnabled = Boolean.parseBoolean(props.getProperty("notification.enabled", String.valueOf(defaultNotificationsEnabled)));

            notificationWidth = Integer.parseInt(props.getProperty("notification.width", String.valueOf(defaultNotificationWidth)));
            notificationHeight = Integer.parseInt(props.getProperty("notification.height", String.valueOf(defaultNotificationHeight)));
            notificationOpacity = Float.parseFloat(props.getProperty("notification.opacity", String.valueOf(defaultNotificationOpacity)));
            alwaysCenter = Boolean.parseBoolean(props.getProperty("notification.always.center", String.valueOf(defaultAlwaysCenter)));
            taskbarFix = Boolean.parseBoolean(props.getProperty("notification.taskbar.fix", String.valueOf(defaultTaskbarFix)));

        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error loading config, using defaults: " + e.getMessage());
            // Use defaults
            monitor = defaultMonitor;
            screenPosition = defaultScreenPosition;
            notificationsEnabled = defaultNotificationsEnabled;
            notificationWidth = defaultNotificationWidth;
            notificationHeight = defaultNotificationHeight;
            notificationOpacity = defaultNotificationOpacity;
            alwaysCenter = defaultAlwaysCenter;
            taskbarFix = defaultTaskbarFix;
        }
    }

    /**
     * Save current configuration to file
     */
    private void saveConfigValues() {
        try {
            // First load existing properties to preserve any we don't explicitly set
            Properties props = new Properties();
            File configFile = new File(CONFIG_PATH);
            if (configFile.exists()) {
                try (FileInputStream in = new FileInputStream(configFile)) {
                    props.load(in);
                }
            }

            // Update with current values
            props.setProperty("notification.monitor", monitor);
            props.setProperty("notification.screen.pos", screenPosition);
            props.setProperty("notification.enabled", String.valueOf(notificationsEnabled));
            props.setProperty("notification.width", String.valueOf(notificationWidth));
            props.setProperty("notification.height", String.valueOf(notificationHeight));
            props.setProperty("notification.opacity", String.valueOf(notificationOpacity));
            props.setProperty("notification.always.center", String.valueOf(alwaysCenter));
            props.setProperty("notification.taskbar.fix", String.valueOf(taskbarFix));

            // Save to file
            try (FileOutputStream out = new FileOutputStream(configFile)) {
                props.store(out, "Notification Display Settings");
            }

            LoggerUtil.info(this.getClass(), "Saved display settings to properties file");
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error saving config: " + e.getMessage());
        }
    }

    private void createDefaultConfigIfNeeded() {
        File configFile = new File(CONFIG_PATH);
        if (!configFile.exists()) {
            try {

                // Write default properties
                Properties props = getProperties();

                try (FileOutputStream out = new FileOutputStream(configFile)) {
                    props.store(out, "Notification Display Settings");
                }

                LoggerUtil.info(this.getClass(), "Created default display-settings.properties file");
            } catch (IOException e) {
                LoggerUtil.error(this.getClass(), "Failed to create default display-settings.properties file: " + e.getMessage());
            }
        }
    }

    private @NotNull Properties getProperties() {
        Properties props = new Properties();
        props.setProperty("notification.monitor", defaultMonitor);
        props.setProperty("notification.screen.pos", defaultScreenPosition);
        props.setProperty("notification.enabled", String.valueOf(defaultNotificationsEnabled));
        props.setProperty("notification.width", String.valueOf(defaultNotificationWidth));
        props.setProperty("notification.height", String.valueOf(defaultNotificationHeight));
        props.setProperty("notification.opacity", String.valueOf(defaultNotificationOpacity));
        props.setProperty("notification.always.center", String.valueOf(defaultAlwaysCenter));
        props.setProperty("notification.taskbar.fix", String.valueOf(defaultTaskbarFix));
        return props;
    }

    /**
     * Toggle notifications on/off and save the setting
     * @return The new state (true = enabled, false = disabled)
     */
    public boolean toggleNotifications() {
        notificationsEnabled = !notificationsEnabled;
        saveConfigValues();
        LoggerUtil.info(this.getClass(), "Notifications " + (notificationsEnabled ? "enabled" : "disabled"));
        return notificationsEnabled;
    }

    // Helper methods
    public boolean isDefaultMonitor() {
        return "default".equalsIgnoreCase(monitor);
    }

    public int getMonitorNumber() {
        if (isDefaultMonitor()) {
            return 0; // Primary monitor
        }
        try {
            return Integer.parseInt(monitor);
        } catch (NumberFormatException e) {
            return 0; // Default to primary monitor on parse error
        }
    }

    // Position helpers
    public boolean isTopPosition() {
        return screenPosition.toLowerCase().startsWith("top");
    }

    public boolean isBottomPosition() {
        return screenPosition.toLowerCase().startsWith("bottom") ||
                (!isTopPosition() && !screenPosition.toLowerCase().startsWith("center"));
    }

    public boolean isRightPosition() {
        return screenPosition.toLowerCase().endsWith("right");
    }

    public boolean isLeftPosition() {
        return screenPosition.toLowerCase().endsWith("left");
    }

    public boolean isCenterPosition() {
        return screenPosition.toLowerCase().contains("center");
    }
}