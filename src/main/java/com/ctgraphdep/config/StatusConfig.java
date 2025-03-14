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
    public StatusConfig(
            UserStatusDbService userStatusDbService,
            UserService userService) {
        this.userStatusDbService = userStatusDbService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Initialize the user status system once the application is ready.
     * This ensures all users at least have a status file, but only creates them if they don't exist.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeUserStatuses() {
        try {
            LoggerUtil.info(this.getClass(), "Initializing user status system...");

            // Get all users and check for existing status files
            userService.getAllUsers().forEach(user -> {
                try {
                    // Only create initial status file for the user if it doesn't exist
                    // Use DataAccessService directly to check if status file exists
                    if (!userStatusDbService.userStatusExists(user.getUsername(), user.getUserId())) {
                        // Create initial status file for the user with Offline status
                        userStatusDbService.updateUserStatus(
                                user.getUsername(),
                                user.getUserId(),
                                WorkCode.WORK_OFFLINE,
                                null
                        );
                        LoggerUtil.debug(this.getClass(),
                                String.format("Created initial status for user: %s", user.getUsername()));
                    } else {
                        LoggerUtil.debug(this.getClass(),
                                String.format("Status already exists for user: %s", user.getUsername()));
                    }
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