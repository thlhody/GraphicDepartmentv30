package com.ctgraphdep.service.cache;

import com.ctgraphdep.model.WorkTimeTable;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * WorktimeCacheEntry - Thread-safe cache entry for monthly worktime data.
 * Key Features:
 * - Manages List<WorkTimeTable> for a specific user/month
 * - Thread-safe operations using ReentrantReadWriteLock
 * - Cache metadata (timestamps, expiration, dirty state)
 * - Follows same pattern as TimeOffCacheEntry
 * - Supports write-through buffering
 */
@Data
public class WorktimeCacheEntry {

    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // === WORKTIME DATA ===
    private String username;
    private Integer userId;
    private int year;
    private int month;
    private List<WorkTimeTable> entries;

    // === CACHE METADATA ===
    private long lastUpdated;
    private long lastServiceLoad;  // When data was loaded from service
    private boolean initialized;
    private boolean dirty; // Indicates if cache has unsaved changes

    /**
     * Default constructor
     */
    public WorktimeCacheEntry() {
        this.initialized = false;
        this.dirty = false;
        this.entries = new ArrayList<>();
    }

    /**
     * Initialize cache entry from service data (thread-safe).
     * This is the method that matches the refactored pattern where
     * cache gets data from service rather than directly from files.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @param entries Worktime entries from service
     */
    public void initializeFromService(String username, Integer userId, int year, int month, List<WorkTimeTable> entries) {
        lock.writeLock().lock();
        try {
            this.username = username;
            this.userId = userId;
            this.year = year;
            this.month = month;
            this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();

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
     * @param month Month
     * @param entries Worktime entries from file
     * @deprecated Use initializeFromService() instead
     */
    @Deprecated
    public void initializeFromFile(String username, Integer userId, int year, int month, List<WorkTimeTable> entries) {
        initializeFromService(username, userId, year, month, entries);
    }

    /**
     * Update entries data (thread-safe)
     *
     * @param entries The updated entries list
     */
    public void updateEntries(List<WorkTimeTable> entries) {
        lock.writeLock().lock();
        try {
            if (!initialized || entries == null) {
                return;
            }

            this.entries = new ArrayList<>(entries);

            // Mark as dirty and update timestamp
            this.dirty = true;
            this.lastUpdated = System.currentTimeMillis();

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get worktime entries (thread-safe)
     * @return Copy of worktime entries list
     */
    public List<WorkTimeTable> getEntries() {
        lock.readLock().lock();
        try {
            if (!initialized) {
                return new ArrayList<>();
            }

            return new ArrayList<>(entries);

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
            return initialized && username != null && userId != null && entries != null;
        } finally {
            lock.readLock().unlock();
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
            this.month = 0;
            this.entries = new ArrayList<>();

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
     * Get entry count for monitoring
     * @return Number of entries in cache
     */
    public int getEntryCount() {
        lock.readLock().lock();
        try {
            return entries != null ? entries.size() : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return String.format("WorktimeCacheEntry{user=%s, period=%d/%d, entries=%d, dirty=%s, age=%dms}",
                    username, year, month, getEntryCount(), dirty, getCacheAge());
        } finally {
            lock.readLock().unlock();
        }
    }
}