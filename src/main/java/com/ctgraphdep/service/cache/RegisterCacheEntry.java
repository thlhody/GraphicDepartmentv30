package com.ctgraphdep.service.cache;

import com.ctgraphdep.model.RegisterEntry;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe cache entry for register data.
 * Manages register entries for a specific month with thread-safe operations.
 */
@Data
public class RegisterCacheEntry {

    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // === REGISTER DATA ===
    private String username;
    private Integer userId;
    private int year;
    private int month;
    private List<RegisterEntry> entries;

    // === CACHE METADATA ===
    private long lastUpdated;
    private long lastFileRead;
    private boolean initialized;
    private boolean dirty; // Indicates if cache has unsaved changes

    /**
     * Default constructor
     */
    public RegisterCacheEntry() {
        this.entries = new ArrayList<>();
        this.initialized = false;
        this.dirty = false;
    }

    /**
     * Initialize cache entry from file data (thread-safe)
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @param registerEntries Register entries from file
     */
    public void initializeFromFile(String username, Integer userId, int year, int month, List<RegisterEntry> registerEntries) {
        lock.writeLock().lock();
        try {
            this.username = username;
            this.userId = userId;
            this.year = year;
            this.month = month;

            // Deep copy entries to avoid reference issues
            this.entries = registerEntries != null ? new ArrayList<>(registerEntries) : new ArrayList<>();

            if (!this.entries.isEmpty()) {
                sortEntries();
            }

            // Update metadata
            this.lastFileRead = System.currentTimeMillis();
            this.lastUpdated = System.currentTimeMillis();
            this.initialized = true;
            this.dirty = false;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Add new register entry (thread-safe)
     * @param entry The register entry to add
     * @return true if entry was added successfully
     */
    public boolean addEntry(RegisterEntry entry) {
        lock.writeLock().lock();
        try {
            if (!initialized || entry == null) {
                return false;
            }

            // Assign entry ID if not set
            if (entry.getEntryId() == null) {
                int nextId = entries.stream()
                        .mapToInt(e -> e.getEntryId() != null ? e.getEntryId() : 0)
                        .max()
                        .orElse(0) + 1;
                entry.setEntryId(nextId);
            }

            // Remove existing entry with same ID if present
            entries.removeIf(e -> e.getEntryId().equals(entry.getEntryId()));

            // Add new entry
            entries.add(entry);

            // Sort by date (newest first) then by ID (highest first)
            sortEntries();

            // Mark as dirty and update timestamp
            this.dirty = true;
            this.lastUpdated = System.currentTimeMillis();

            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update existing register entry (thread-safe)
     * @param entry The register entry to update
     * @return true if entry was updated successfully
     */
    public boolean updateEntry(RegisterEntry entry) {
        lock.writeLock().lock();
        try {
            if (!initialized || entry == null || entry.getEntryId() == null) {
                return false;
            }

            // Find and update existing entry
            boolean found = false;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).getEntryId().equals(entry.getEntryId())) {
                    entries.set(i, entry);
                    found = true;
                    break;
                }
            }

            if (found) {
                // Sort entries after update
                sortEntries();

                // Mark as dirty and update timestamp
                this.dirty = true;
                this.lastUpdated = System.currentTimeMillis();
                return true;
            }

            return false;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Delete register entry (thread-safe)
     * @param entryId The ID of entry to delete
     * @return true if entry was deleted successfully
     */
    public boolean deleteEntry(Integer entryId) {
        lock.writeLock().lock();
        try {
            if (!initialized || entryId == null) {
                return false;
            }

            boolean removed = entries.removeIf(entry -> entryId.equals(entry.getEntryId()));

            if (removed) {
                // Mark as dirty and update timestamp
                this.dirty = true;
                this.lastUpdated = System.currentTimeMillis();
            }

            return removed;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get all register entries (thread-safe)
     * @return Copy of all register entries
     */
    public List<RegisterEntry> getAllEntries() {
        lock.readLock().lock();
        try {
            if (!initialized) {
                return new ArrayList<>();
            }

            // Return deep copy to prevent external modifications
            return new ArrayList<>(entries);

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get specific register entry by ID (thread-safe)
     * @param entryId The entry ID
     * @return Register entry or null if not found
     */
    public RegisterEntry getEntry(Integer entryId) {
        lock.readLock().lock();
        try {
            if (!initialized || entryId == null) {
                return null;
            }

            return entries.stream()
                    .filter(entry -> entryId.equals(entry.getEntryId()))
                    .findFirst()
                    .orElse(null);

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get entries count (thread-safe)
     * @return Number of entries in cache
     */
    public int getEntryCount() {
        lock.readLock().lock();
        try {
            return initialized ? entries.size() : 0;
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
            return initialized && username != null && userId != null;
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
            this.month = 0;
            this.entries.clear();

            this.lastUpdated = 0;
            this.lastFileRead = 0;
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
     * Sort entries by date (newest first) then by ID (highest first)
     * Must be called within write lock
     */
    private void sortEntries() {
        entries.sort((e1, e2) -> {
            // First sort by date (newest first)
            int dateCompare = e2.getDate().compareTo(e1.getDate());
            if (dateCompare != 0) {
                return dateCompare;
            }

            // Then by ID (highest first)
            if (e1.getEntryId() == null && e2.getEntryId() == null) return 0;
            if (e1.getEntryId() == null) return 1;
            if (e2.getEntryId() == null) return -1;

            return e2.getEntryId().compareTo(e1.getEntryId());
        });
    }

    /**
     * Get month key for this cache entry
     * @return String key in format "year-month"
     */
    public String getMonthKey() {
        lock.readLock().lock();
        try {
            return String.format("%d-%02d", year, month);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return String.format("RegisterCacheEntry{user=%s, month=%d/%d, entries=%d, dirty=%s, age=%dms}",
                    username, month, year, entries.size(), dirty, getCacheAge());
        } finally {
            lock.readLock().unlock();
        }
    }
}