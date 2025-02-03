package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class SessionPersistenceService {
    private final DataAccessService dataAccess;
    private final PathConfig pathConfig;

    public SessionPersistenceService(DataAccessService dataAccess, PathConfig pathConfig) {
        this.dataAccess = dataAccess;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public void persistSession(WorkUsersSessionsStates session) {
        try {
            // Validate session
            if (session == null || session.getUsername() == null || session.getUserId() == null) {
                throw new IllegalArgumentException("Invalid session: username and userId are required");
            }

            // Use PathConfig to generate local session path
            Path localSessionPath = pathConfig.getLocalSessionPath(session.getUsername(), session.getUserId());

            // Write session using DataAccessService's writeLocalSessionFile method
            dataAccess.writeLocalSessionFile(session);

            LoggerUtil.info(this.getClass(),
                    "Session persisted for user: " + session.getUsername() +
                            " at path: " + localSessionPath);
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), "Failed to persist session: ", e);
        }
    }
}