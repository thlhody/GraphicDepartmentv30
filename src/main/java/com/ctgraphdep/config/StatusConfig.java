package com.ctgraphdep.config;

import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.ReadFileNameStatusService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.LocalDateTime;

/**
 * Configuration for the user status system.
 * This initializes the user status when the application starts.
 */
@Configuration
public class StatusConfig {

    private final ReadFileNameStatusService readFileNameStatusService;
    private final UserService userService;
    private final TimeValidationService timeValidationService;


    @Autowired
    public StatusConfig(ReadFileNameStatusService readFileNameStatusService, UserService userService, TimeValidationService timeValidationService) {
        this.readFileNameStatusService = readFileNameStatusService;
        this.userService = userService;
        this.timeValidationService = timeValidationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Initialize the user status system once the application is ready.
     * This ensures all users have a default status in the local cache and network flags.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeUserStatuses() {
        try {
            LoggerUtil.info(this.getClass(), "Initializing user status system...");

            // Get all users and initialize their statuses in the local cache only
            userService.getAllUsers().forEach(user -> {
                try {
                    // Create initial status for all users with Offline status in local cache only
                    readFileNameStatusService.initializeUserStatusLocally(
                            user.getUsername(),
                            user.getUserId(),
                            WorkCode.WORK_OFFLINE,
                            getStandardCurrentTime()
                    );
                    //LoggerUtil.debug(this.getClass(), String.format("Initialized status for user: %s", user.getUsername()));
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format("Error initializing status for user %s: %s", user.getUsername(), e.getMessage()));
                }
            });

            // Save the cache after all users have been initialized
            readFileNameStatusService.saveStatusCache();

            LoggerUtil.info(this.getClass(), "User status system initialization complete");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing user status system: " + e.getMessage(), e);
        }
    }

    private LocalDateTime getStandardCurrentTime() {
        // Get standardized time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        return timeValues.getCurrentTime();
    }
}