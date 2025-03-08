package com.ctgraphdep.config;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.service.DataAccessService;
import com.ctgraphdep.service.SessionStatusService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * This component initializes the session status database
 * with data from existing session files when the application starts.
 */
@Component
public class SessionStatusInitializer {

    private final UserService userService;
    private final DataAccessService dataAccessService;
    private final SessionStatusService sessionStatusService;
    private final PathConfig pathConfig;

    @Autowired
    public SessionStatusInitializer(
            UserService userService,
            DataAccessService dataAccessService,
            SessionStatusService sessionStatusService,
            PathConfig pathConfig) {
        this.userService = userService;
        this.dataAccessService = dataAccessService;
        this.sessionStatusService = sessionStatusService;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSessionStatuses() {
        try {
            LoggerUtil.info(this.getClass(), "Initializing session status database from existing files...");

            // Get all users
            List<User> users = userService.getAllUsers();

            // Loop through users and check for session files
            for (User user : users) {
                try {
                    // Try to load from network first if available
                    if (pathConfig.isNetworkAvailable()) {
                        Path networkSessionPath = pathConfig.getNetworkSessionPath(user.getUsername(), user.getUserId());

                        if (Files.exists(networkSessionPath) && Files.size(networkSessionPath) > 0) {
                            WorkUsersSessionsStates networkSession =
                                    dataAccessService.readNetworkSessionFile(user.getUsername(), user.getUserId());

                            if (networkSession != null) {
                                sessionStatusService.updateSessionStatus(networkSession);
                                LoggerUtil.info(this.getClass(),
                                        String.format("Initialized session status from network for user %s", user.getUsername()));
                                continue; // Skip to next user
                            }
                        }
                    }

                    // If network read failed or network is unavailable, try local file
                    Path localSessionPath = pathConfig.getLocalSessionPath(user.getUsername(), user.getUserId());

                    if (Files.exists(localSessionPath) && Files.size(localSessionPath) > 0) {
                        WorkUsersSessionsStates localSession =
                                dataAccessService.readLocalSessionFile(user.getUsername(), user.getUserId());

                        if (localSession != null) {
                            sessionStatusService.updateSessionStatus(localSession);
                            LoggerUtil.info(this.getClass(),
                                    String.format("Initialized session status from local file for user %s", user.getUsername()));
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(),
                            String.format("Error initializing session status for user %s: %s",
                                    user.getUsername(), e.getMessage()), e);
                }
            }

            LoggerUtil.info(this.getClass(), "Session status initialization completed");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during session status initialization: " + e.getMessage(), e);
        }
    }
}