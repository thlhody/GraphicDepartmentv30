package com.ctgraphdep.service;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class SessionPersistenceService {
    private final DataAccessService dataAccess;

    public SessionPersistenceService(DataAccessService dataAccess) {
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), "Initializing Session Persistence Service");
    }

    public void persistSession(WorkUsersSessionsStates session, String filePath) {
        try {
            dataAccess.writeFile(Path.of(filePath), session);
            LoggerUtil.info(this.getClass(), "Session persisted for user: " + session.getUsername());
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), "Failed to persist session: " , e);
        }
    }

}