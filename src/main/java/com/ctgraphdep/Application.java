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
    public void initializeSystemTray() {
        // Log headless mode status
        LoggerUtil.info(this.getClass(),
                String.format("Headless mode check - GraphicsEnvironment: %s, System property: %s",
                        GraphicsEnvironment.isHeadless(),
                        System.getProperty("java.awt.headless")));

        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> {
                try {
                    Thread.sleep(1000);
                    systemTray.initialize();
                } catch (Exception e) {
                    LoggerUtil.error(Application.class,
                            "Failed to initialize system tray: " + e.getMessage());
                }
            });
        } else {
            LoggerUtil.error(Application.class,
                    "Still running in headless mode despite configuration! " +
                            "System tray will be disabled. Please check your environment configuration.");
        }
    }
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> {
                try {
                    LoggerUtil.info(this.getClass(), "Initializing System Tray");
                    systemTray.initialize();
                    LoggerUtil.info(this.getClass(), "System Tray initialization completed");
                } catch (Exception e) {
                    LoggerUtil.error(Application.class,
                            "Failed to initialize system tray: " + e.getMessage());
                }
            });
        }
    }
}