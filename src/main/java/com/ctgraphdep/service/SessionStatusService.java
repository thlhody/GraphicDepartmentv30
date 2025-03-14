package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.db.SessionStatusEntity;
import com.ctgraphdep.repository.SessionStatusRepository;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for managing user session status.
 * This now delegates to UserStatusDbService to avoid concurrent file access issues.
 */
@Service
public class SessionStatusService {

    private final SessionStatusRepository sessionStatusRepository;
    private final UserService userService;
    private final UserStatusDbService userStatusDbService; // Added for delegation

    @Autowired
    public SessionStatusService(SessionStatusRepository sessionStatusRepository,
                                UserService userService,
                                UserStatusDbService userStatusDbService) {
        this.sessionStatusRepository = sessionStatusRepository;
        this.userService = userService;
        this.userStatusDbService = userStatusDbService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Updates a user's session status
    @Transactional
    public void updateSessionStatus(String username, Integer userId, String status, LocalDateTime lastActive) {
        try {
            // Update shared status DB file - this also invalidates the cache
            userStatusDbService.updateUserStatus(username, userId, status, lastActive);

            // For backward compatibility, still update the database repository
            updateDbRepository(username, userId, status, lastActive);

            // Force cache invalidation to ensure status is refreshed immediately
            userStatusDbService.invalidateCache();

            LoggerUtil.debug(this.getClass(),
                    String.format("Updated session status for %s to %s", username, status));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error updating session status for %s: %s", username, e.getMessage()), e);
        }
    }

    // Helper method to update the database repository
    private void updateDbRepository(String username, Integer userId, String status, LocalDateTime lastActive) {
        try {
            User user = userService.getUserByUsername(username).orElse(null);
            if (user == null) {
                LoggerUtil.warn(this.getClass(), "Attempted to update status for unknown user: " + username);
                return;
            }

            // Find existing record or create new one
            SessionStatusEntity entity = sessionStatusRepository.findByUsername(username)
                    .orElse(new SessionStatusEntity());

            // Update entity fields
            entity.setUsername(username);
            entity.setUserId(userId);
            entity.setName(user.getName());
            entity.setStatus(status);
            entity.setLastActive(lastActive);
            entity.setLastUpdated(LocalDateTime.now());

            // Save to database
            sessionStatusRepository.save(entity);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating database repository: " + e.getMessage());
        }
    }

    // Updates a user's session status from a WorkUsersSessionsStates object
    @Transactional
    public void updateSessionStatus(WorkUsersSessionsStates session) {
        if (session == null) return;

        try {
            String status = determineStatus(session.getSessionStatus());

            // Update the file-based status storage
            userStatusDbService.updateUserStatusFromSession(
                    session.getUsername(),
                    session.getUserId(),
                    session.getSessionStatus(),
                    session.getLastActivity()
            );

            // Also update the DB for backward compatibility
            updateSessionStatus(
                    session.getUsername(),
                    session.getUserId(),
                    status,
                    session.getLastActivity()
            );

            // Force cache invalidation
            userStatusDbService.invalidateCache();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error updating session status from session object: %s", e.getMessage()), e);
        }
    }

    /**
     * Get the number of online users
     * Delegates to UserStatusDbService
     */
    public int getOnlineUserCount() {
        return userStatusDbService.getOnlineUserCount();
    }

    /**
     * Get the number of active users (online or temporary stop)
     * Delegates to UserStatusDbService
     */
    public int getActiveUserCount() {
        return userStatusDbService.getActiveUserCount();
    }

    // Helper method to determine status string from work code
    private String determineStatus(String workCode) {
        if (workCode == null) {
            return WorkCode.WORK_OFFLINE;
        }

        return switch (workCode) {
            case WorkCode.WORK_ONLINE -> WorkCode.WORK_ONLINE;
            case WorkCode.WORK_TEMPORARY_STOP -> WorkCode.WORK_TEMPORARY_STOP;
            case WorkCode.WORK_OFFLINE -> WorkCode.WORK_OFFLINE;
            default -> WorkCode.STATUS_UNKNOWN;
        };
    }
}