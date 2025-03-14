package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.SaveSessionCommand;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Component responsible for handling sessions at midnight.
 * Refactored to use the command pattern for all operations.
 */
@Component
public class SessionMidnightHandler {

    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final UserService userService;

    public SessionMidnightHandler(
            SessionCommandService commandService,
            SessionCommandFactory commandFactory,
            UserService userService) {
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.userService = userService;
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
            List<User> activeUsers = users.stream().filter(this::hasActiveSession).toList();

            LoggerUtil.info(this.getClass(), String.format("Found %d active sessions at midnight", activeUsers.size()));

            // Process each active session
            for (User user : activeUsers) {
                handleMidnightSessionEnd(user);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during midnight session check: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a user has an active session using command pattern
     */
    private boolean hasActiveSession(User user) {
        try {
            // Create and execute query to get current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(user.getUsername(), user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            return session != null && (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) || WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking active session for %s: %s", user.getUsername(), e.getMessage()));
            return false;
        }
    }

    /**
     * Handle a midnight session end for a user using command pattern
     */
    private void handleMidnightSessionEnd(User user) {
        try {
            // Create and execute query to get current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(user.getUsername(), user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            if (session == null) {
                return;
            }

            LoggerUtil.info(this.getClass(), String.format("Processing midnight session end for user %s", user.getUsername()));

            // Mark session as offline but DO NOT calculate values - we'll let user decide next day
            session.setSessionStatus(WorkCode.WORK_OFFLINE);

            // Mark as not completed - will need resolution
            session.setWorkdayCompleted(false);
            session.setLastActivity(LocalDateTime.now());

            // Save the session using SaveSessionCommand
            SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(session);
            commandService.executeCommand(saveCommand);

            LoggerUtil.info(this.getClass(), String.format("Completed midnight session end for user %s - will require resolution", user.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error handling midnight session end for %s: %s", user.getUsername(), e.getMessage()), e);
        }
    }
}