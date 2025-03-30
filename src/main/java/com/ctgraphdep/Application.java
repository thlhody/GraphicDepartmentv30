package com.ctgraphdep;

import com.ctgraphdep.tray.CTTTSystemTray;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

@SpringBootApplication
@EnableScheduling
public class Application {
    private final CTTTSystemTray systemTray;

    public Application(CTTTSystemTray systemTray) {
        this.systemTray = systemTray;

    }

    public static void main(String[] args) {
        // Disable headless mode
        System.setProperty("java.awt.headless", "false");

        // Configure Spring Boot application
        SpringApplication app = new SpringApplication(Application.class);
        app.setHeadless(false);

        // Add additional properties programmatically
        Properties props = new Properties();
        props.put("spring.main.headless", "false");
        app.setDefaultProperties(props);

        // Run the application
        app.run(args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        LoggerUtil.info(this.getClass(), "Application ready event received");
        LoggerUtil.info(this.getClass(), "Headless mode: " + GraphicsEnvironment.isHeadless());
        LoggerUtil.info(this.getClass(), "java.awt.headless property: " + System.getProperty("java.awt.headless"));

        if (SystemTray.isSupported()) {
            SwingUtilities.invokeLater(() -> {
                try {
                    LoggerUtil.info(this.getClass(), "Initializing System Tray");
                    systemTray.initialize();

                    // Add validation check
                    if (systemTray.getTrayIcon() == null) {
                        LoggerUtil.error(this.getClass(), "System tray initialization failed - tray icon is null");
                    } else {
                        LoggerUtil.info(this.getClass(), "System Tray successfully initialized with icon. Quick Register feature is available");
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Failed to initialize system tray: " + e.getMessage(), e);
                }
            });
        } else {
            LoggerUtil.error(this.getClass(), "System tray not supported on this platform. Headless: " +
                            GraphicsEnvironment.isHeadless() + ", Tray supported: " + SystemTray.isSupported());
        }
    }
}