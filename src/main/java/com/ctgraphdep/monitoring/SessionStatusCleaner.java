package com.ctgraphdep.monitoring;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.db.SessionStatusEntity;
import com.ctgraphdep.repository.SessionStatusRepository;
import com.ctgraphdep.service.DataAccessService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * This component cleans up stale session statuses in the database.
 * It runs on a schedule to ensure the status page reflects reality.
 */
@Component
public class SessionStatusCleaner {

    private final SessionStatusRepository sessionStatusRepository;
    private final PathConfig pathConfig;
    private final DataAccessService dataAccessService;

    @Autowired
    public SessionStatusCleaner(SessionStatusRepository sessionStatusRepository, PathConfig pathConfig, DataAccessService dataAccessService) {
        this.sessionStatusRepository = sessionStatusRepository;
        this.pathConfig = pathConfig;
        this.dataAccessService = dataAccessService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Run every 15 minutes to clean up stale session statuses
    @Scheduled(fixedRate = 900000) // 15 minutes
    @Transactional
    public void cleanupStaleSessions() {
        try {
            LoggerUtil.info(this.getClass(), "Running session status cleanup...");

            // Get all active session statuses from database
            List<SessionStatusEntity> activeSessions = sessionStatusRepository.findByStatusIn(
                    List.of(WorkCode.WORK_ONLINE, WorkCode.WORK_TEMPORARY_STOP)
            );

            // For each active database status, check if the corresponding file shows them as offline
            for (SessionStatusEntity dbStatus : activeSessions) {
                try {
                    String username = dbStatus.getUsername();
                    Integer userId = dbStatus.getUserId();

                    // Skip if missing data
                    if (username == null || userId == null) continue;

                    // Read the actual session file to check real status
                    Path sessionPath = pathConfig.getLocalSessionPath(username, userId);
                    if (Files.exists(sessionPath)) {
                        // Use DataAccessService to read session file
                        WorkUsersSessionsStates fileSession = dataAccessService.readLocalSessionFile(username, userId);

                        // If file shows offline but database shows online, update database
                        if (fileSession != null && WorkCode.WORK_OFFLINE.equals(fileSession.getSessionStatus()) &&
                                !WorkCode.WORK_OFFLINE.equals(dbStatus.getStatus())) {

                            LoggerUtil.info(this.getClass(),
                                    String.format("Fixing mismatched status for user %s: File shows %s but DB shows %s",
                                            username, fileSession.getSessionStatus(), dbStatus.getStatus()));

                            // Update database to match file
                            dbStatus.setStatus(WorkCode.WORK_OFFLINE);
                            dbStatus.setLastUpdated(LocalDateTime.now());
                            sessionStatusRepository.save(dbStatus);
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(),
                            String.format("Error processing session for user %s: %s",
                                    dbStatus.getUsername(), e.getMessage()));
                }
            }
            // Calculate cutoff time for stale sessions (more than 1 hour old)
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1);
            // Identify stale sessions
            List<SessionStatusEntity> staleSessions = activeSessions.stream()
                    .filter(session -> session.getLastUpdated().isBefore(cutoffTime))
                    .toList();

            // Update stale sessions to offline
            for (SessionStatusEntity session : staleSessions) {
                LoggerUtil.info(this.getClass(),
                        String.format("Marking stale session for %s as offline (last updated: %s)",
                                session.getUsername(), session.getLastUpdated()));
                session.setStatus(WorkCode.WORK_OFFLINE);
                session.setLastUpdated(LocalDateTime.now());
                sessionStatusRepository.save(session);
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Session cleanup completed. Updated %d stale sessions to offline.",
                            staleSessions.size()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error cleaning up session statuses: " + e.getMessage(), e);
        }
    }
}
