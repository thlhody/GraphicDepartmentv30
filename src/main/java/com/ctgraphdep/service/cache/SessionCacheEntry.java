package com.ctgraphdep.service.cache;

import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ENHANCED SessionCacheEntry - Thread-safe cache entry for session data.
 * Enhancements:
 * - Better separation of static vs dynamic values
 * - Enhanced metadata tracking (source, version, health)
 * - Support for write-through pattern validation
 * - Comprehensive cache age and health monitoring
 * - Thread-safe operations using ReentrantReadWriteLock
 * - Memory optimization and cleanup methods
 */
@Data
public class SessionCacheEntry {

    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // === STATIC VALUES (Set by commands, stable during day) ===
    private Integer userId;
    private String username;
    private String sessionStatus;
    private LocalDateTime dayStartTime;
    private LocalDateTime dayEndTime;
    private LocalDateTime currentStartTime;
    private Integer temporaryStopCount;
    private List<TemporaryStop> temporaryStops;
    private Boolean workdayCompleted;

    // === DYNAMIC VALUES (Calculated frequently by SessionMonitor) ===
    private Integer totalWorkedMinutes;
    private Integer finalWorkedMinutes;
    private Integer totalOvertimeMinutes;
    private Boolean lunchBreakDeducted;
    private Integer totalTemporaryStopMinutes;
    private LocalDateTime lastTemporaryStopTime;
    private LocalDateTime lastActivity;

    // === ENHANCED CACHE METADATA ===
    private long lastFileRead;
    private long lastCalculationUpdate;
    private long lastFileWrite;
    private boolean initialized;
    private String dataSource; // "file", "network", "default", etc.
    private long cacheVersion; // For optimistic concurrency
    private boolean dirty; // Indicates unsaved changes

    /**
     * Default constructor
     */
    public SessionCacheEntry() {
        this.temporaryStops = new ArrayList<>();
        this.initialized = false;
        this.dirty = false;
        this.cacheVersion = 0;
        this.dataSource = "unknown";
    }

    // ENHANCED: Initialize cache from file data with metadata tracking
    public void initializeFromFile(WorkUsersSessionsStates session, String source) {
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

            // Update enhanced metadata
            long currentTime = System.currentTimeMillis();
            this.lastFileRead = currentTime;
            this.lastCalculationUpdate = currentTime;
            this.dataSource = source != null ? source : "unknown";
            this.cacheVersion++;
            this.initialized = true;
            this.dirty = false;

        } finally {
            lock.writeLock().unlock();
        }
    }

    // Legacy compatibility method
    public void initializeFromFile(WorkUsersSessionsStates session) {
        initializeFromFile(session, "file");
    }

    //Get complete session object combining static and dynamic values
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

    // Check if cache is initialized and valid
    public boolean isValid() {
        lock.readLock().lock();
        try {
            return initialized && username != null && userId != null;
        } finally {
            lock.readLock().unlock();
        }
    }

    //Check if cache is healthy (not too old)
    public boolean isHealthy() {
        lock.readLock().lock();
        try {
            if (!isValid()) {
                return false;
            }

            // Check if cache is too old (older than 1 hour is considered unhealthy)
            long cacheAge = getCacheAge();
            long maxAge = 60 * 60 * 1000; // 1 hour in milliseconds

            return cacheAge < maxAge;
        } finally {
            lock.readLock().unlock();
        }
    }

    //Clear cache (for midnight reset)
    public void clear() {
        lock.writeLock().lock();
        try {
            // Reset all values
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
            this.lastFileWrite = 0;
            this.initialized = false;
            this.dirty = false;
            this.cacheVersion = 0;
            this.dataSource = "unknown";

        } finally {
            lock.writeLock().unlock();
        }
    }

    // Get cache age in milliseconds
    public long getCacheAge() {
        lock.readLock().lock();
        try {
            return initialized ? System.currentTimeMillis() - lastFileRead : Long.MAX_VALUE;
        } finally {
            lock.readLock().unlock();
        }
    }

    //Get calculation age in milliseconds
    public long getCalculationAge() {
        lock.readLock().lock();
        try {
            return initialized ? System.currentTimeMillis() - lastCalculationUpdate : Long.MAX_VALUE;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Get diagnostic information about cache entry
    public String getDiagnosticInfo() {
        lock.readLock().lock();
        try {
            if (!initialized) {
                return "SessionCacheEntry{uninitialized}";
            }

            return String.format(
                    "SessionCacheEntry{user=%s, status=%s, source=%s, version=%d, " +
                            "cacheAge=%dms, calcAge=%dms, dirty=%s, healthy=%s}",
                    username, sessionStatus, dataSource, cacheVersion,
                    getCacheAge(), getCalculationAge(), dirty, isHealthy()
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    // Override toString for better logging
    @Override
    public String toString() {
        return getDiagnosticInfo();
    }
}