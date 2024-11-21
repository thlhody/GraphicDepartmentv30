package com.ctgraphdep.service;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

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

    public void saveWorkTimeEntries(List<WorkTimeTable> entries, String filePath, String username) {
        try {
            dataAccess.writeFile(Path.of(filePath), entries);
            LoggerUtil.info(this.getClass(), "Successfully saved worktime entries for user: " + username);
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), "Failed to save worktime entries: ", e);
        }
    }
}