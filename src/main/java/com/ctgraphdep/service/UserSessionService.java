package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.enums.SyncStatus;

import com.ctgraphdep.model.*;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class UserSessionService {
    private final DataAccessService dataAccess;
    private final UserWorkTimeService userWorkTimeService;
    private final SessionMonitorService sessionMonitorService;
    private final UserService userService;
    private final PathConfig pathConfig;
    private final ReentrantLock sessionLock = new ReentrantLock();
    private final Map<String, WorkUsersSessionsStates> userSessions = new ConcurrentHashMap<>();
    private final Map<Integer, WorkUsersSessionsStates> activeSessions = new ConcurrentHashMap<>();

    //Core service methods
    public UserSessionService(DataAccessService dataAccess, UserWorkTimeService userWorkTimeService,
                              @Lazy SessionMonitorService sessionMonitorService, UserService userService, PathConfig pathConfig) {
        this.dataAccess = dataAccess;
        this.userWorkTimeService = userWorkTimeService;
        this.sessionMonitorService = sessionMonitorService;
        this.userService = userService;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public WorkUsersSessionsStates getCurrentSession(String username, Integer userId) {
        // Clear any stale sessions first
        clearStaleSession(username);

        // Check from userSessions map first
        WorkUsersSessionsStates session = userSessions.get(username);
        if (session != null) {
            return session;
        }

        try {
            session = resolveSession(username, userId);

            // Check if session is from a previous day
            if (isPreviousDaySession(session)) {
                handlePreviousDaySession(session);
            }

            userSessions.put(username, session);
            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error getting session for user %s: %s", username, e.getMessage()));

            // Create new session as fallback
            session = initializeSession(username, userId, LocalDateTime.now());
            session.setSessionStatus(WorkCode.WORK_OFFLINE);
            saveSession(username, session);
            userSessions.put(username, session);
            return session;
        }
    }
    private boolean isPreviousDaySession(WorkUsersSessionsStates session) {
        if (session == null || session.getDayStartTime() == null || WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
            return false;
        }

        LocalDate sessionDate = session.getDayStartTime().toLocalDate();
        LocalDate today = LocalDateTime.now().toLocalDate();

        return sessionDate.isBefore(today);
    }
    private void handlePreviousDaySession(WorkUsersSessionsStates session) {
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Handling previous day session for user %s from %s",
                            session.getUsername(),
                            session.getDayStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE)));

            // Calculate default values for the session
            SessionCalculationResult calculationResult = calculateDefaultSessionValues(session);

            // Update and persist the session
            updateAndPersistSession(session, calculationResult);

            LoggerUtil.info(this.getClass(),
                    String.format("Successfully handled previous day session for user %s",
                            session.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error handling previous day session: %s", e.getMessage()));
        }
    }
    private SessionCalculationResult calculateDefaultSessionValues(WorkUsersSessionsStates session) {
        // Get user's schedule
        User user = userService.getUserById(session.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Calculate default duration based on schedule
        int scheduleDuration = Objects.equals(user.getSchedule(), WorkCode.INTERVAL_HOURS_C) ? 510 : user.getSchedule() * 60;
        // Direct conversion for other schedules
        // 8.5 hours in minutes for 8-hour schedule

        // Store original values
        LocalDateTime originalStartTime = session.getDayStartTime();
        int originalTempStopCount = session.getTemporaryStopCount();
        int originalTempStopMinutes = session.getTotalTemporaryStopMinutes();
        List<TemporaryStop> originalTempStops = session.getTemporaryStops();
        LocalDateTime originalLastTempStopTime = session.getLastTemporaryStopTime();

        // Calculate end time and work time results
        LocalDateTime calculatedEndTime = originalStartTime.plusMinutes(scheduleDuration);
        WorkTimeCalculationResult workTimeResult =
                CalculateWorkHoursUtil.calculateWorkTime(scheduleDuration, user.getSchedule());

        return new SessionCalculationResult(
                scheduleDuration,
                calculatedEndTime,
                workTimeResult,
                originalStartTime,
                originalTempStopCount,
                originalTempStopMinutes,
                originalTempStops,
                originalLastTempStopTime
        );
    }
    private void updateAndPersistSession(WorkUsersSessionsStates session, SessionCalculationResult calc) {
        // Update session with calculated values
        session.setSessionStatus(WorkCode.WORK_OFFLINE);
        session.setDayEndTime(calc.getCalculatedEndTime());
        session.setCurrentStartTime(calc.getOriginalStartTime());
        session.setTotalWorkedMinutes(calc.getScheduleDuration());
        session.setFinalWorkedMinutes(calc.getWorkTimeResult().getProcessedMinutes());
        session.setTotalOvertimeMinutes(calc.getWorkTimeResult().getOvertimeMinutes());
        session.setLunchBreakDeducted(calc.getWorkTimeResult().isLunchDeducted());
        session.setWorkdayCompleted(true);
        session.setLastActivity(LocalDateTime.now());

        // Preserve temporary stop information
        session.setTemporaryStopCount(calc.getOriginalTempStopCount());
        session.setTotalTemporaryStopMinutes(calc.getOriginalTempStopMinutes());
        session.setTemporaryStops(calc.getOriginalTempStops());
        session.setLastTemporaryStopTime(calc.getOriginalLastTempStopTime());

        // Save updated session
        saveSession(session.getUsername(), session);

        // End the day with calculated values for the original date
        endDay(session.getUsername(),
                session.getUserId(),
                calc.getWorkTimeResult().getFinalTotalMinutes());
    }

    //Session Initialization & Cleanup
    public void startDay(String username, Integer userId) {
        executeWithLock(() -> {
            // Clear any existing session
            userSessions.remove(username);
            activeSessions.remove(userId);

            // Create fresh session
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(WorkCode.BUFFER_MINUTES);
            WorkUsersSessionsStates newSession = initializeSession(username, userId, startTime);
            saveSession(username, newSession);
            createWorktimeEntry(username, userId, newSession, username);

            // Start session monitoring
            sessionMonitorService.startMonitoring(username);

            LoggerUtil.info(this.getClass(),
                    String.format("Started new session for user %s (start time set to %s)",
                            username, startTime));

            return newSession;
        });
    }
    private WorkUsersSessionsStates initializeSession(String username, Integer userId, LocalDateTime startTime) {
        WorkUsersSessionsStates session = new WorkUsersSessionsStates();
        session.setUserId(userId);
        session.setUsername(username);
        session.setSessionStatus(WorkCode.WORK_ONLINE);
        session.setDayStartTime(startTime);
        session.setCurrentStartTime(startTime);
        session.setTotalWorkedMinutes(0);
        session.setFinalWorkedMinutes(0);
        session.setTotalOvertimeMinutes(0);
        session.setLunchBreakDeducted(false);
        session.setWorkdayCompleted(false);
        session.setTemporaryStopCount(0);
        session.setTotalTemporaryStopMinutes(0);
        session.setTemporaryStops(new ArrayList<>());
        session.setLastTemporaryStopTime(null);
        session.setLastActivity(LocalDateTime.now());
        return session;
    }
    private void clearStaleSession(String username) {
        WorkUsersSessionsStates existingSession = userSessions.get(username);
        if (existingSession != null && WorkCode.WORK_OFFLINE.equals(existingSession.getSessionStatus())) {
            userSessions.remove(username);
        }
    }
    private WorkUsersSessionsStates resolveSession(String username, Integer userId) {
        try {
            // Try to read local session first
            WorkUsersSessionsStates localSession = dataAccess.readLocalSessionFile(username, userId);

            if (localSession != null) {
                LoggerUtil.debug(this.getClass(),
                        String.format("Found local session for user %s", username));
                return localSession;
            }

            // If no local session and network is available, try specific user's network session
            if (pathConfig.isNetworkAvailable()) {
                // Get the exact network session path for this specific user
                Path networkSessionPath = pathConfig.getNetworkSessionPath(username, userId);

                LoggerUtil.debug(this.getClass(),
                        String.format("Attempting to read network session from path: %s", networkSessionPath));

                // Check if the specific user's network session file exists
                if (Files.exists(networkSessionPath) && Files.size(networkSessionPath) > 0) {
                    try {
                        // Read network session using existing method
                        WorkUsersSessionsStates networkSession =
                                dataAccess.readNetworkSessionFile(username, userId);

                        if (networkSession != null &&
                                username.equals(networkSession.getUsername()) &&
                                userId.equals(networkSession.getUserId())) {

                            // Sync network session to local
                            dataAccess.writeLocalSessionFile(networkSession);

                            LoggerUtil.info(this.getClass(),
                                    String.format("Initialized local session from network for user %s", username));
                            return networkSession;
                        }
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(),
                                String.format("Error reading network session for user %s: %s",
                                        username, e.getMessage()));
                    }
                } else {
                    LoggerUtil.debug(this.getClass(),
                            String.format("No network session file found for user %s", username));
                }
            }

            // If no session exists, create new one
            WorkUsersSessionsStates newSession = initializeSession(username, userId, LocalDateTime.now());
            newSession.setSessionStatus(WorkCode.WORK_OFFLINE);
            saveSession(username, newSession);

            LoggerUtil.info(this.getClass(),
                    String.format("Created new offline session for user %s", username));

            return newSession;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Critical error resolving session for user %s: %s", username, e.getMessage()));

            // Fallback to creating a new offline session
            WorkUsersSessionsStates fallbackSession = initializeSession(username, userId, LocalDateTime.now());
            fallbackSession.setSessionStatus(WorkCode.WORK_OFFLINE);
            saveSession(username, fallbackSession);

            return fallbackSession;
        }
    }

    //Session State Management
    public void saveSession(String username, WorkUsersSessionsStates session) {
        try {
            // Use DataAccessService to write session file
            dataAccess.writeLocalSessionFile(session);

            // Update in-memory session map
            userSessions.put(username, session);

            LoggerUtil.debug(this.getClass(),
                    String.format("Saved session for user %s", username));
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), "Session persistence failed", e);
        }
    }
    private boolean isValidSessionForOperation(WorkUsersSessionsStates session, String expectedStatus) {
        return session != null && expectedStatus.equals(session.getSessionStatus());
    }

    //Temporary Stop Operations
    public void startTemporaryStop(String username, Integer userId) {
        executeWithLock(() -> {
            LoggerUtil.info(this.getClass(),
                    "Starting temporary stop for user: " + username);

            WorkUsersSessionsStates session = getCurrentSession(username, userId);
            // Log before state change
            LoggerUtil.debug(this.getClass(),
                    "Current session status: " + session.getSessionStatus());

            if (isValidSessionForOperation(session, WorkCode.WORK_ONLINE)) {
                LocalDateTime now = LocalDateTime.now();
                updateSessionForTemporaryStop(session, now);
                saveSession(username, session);

                // Log after state change
                LoggerUtil.debug(this.getClass(),
                        "New session status: " + session.getSessionStatus());
            }
            return null;
        });
    }
    public void resumeFromTemporaryStop(String username, Integer userId) {
        executeWithLock(() -> {
            WorkUsersSessionsStates session = getCurrentSession(username, userId);
            if (isValidSessionForOperation(session, WorkCode.WORK_TEMPORARY_STOP)) {
                LocalDateTime now = LocalDateTime.now();
                updateSessionForResume(session, now);
                saveSession(username, session);

                LoggerUtil.info(this.getClass(), String.format("Resumed session for user %s after temporary stop", username));
            }
            return null;
        });
    }
    private void updateSessionForTemporaryStop(WorkUsersSessionsStates session, LocalDateTime now) {
        // Calculate work minutes from last start to now
        int workedMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(session.getCurrentStartTime(), now);

        // Add only actual work minutes to total
        session.setTotalWorkedMinutes((session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0) + workedMinutes);

        // Create and add new temporary stop entry
        TemporaryStop stop = new TemporaryStop();
        stop.setStartTime(now);

        // Initialize temporaryStops if null
        if (session.getTemporaryStops() == null) {
            session.setTemporaryStops(new ArrayList<>());
        }
        session.getTemporaryStops().add(stop);

        // Update session state
        session.setSessionStatus(WorkCode.WORK_TEMPORARY_STOP);
        session.setLastTemporaryStopTime(now);
        session.setTemporaryStopCount(session.getTemporaryStopCount() + 1);
        session.setLastActivity(now);

    }

    public boolean hasCompletedSessionForToday(String username, Integer userId) {
        WorkUsersSessionsStates existingSession = dataAccess.readLocalSessionFile(username, userId);

        return existingSession != null &&
                existingSession.getDayStartTime() != null &&
                existingSession.getDayStartTime().toLocalDate().equals(LocalDate.now()) &&
                WorkCode.WORK_OFFLINE.equals(existingSession.getSessionStatus()) &&
                existingSession.getWorkdayCompleted();
    }

    public void resumePreviousSession(String username, Integer userId) {
        executeWithLock(() -> {
            // Get the existing session
            WorkUsersSessionsStates existingSession = dataAccess.readLocalSessionFile(username, userId);

            if (existingSession == null ||
                    !WorkCode.WORK_OFFLINE.equals(existingSession.getSessionStatus()) ||
                    !existingSession.getWorkdayCompleted()) {
                throw new IllegalStateException("No completed session found to resume");
            }

            // Record the total time worked before resuming
            int previousWorkedMinutes = existingSession.getTotalWorkedMinutes();
            LocalDateTime previousEndTime = existingSession.getDayEndTime();

            // Create a temporary stop to account for the break between end and resume
            if (previousEndTime != null) {
                // Create a new temporary stop entry for the break
                TemporaryStop breakStop = new TemporaryStop();
                breakStop.setStartTime(previousEndTime);
                breakStop.setEndTime(LocalDateTime.now());
                breakStop.setDuration(CalculateWorkHoursUtil.calculateMinutesBetween(
                        previousEndTime, LocalDateTime.now()));

                // Add this break to the temporary stops list
                if (existingSession.getTemporaryStops() == null) {
                    existingSession.setTemporaryStops(new ArrayList<>());
                }
                existingSession.getTemporaryStops().add(breakStop);

                // Update temporary stop count and total stop minutes
                existingSession.setTemporaryStopCount(
                        existingSession.getTemporaryStopCount() != null ?
                                existingSession.getTemporaryStopCount() + 1 : 1);

                int totalStopMinutes = existingSession.getTemporaryStops().stream()
                        .mapToInt(stop -> stop.getDuration() != null ? stop.getDuration() : 0)
                        .sum();
                existingSession.setTotalTemporaryStopMinutes(totalStopMinutes);
            }

            // Set session status back to active
            existingSession.setSessionStatus(WorkCode.WORK_ONLINE);

            // Set the current start time to now
            existingSession.setCurrentStartTime(LocalDateTime.now());

            // Clear day end time since we're still working
            existingSession.setDayEndTime(null);

            // Session is no longer completed
            existingSession.setWorkdayCompleted(false);

            // Update the activity time
            existingSession.setLastActivity(LocalDateTime.now());

            // Save the updated session
            saveSession(username, existingSession);

            // Update the worktime entry to IN_PROCESS again
            updateWorktimeEntryForResume(username, existingSession);

            // Start monitoring again
            sessionMonitorService.startMonitoring(username);

            LoggerUtil.info(this.getClass(),
                    String.format("Resumed previous session for user %s", username));

            return existingSession;
        });
    }

    private void updateWorktimeEntryForResume(String username, WorkUsersSessionsStates session) {
        LocalDate workDate = session.getDayStartTime().toLocalDate();

        try {
            // Get existing entries for this day
            List<WorkTimeTable> entries = loadUserEntries(username, workDate.getYear(), workDate.getMonthValue());

            // Find the entry for this specific day
            WorkTimeTable existingEntry = entries.stream().filter(e -> e.getWorkDate().equals(workDate))
                    .findFirst().orElse(null);

            if (existingEntry != null) {
                // Update status to in-process
                existingEntry.setAdminSync(SyncStatus.USER_IN_PROCESS);

                // Clear end time to match session
                existingEntry.setDayEndTime(null);

                // Copy temporary stop info from session
                existingEntry.setTemporaryStopCount(session.getTemporaryStopCount());
                existingEntry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());

                // Save the updated entry
                userWorkTimeService.saveWorkTimeEntry(
                        username,
                        existingEntry,
                        workDate.getYear(),
                        workDate.getMonthValue(),
                        username);

                LoggerUtil.info(this.getClass(),
                        String.format("Updated worktime entry for resumed session: %s", username));
            } else {
                LoggerUtil.warn(this.getClass(),
                        String.format("No existing worktime entry found for user %s on %s",
                                username, workDate));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Failed to update worktime entry for resumed session: %s", e.getMessage()));
        }
    }

    // Add this method to UserSessionService
    private List<WorkTimeTable> loadUserEntries(String username, int year, int month) {
        try {
            return userWorkTimeService.loadUserEntries(username, year, month, username);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading user entries for %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private void updateSessionForResume(WorkUsersSessionsStates session, LocalDateTime now) {
        int stopMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(
                session.getLastTemporaryStopTime(), now);

        // Update the last temporary stop entry
        if (!session.getTemporaryStops().isEmpty()) {
            TemporaryStop lastStop = session.getTemporaryStops().get(session.getTemporaryStops().size() - 1);
            lastStop.setEndTime(now);
            lastStop.setDuration(stopMinutes);
        }

        // Update total temporary stop minutes by summing all stop durations
        int totalStopMinutes = session.getTemporaryStops().stream()
                .mapToInt(stop -> stop.getDuration() != null ? stop.getDuration() : 0)
                .sum();
        session.setTotalTemporaryStopMinutes(totalStopMinutes);

        // Calculate final worked minutes (raw work minus stops)
        int actualMinutes = session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0;
        session.setFinalWorkedMinutes(actualMinutes);

        // Update session state for resuming work
        session.setSessionStatus(WorkCode.WORK_ONLINE);
        session.setCurrentStartTime(now);  // Reset start time for next work period
        session.setLastActivity(now);
    }

    //End Session Operations
    public void endDay(String username, Integer userId, Integer finalMinutes) {
        executeWithLock(() -> {
            try {
                WorkUsersSessionsStates session = loadAndValidateSession(username, userId);
                if (session != null) {
                    processSessionEnd(session, finalMinutes);
                    updateWorkTimeAndPersist(session);
                    cleanupSession(username, userId);
                    logSessionEnd(username, finalMinutes);
                }
            } catch (Exception e) {
                handleEndDayError(username, e);
            }
            return null;
        });
    }
    private WorkUsersSessionsStates loadAndValidateSession(String username, Integer userId) {
        try {
            // Read local session file
            WorkUsersSessionsStates session = dataAccess.readLocalSessionFile(username, userId);

            // Validate session status
            if (session == null || !WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                LoggerUtil.warn(this.getClass(),
                        String.format("No active session found for user %s", username));
                return null;
            }

            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading session for user %s: %s", username, e.getMessage()));
            return null;
        }
    }
    private void processSessionEnd(WorkUsersSessionsStates session, Integer finalMinutes) {
        LocalDateTime now = LocalDateTime.now();

        // Get current values before modifying session
        int currentWorkedMinutes = session.getTotalWorkedMinutes();
        int currentOvertimeMinutes = session.getTotalOvertimeMinutes() != null ? session.getTotalOvertimeMinutes() : 0;

        // Update session state
        session.setSessionStatus(WorkCode.WORK_OFFLINE);
        session.setDayEndTime(now);
        session.setTotalWorkedMinutes(currentWorkedMinutes);  // Preserve total worked minutes
        session.setFinalWorkedMinutes(finalMinutes);          // Set final minutes as provided
        session.setTotalOvertimeMinutes(currentOvertimeMinutes);  // Preserve overtime minutes
        session.setWorkdayCompleted(true);
        session.setLastActivity(now);

        LoggerUtil.debug(this.getClass(), String.format("Processing session end - Current Total: %d, Final: %d, Overtime: %d",
                        currentWorkedMinutes, finalMinutes, currentOvertimeMinutes));
    }
    private void updateWorkTimeAndPersist(WorkUsersSessionsStates session) {
        try {
            // Update worktime entry
            updateWorktimeEntry(session.getUsername(), session);

            // Persist session using DataAccessService
            dataAccess.writeLocalSessionFile(session);

            LoggerUtil.info(this.getClass(), String.format("Successfully persisted session for user %s with total minutes: %d",
                            session.getUsername(), session.getTotalWorkedMinutes()));
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Failed to update and persist session for user %s", session.getUsername()), e);
        }
    }
    private void cleanupSession(String username, Integer userId) {
        userSessions.remove(username);
        activeSessions.remove(userId);
        sessionMonitorService.stopMonitoring(username);  // Stop monitoring when session ends
    }
    private void logSessionEnd(String username, Integer finalMinutes) {
        LoggerUtil.info(this.getClass(), String.format("Successfully ended session for user %s with %d minutes", username, finalMinutes));
    }
    private void handleEndDayError(String username, Exception e) {
        LoggerUtil.logAndThrow(this.getClass(), String.format("Failed to end session for user %s", username), e);
    }

    //Work Time Entry Management
    private void createWorktimeEntry(String username, Integer userId, WorkUsersSessionsStates session, String operatingUsername) {
        LocalDate today = LocalDate.now();
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(today);
        entry.setDayStartTime(session.getDayStartTime());
        entry.setTotalWorkedMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setLunchBreakDeducted(false);
        entry.setAdminSync(SyncStatus.USER_IN_PROCESS);
        userWorkTimeService.saveWorkTimeEntry(username, entry, today.getYear(), today.getMonthValue(), operatingUsername);
    }

    private void updateWorktimeEntry(String username, WorkUsersSessionsStates session) {
        LocalDateTime now = LocalDateTime.now();
        WorkTimeTable entry = createWorkTimeEntryFromSession(session, now);
        entry.setAdminSync(SyncStatus.USER_INPUT); // Now set to USER_INPUT when completed
        LocalDate workDate = session.getDayStartTime().toLocalDate();

        // Use the session username for authentication
        userWorkTimeService.saveWorkTimeEntry(username, entry, workDate.getYear(), workDate.getMonthValue(), session.getUsername()  // Pass the username from session
        );

        LoggerUtil.info(this.getClass(), String.format("Updated worktime entry for user %s - Total minutes: %d, Overtime: %d",
                        username, entry.getTotalWorkedMinutes(), entry.getTotalOvertimeMinutes()));
    }
    private WorkTimeTable createWorkTimeEntryFromSession(WorkUsersSessionsStates session, LocalDateTime endTime) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(session.getUserId());
        entry.setWorkDate(session.getDayStartTime().toLocalDate());
        entry.setDayStartTime(session.getDayStartTime());
        entry.setDayEndTime(endTime);
        entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
        entry.setTotalOvertimeMinutes(session.getTotalOvertimeMinutes() != null ? session.getTotalOvertimeMinutes() : 0);
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
        entry.setLunchBreakDeducted(session.getLunchBreakDeducted());
        entry.setAdminSync(SyncStatus.USER_INPUT);
        entry.setTimeOffType(null);
        return entry;
    }

    //Utility Methods
    private <T> void executeWithLock(SessionOperation<T> operation) {
        sessionLock.lock();
        try {
            operation.execute();
        } finally {
            sessionLock.unlock();
        }
    }
    // Functional interface for lock operations
    @FunctionalInterface
    private interface SessionOperation<T> {
        T execute();
    }

}