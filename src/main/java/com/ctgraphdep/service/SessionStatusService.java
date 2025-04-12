package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for managing user session status.
 * This now delegates to ReadFileNameStatusService to handle status updates.
 */
@Service
public class SessionStatusService {

    private final ReadFileNameStatusService readFileNameStatusService; // Now using the new service

    @Autowired
    public SessionStatusService(ReadFileNameStatusService readFileNameStatusService) {

        this.readFileNameStatusService = readFileNameStatusService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Updates a user's session status
    @Transactional
    public void updateSessionStatus(String username, Integer userId, String status, LocalDateTime lastActive) {
        try {
            // Update status using the new ReadFileNameStatusService
            readFileNameStatusService.updateUserStatus(username, userId, status, lastActive);

            LoggerUtil.debug(this.getClass(), String.format("Updated session status for %s to %s", username, status));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating session status for %s: %s", username, e.getMessage()), e);
        }
    }

    // Updates a user's session status from a WorkUsersSessionsStates object
    @Transactional
    public void updateSessionStatus(WorkUsersSessionsStates session) {
        if (session == null) return;

        try {
            String status = determineStatus(session.getSessionStatus());

            // Update status using the new ReadFileNameStatusService
            readFileNameStatusService.updateUserStatus(session.getUsername(), session.getUserId(), status, session.getLastActivity());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating session status from session object: %s", e.getMessage()), e);
        }
    }

    /**
     * Get the number of online users
     * Now delegates to ReadFileNameStatusService
     */
    public int getOnlineUserCount() {
        return readFileNameStatusService.getOnlineUserCount();
    }

    /**
     * Get the number of active users (online or temporary stop)
     * Now delegates to ReadFileNameStatusService
     */
    public int getActiveUserCount() {
        return readFileNameStatusService.getActiveUserCount();
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