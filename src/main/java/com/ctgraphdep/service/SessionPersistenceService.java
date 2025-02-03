package com.ctgraphdep.service;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

@Service
public class SessionPersistenceService {
    private final DataAccessService dataAccess;

    // Constructor injection of ObjectMapper
    public SessionPersistenceService(
            DataAccessService dataAccess) {
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public void persistSession(WorkUsersSessionsStates session) {
        try {
            // Existing validation
            if (session == null || session.getUsername() == null || session.getUserId() == null) {
                throw new IllegalArgumentException("Invalid session: username and userId are required");
            }

            // Write serialized session using DataAccessService
            dataAccess.writeLocalSessionFile(session);

            LoggerUtil.info(this.getClass(),
                    "Session persisted for user: " + session.getUsername());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Unexpected error persisting session for user " +
                            (session != null ? session.getUsername() : "unknown"), e);
        }
    }
}