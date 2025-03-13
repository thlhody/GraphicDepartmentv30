package com.ctgraphdep.config;

import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.UserStatusDbService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configuration for the user status system.
 * This initializes the user status files when the application starts.
 */
@Configuration
public class StatusConfig {

    private final UserStatusDbService userStatusDbService;
    private final UserService userService;

    @Autowired
    public StatusConfig(UserStatusDbService userStatusDbService, UserService userService) {
        this.userStatusDbService = userStatusDbService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Initialize the user status system once the application is ready.
     * This ensures all users at least have a status file, even if they're offline.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeUserStatuses() {
        try {
            LoggerUtil.info(this.getClass(), "Initializing user status system...");

            // Get all users and their current status
            userService.getAllUsers().forEach(user -> {
                try {
                    // Create initial status file for the user if it doesn't exist
                    // This helps populate the status page even for offline users
                    userStatusDbService.updateUserStatus(
                            user.getUsername(),
                            user.getUserId(),
                            WorkCode.WORK_OFFLINE,
                            null
                    );

                    LoggerUtil.debug(this.getClass(),
                            String.format("Initialized status for user: %s", user.getUsername()));
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(),
                            String.format("Error initializing status for user %s: %s",
                                    user.getUsername(), e.getMessage()));
                }
            });

            LoggerUtil.info(this.getClass(), "User status system initialization complete");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing user status system: " + e.getMessage(), e);
        }
    }
}