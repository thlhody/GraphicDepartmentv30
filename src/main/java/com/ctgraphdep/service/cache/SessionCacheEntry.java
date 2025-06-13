package com.ctgraphdep.service.cache;

import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe cache entry for session data.
 * Separates static values (from commands) and dynamic values (from calculations).
 */
@Data
public class SessionCacheEntry {

    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // === STATIC VALUES (Set by commands, never change until midnight) ===
    private Integer userId;
    private String username;
    private String sessionStatus;
    private LocalDateTime dayStartTime;
    private LocalDateTime dayEndTime;
    private LocalDateTime currentStartTime;
    private Integer temporaryStopCount;
    private List<TemporaryStop> temporaryStops;
    private Boolean workdayCompleted;

    // === DYNAMIC VALUES (Calculated by SessionMonitor) ===
    private Integer totalWorkedMinutes;
    private Integer finalWorkedMinutes;
    private Integer totalOvertimeMinutes;
    private Boolean lunchBreakDeducted;
    private Integer totalTemporaryStopMinutes;
    private LocalDateTime lastTemporaryStopTime;
    private LocalDateTime lastActivity;

    // === CACHE METADATA ===
    private long lastFileRead;
    private long lastCalculationUpdate;
    private boolean initialized;

    /**
     * Default constructor
     */
    public SessionCacheEntry() {
        this.temporaryStops = new ArrayList<>();
        this.initialized = false;
    }

    /**
     * Initialize cache from file data (thread-safe)
     * @param session Session data from file
     */
    public void initializeFromFile(WorkUsersSessionsStates session) {
        lock.writeLock().lock();
        try {
            if (session == null) {
                this.initialized = false;
                return;
            }

            // Update static values
            this.userId = session.getUserId();
            this.username = session.getUsername();
            this.sessionStatus = session.getSessionStatus();
            this.dayStartTime = session.getDayStartTime();
            this.dayEndTime = session.getDayEndTime();
            this.currentStartTime = session.getCurrentStartTime();
            this.temporaryStopCount = session.getTemporaryStopCount();
            this.workdayCompleted = session.getWorkdayCompleted();

            // Deep copy temporary stops to avoid reference issues
            this.temporaryStops = session.getTemporaryStops() != null ?
                    new ArrayList<>(session.getTemporaryStops()) : new ArrayList<>();

            // Update dynamic values
            this.totalWorkedMinutes = session.getTotalWorkedMinutes();
            this.finalWorkedMinutes = session.getFinalWorkedMinutes();
            this.totalOvertimeMinutes = session.getTotalOvertimeMinutes();
            this.lunchBreakDeducted = session.getLunchBreakDeducted();
            this.totalTemporaryStopMinutes = session.getTotalTemporaryStopMinutes();
            this.lastTemporaryStopTime = session.getLastTemporaryStopTime();
            this.lastActivity = session.getLastActivity();

            // Update metadata
            this.lastFileRead = System.currentTimeMillis();
            this.lastCalculationUpdate = System.currentTimeMillis();
            this.initialized = true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update only calculated values (thread-safe)
     * Called by SessionMonitorService
     * @param session Session with updated calculations
     */
    public void updateCalculatedValues(WorkUsersSessionsStates session) {
        lock.writeLock().lock();
        try {
            if (session == null || !initialized) {
                return;
            }

            // Update only dynamic/calculated fields
            this.totalWorkedMinutes = session.getTotalWorkedMinutes();
            this.finalWorkedMinutes = session.getFinalWorkedMinutes();
            this.totalOvertimeMinutes = session.getTotalOvertimeMinutes();
            this.lunchBreakDeducted = session.getLunchBreakDeducted();
            this.totalTemporaryStopMinutes = session.getTotalTemporaryStopMinutes();
            this.lastTemporaryStopTime = session.getLastTemporaryStopTime();
            this.lastActivity = session.getLastActivity();

            // Update calculation timestamp
            this.lastCalculationUpdate = System.currentTimeMillis();

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get complete session object combining static and dynamic values (thread-safe)
     * @return Complete WorkUsersSessionsStates object
     */
    public WorkUsersSessionsStates toCombinedSession() {
        lock.readLock().lock();
        try {
            if (!initialized) {
                return null;
            }

            WorkUsersSessionsStates session = new WorkUsersSessionsStates();

            // Set static values
            session.setUserId(this.userId);
            session.setUsername(this.username);
            session.setSessionStatus(this.sessionStatus);
            session.setDayStartTime(this.dayStartTime);
            session.setDayEndTime(this.dayEndTime);
            session.setCurrentStartTime(this.currentStartTime);
            session.setTemporaryStopCount(this.temporaryStopCount);
            session.setWorkdayCompleted(this.workdayCompleted);

            // Deep copy temporary stops
            session.setTemporaryStops(this.temporaryStops != null ?
                    new ArrayList<>(this.temporaryStops) : new ArrayList<>());

            // Set dynamic values
            session.setTotalWorkedMinutes(this.totalWorkedMinutes);
            session.setFinalWorkedMinutes(this.finalWorkedMinutes);
            session.setTotalOvertimeMinutes(this.totalOvertimeMinutes);
            session.setLunchBreakDeducted(this.lunchBreakDeducted);
            session.setTotalTemporaryStopMinutes(this.totalTemporaryStopMinutes);
            session.setLastTemporaryStopTime(this.lastTemporaryStopTime);
            session.setLastActivity(this.lastActivity);

            return session;

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if cache is initialized and valid
     * @return true if cache has valid data
     */
    public boolean isValid() {
        lock.readLock().lock();
        try {
            return initialized && username != null && userId != null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear cache (for midnight reset)
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            // Resets all values
            this.userId = null;
            this.username = null;
            this.sessionStatus = null;
            this.dayStartTime = null;
            this.dayEndTime = null;
            this.currentStartTime = null;
            this.temporaryStopCount = null;
            this.temporaryStops = new ArrayList<>();
            this.workdayCompleted = null;

            this.totalWorkedMinutes = null;
            this.finalWorkedMinutes = null;
            this.totalOvertimeMinutes = null;
            this.lunchBreakDeducted = null;
            this.totalTemporaryStopMinutes = null;
            this.lastTemporaryStopTime = null;
            this.lastActivity = null;

            // Reset metadata
            this.lastFileRead = 0;
            this.lastCalculationUpdate = 0;
            this.initialized = false;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get cache age in milliseconds
     * @return Age since last file read
     */
    public long getCacheAge() {
        lock.readLock().lock();
        try {
            return initialized ? System.currentTimeMillis() - lastFileRead : Long.MAX_VALUE;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get calculation age in milliseconds
     * @return Age since last calculation update
     */
    public long getCalculationAge() {
        lock.readLock().lock();
        try {
            return initialized ? System.currentTimeMillis() - lastCalculationUpdate : Long.MAX_VALUE;
        } finally {
            lock.readLock().unlock();
        }
    }
}