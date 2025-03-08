package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Component responsible for handling sessions at midnight.
 * This ensures that sessions don't carry over to the next day without proper resolution.
 */
@Component
public class SessionMidnightHandler {

    private final UserSessionService userSessionService;
    private final UserService userService;
    private final ContinuationTrackingService continuationTrackingService;

    public SessionMidnightHandler(
            UserSessionService userSessionService,
            UserService userService,
            ContinuationTrackingService continuationTrackingService) {
        this.userSessionService = userSessionService;
        this.userService = userService;
        this.continuationTrackingService = continuationTrackingService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Scheduled task that runs at 11:59 PM to check for active sessions
     * These will be marked for midnight end and require resolution the next day
     */
    @Scheduled(cron = "0 59 23 * * *")
    public void checkActiveSessions() {
        LoggerUtil.info(this.getClass(), "Running midnight session check");

        try {
            // Find all active users
            List<User> users = userService.getAllUsers();

            // Filter to only those with active sessions
            List<User> activeUsers = users.stream()
                    .filter(this::hasActiveSession)
                    .toList();

            LoggerUtil.info(this.getClass(),
                    String.format("Found %d active sessions at midnight", activeUsers.size()));

            // Process each active session
            for (User user : activeUsers) {
                handleMidnightSessionEnd(user);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error during midnight session check: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a user has an active session
     */
    private boolean hasActiveSession(User user) {
        try {
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(
                    user.getUsername(), user.getUserId());

            return session != null &&
                    (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                            WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking active session for %s: %s",
                            user.getUsername(), e.getMessage()));
            return false;
        }
    }

    /**
     * Handle a midnight session end for a user
     * Leverages existing methods in UserSessionService where possible
     */
    private void handleMidnightSessionEnd(User user) {
        try {
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(
                    user.getUsername(), user.getUserId());

            if (session == null) {
                return;
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Processing midnight session end for user %s", user.getUsername()));

            // Mark session as offline but DO NOT calculate values - we'll let user decide next day
            session.setSessionStatus(WorkCode.WORK_OFFLINE);

            // Mark as not completed - will need resolution
            session.setWorkdayCompleted(false);
            session.setLastActivity(LocalDateTime.now());

            // Save the session using UserSessionService
            userSessionService.saveSession(user.getUsername(), session);

            // Record the midnight session end in the continuation tracking service
            continuationTrackingService.recordMidnightSessionEnd(
                    user.getUsername(), user.getUserId());

            LoggerUtil.info(this.getClass(),
                    String.format("Completed midnight session end for user %s - will require resolution",
                            user.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error handling midnight session end for %s: %s",
                            user.getUsername(), e.getMessage()), e);
        }
    }
}