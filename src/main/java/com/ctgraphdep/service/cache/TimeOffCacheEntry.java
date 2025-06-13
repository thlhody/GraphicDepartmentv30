package com.ctgraphdep.service.cache;

import com.ctgraphdep.model.TimeOffTracker;
import lombok.Data;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * UPDATED TimeOffCacheEntry - Thread-safe cache entry for time off tracker data.
 * Key Changes:
 * - Added initializeFromService() method to match new pattern
 * - Follows the same pattern as RegisterCacheEntry
 * - Thread-safe operations using ReentrantReadWriteLock
 * - Manages yearly time off tracker with proper cache metadata
 */
@Data
public class TimeOffCacheEntry {

    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // === TIME OFF TRACKER DATA ===
    private String username;
    private Integer userId;
    private int year;
    private TimeOffTracker tracker;

    // === CACHE METADATA ===
    private long lastUpdated;
    private long lastServiceLoad;  // When data was loaded from service
    private boolean initialized;
    private boolean dirty; // Indicates if cache has unsaved changes

    /**
     * Default constructor
     */
    public TimeOffCacheEntry() {
        this.initialized = false;
        this.dirty = false;
    }

    /**
     * Initialize cache entry from service data (thread-safe).
     * This is the new method that matches the refactored pattern where
     * cache gets data from service rather than directly from files.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param tracker Time off tracker from service
     */
    public void initializeFromService(String username, Integer userId, int year, TimeOffTracker tracker) {
        lock.writeLock().lock();
        try {
            this.username = username;
            this.userId = userId;
            this.year = year;
            this.tracker = tracker;

            // Update metadata
            this.lastServiceLoad = System.currentTimeMillis();
            this.lastUpdated = System.currentTimeMillis();
            this.initialized = true;
            this.dirty = false;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Initialize cache entry from file data (thread-safe).
     * Keep this method for backward compatibility, but mark as deprecated.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param tracker Time off tracker from file
     * @deprecated Use initializeFromService() instead
     */
    @Deprecated
    public void initializeFromFile(String username, Integer userId, int year, TimeOffTracker tracker) {
        initializeFromService(username, userId, year, tracker);
    }

    /**
     * Update tracker data (thread-safe)
     *
     * @param tracker The updated tracker
     */
    public void updateTracker(TimeOffTracker tracker) {
        lock.writeLock().lock();
        try {
            if (!initialized || tracker == null) {
                return;
            }

            this.tracker = tracker;

            // Mark as dirty and update timestamp
            this.dirty = true;
            this.lastUpdated = System.currentTimeMillis();

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get time off tracker (thread-safe)
     * @return Copy of time off tracker
     */
    public TimeOffTracker getTracker() {
        lock.readLock().lock();
        try {
            if (!initialized) {
                return null;
            }

            return tracker;

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if cache entry is initialized and valid
     * @return true if cache has valid data
     */
    public boolean isValid() {
        lock.readLock().lock();
        try {
            return initialized && username != null && userId != null && tracker != null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if cache has unsaved changes
     * @return true if cache needs to be written to file
     */
    public boolean isDirty() {
        lock.readLock().lock();
        try {
            return dirty;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Mark cache as clean (after successful file write)
     */
    public void markClean() {
        lock.writeLock().lock();
        try {
            this.dirty = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clear cache entry (for cleanup)
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            this.username = null;
            this.userId = null;
            this.year = 0;
            this.tracker = null;

            this.lastUpdated = 0;
            this.lastServiceLoad = 0;
            this.initialized = false;
            this.dirty = false;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get cache age in milliseconds
     * @return Age since last update
     */
    public long getCacheAge() {
        lock.readLock().lock();
        try {
            return initialized ? System.currentTimeMillis() - lastUpdated : Long.MAX_VALUE;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if cache is expired (1 hour timeout)
     * @return true if cache is older than 1 hour
     */
    public boolean isExpired() {
        lock.readLock().lock();
        try {
            return getCacheAge() > 3600000L; // 1 hour in milliseconds
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get year key for this cache entry
     * @return String key in format "year"
     */
    public String getYearKey() {
        lock.readLock().lock();
        try {
            return String.valueOf(year);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return String.format("TimeOffCacheEntry{user=%s, year=%d, requests=%d, dirty=%s, age=%dms}",
                    username, year,
                    tracker != null && tracker.getRequests() != null ? tracker.getRequests().size() : 0,
                    dirty, getCacheAge());
        } finally {
            lock.readLock().unlock();
        }
    }
}