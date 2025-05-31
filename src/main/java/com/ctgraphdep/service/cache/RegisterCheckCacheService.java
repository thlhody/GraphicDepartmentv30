package com.ctgraphdep.service.cache;

import com.ctgraphdep.fileOperations.data.CheckRegisterDataService;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe check register cache service with write-through pattern.
 * Manages in-memory check register data per month to eliminate file concurrency issues.
 * Features:
 * - Per-user, per-month caching
 * - Write-through: cache updates immediately trigger file writes
 * - Month-based memory management
 * - Thread-safe operations
 */
@Service
public class RegisterCheckCacheService {

    private final CheckRegisterDataService checkRegisterDataService;

    // Thread-safe cache - monthKey as key (format: "username-year-month")
    private final ConcurrentHashMap<String, RegisterCheckCacheEntry> checkRegisterCache = new ConcurrentHashMap<>();

    // Global cache lock for operations that affect multiple entries
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    // Current user info (since it's 1 app/1 user)
    private String currentUsername;
    private Integer currentUserId;

    @Autowired
    public RegisterCheckCacheService(CheckRegisterDataService checkRegisterDataService) {
        this.checkRegisterDataService = checkRegisterDataService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void initializeCache() {
        LoggerUtil.info(this.getClass(), "Initializing check register cache service...");
        // Cache will be populated on first access to any month
    }

    /**
     * Set current user info (called at login/startup)
     * @param username Current username
     * @param userId Current user ID
     */
    public void setCurrentUser(String username, Integer userId) {
        this.currentUsername = username;
        this.currentUserId = userId;
        LoggerUtil.info(this.getClass(), String.format("Set current user: %s (ID: %d)", username, userId));
    }

    /**
     * Get check register entries for a specific month (loads from file if not cached)
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return List of check register entries for the month
     */
    public List<RegisterCheckEntry> getMonthEntries(String username, Integer userId, int year, int month) {
        try {
            String monthKey = createMonthKey(username, year, month);

            // Try cache first
            RegisterCheckCacheEntry cacheEntry = checkRegisterCache.get(monthKey);

            if (cacheEntry != null && cacheEntry.isValid()) {
                LoggerUtil.debug(this.getClass(), String.format("Cache hit for %s - %d/%d", username, month, year));
                return cacheEntry.getAllEntries();
            }

            // Cache miss - load from file
            LoggerUtil.info(this.getClass(), String.format("Loading check register entries from file for %s - %d/%d", username, month, year));
            return loadMonthFromFile(username, userId, year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting month entries for %s - %d/%d: %s", username, month, year, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    /**
     * Add new check register entry with write-through
     * @param username Username
     * @param userId User ID
     * @param entry Check register entry to add
     * @return true if entry was added successfully
     */
    public boolean addEntry(String username, Integer userId, RegisterCheckEntry entry) {
        try {
            if (entry == null || entry.getDate() == null) {
                LoggerUtil.warn(this.getClass(), "Cannot add null entry or entry without date");
                return false;
            }

            int year = entry.getDate().getYear();
            int month = entry.getDate().getMonthValue();
            String monthKey = createMonthKey(username, year, month);

            // Ensure month is loaded in cache
            ensureMonthLoaded(username, userId, year, month);

            RegisterCheckCacheEntry cacheEntry = checkRegisterCache.get(monthKey);
            if (cacheEntry == null || !cacheEntry.isValid()) {
                LoggerUtil.error(this.getClass(), String.format("Failed to load cache for %s - %d/%d", username, month, year));
                return false;
            }

            // Add to cache
            boolean added = cacheEntry.addEntry(entry);
            if (!added) {
                LoggerUtil.warn(this.getClass(), String.format("Failed to add entry to cache for %s", username));
                return false;
            }

            // Write-through: immediately write to file
            boolean written = writeMonthToFile(cacheEntry);
            if (!written) {
                LoggerUtil.error(this.getClass(), String.format("Failed to write entry to file for %s - %d/%d", username, month, year));
                // Remove from cache since file write failed
                cacheEntry.deleteEntry(entry.getEntryId());
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully added entry %d for %s - %d/%d", entry.getEntryId(), username, month, year));
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error adding entry for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Update existing check register entry with write-through
     * @param username Username
     * @param userId User ID
     * @param entry Check register entry to update
     * @return true if entry was updated successfully
     */
    public boolean updateEntry(String username, Integer userId, RegisterCheckEntry entry) {
        try {
            if (entry == null || entry.getDate() == null || entry.getEntryId() == null) {
                LoggerUtil.warn(this.getClass(), "Cannot update null entry or entry without date/ID");
                return false;
            }

            int year = entry.getDate().getYear();
            int month = entry.getDate().getMonthValue();
            String monthKey = createMonthKey(username, year, month);

            // Ensure month is loaded in cache
            ensureMonthLoaded(username, userId, year, month);

            RegisterCheckCacheEntry cacheEntry = checkRegisterCache.get(monthKey);
            if (cacheEntry == null || !cacheEntry.isValid()) {
                LoggerUtil.error(this.getClass(), String.format("Failed to load cache for %s - %d/%d", username, month, year));
                return false;
            }

            // Update in cache
            boolean updated = cacheEntry.updateEntry(entry);
            if (!updated) {
                LoggerUtil.warn(this.getClass(), String.format("Entry %d not found in cache for %s", entry.getEntryId(), username));
                return false;
            }

            // Write-through: immediately write to file
            boolean written = writeMonthToFile(cacheEntry);
            if (!written) {
                LoggerUtil.error(this.getClass(), String.format("Failed to write updated entry to file for %s - %d/%d", username, month, year));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully updated entry %d for %s - %d/%d", entry.getEntryId(), username, month, year));
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating entry for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Delete check register entry with write-through
     * @param username Username
     * @param userId User ID
     * @param entryId Entry ID to delete
     * @param year Year
     * @param month Month
     * @return true if entry was deleted successfully
     */
    public boolean deleteEntry(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            if (entryId == null) {
                LoggerUtil.warn(this.getClass(), "Cannot delete entry with null ID");
                return false;
            }

            String monthKey = createMonthKey(username, year, month);

            // Ensure month is loaded in cache
            ensureMonthLoaded(username, userId, year, month);

            RegisterCheckCacheEntry cacheEntry = checkRegisterCache.get(monthKey);
            if (cacheEntry == null || !cacheEntry.isValid()) {
                LoggerUtil.error(this.getClass(), String.format("Failed to load cache for %s - %d/%d", username, month, year));
                return false;
            }

            // Delete from cache
            boolean deleted = cacheEntry.deleteEntry(entryId);
            if (!deleted) {
                LoggerUtil.warn(this.getClass(), String.format("Entry %d not found in cache for %s", entryId, username));
                return false;
            }

            // Write-through: immediately write to file
            boolean written = writeMonthToFile(cacheEntry);
            if (!written) {
                LoggerUtil.error(this.getClass(), String.format("Failed to write after deletion to file for %s - %d/%d", username, month, year));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully deleted entry %d for %s - %d/%d", entryId, username, month, year));
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error deleting entry for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Get specific check register entry
     * @param username Username
     * @param userId User ID
     * @param entryId Entry ID
     * @param year Year
     * @param month Month
     * @return Check register entry or null if not found
     */
    public RegisterCheckEntry getEntry(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            String monthKey = createMonthKey(username, year, month);

            // Ensure month is loaded in cache
            ensureMonthLoaded(username, userId, year, month);

            RegisterCheckCacheEntry cacheEntry = checkRegisterCache.get(monthKey);
            if (cacheEntry != null && cacheEntry.isValid()) {
                return cacheEntry.getEntry(entryId);
            }

            return null;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting entry %d for %s: %s", entryId, username, e.getMessage()), e);
            return null;
        }
    }

    /**
     * Clear specific month from cache
     * @param username Username
     * @param year Year
     * @param month Month
     */
    public void clearMonth(String username, int year, int month) {
        globalLock.writeLock().lock();
        try {
            String monthKey = createMonthKey(username, year, month);
            RegisterCheckCacheEntry removed = checkRegisterCache.remove(monthKey);
            if (removed != null) {
                removed.clear();
                LoggerUtil.info(this.getClass(), String.format("Cleared cache for %s - %d/%d", username, month, year));
            }
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Clear entire cache
     */
    public void clearAllCache() {
        globalLock.writeLock().lock();
        try {
            for (RegisterCheckCacheEntry entry : checkRegisterCache.values()) {
                entry.clear();
            }
            checkRegisterCache.clear();
            LoggerUtil.info(this.getClass(), "Cleared entire check register cache");
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Get cache status for monitoring
     * @return Cache status information
     */
    public String getCacheStatus() {
        globalLock.readLock().lock();
        try {
            StringBuilder status = new StringBuilder();
            status.append("Check Register Cache Status:\n");
            status.append("Current user: ").append(currentUsername).append(" (ID: ").append(currentUserId).append(")\n");
            status.append("Total cached months: ").append(checkRegisterCache.size()).append("\n");

            checkRegisterCache.forEach((monthKey, entry) -> status.append("Month: ").append(monthKey)
                    .append(", Entries: ").append(entry.getEntryCount())
                    .append(", Valid: ").append(entry.isValid())
                    .append(", Dirty: ").append(entry.isDirty())
                    .append(", Age: ").append(entry.getCacheAge()).append("ms\n"));

            return status.toString();
        } finally {
            globalLock.readLock().unlock();
        }
    }

    // === PRIVATE HELPER METHODS ===

    /**
     * Load month data from file into cache
     */
    private List<RegisterCheckEntry> loadMonthFromFile(String username, Integer userId, int year, int month) {
        try {
            // Load from file (with smart fallback logic)
            List<RegisterCheckEntry> entriesFromFile = checkRegisterDataService.readUserCheckRegisterLocalReadOnly(username, userId, year, month);
            if (entriesFromFile == null) {
                entriesFromFile = new ArrayList<>();
            }

            // Create and populate cache entry
            String monthKey = createMonthKey(username, year, month);
            RegisterCheckCacheEntry cacheEntry = new RegisterCheckCacheEntry();
            cacheEntry.initializeFromFile(username, userId, year, month, entriesFromFile);

            // Store in cache
            checkRegisterCache.put(monthKey, cacheEntry);

            return cacheEntry.getAllEntries();  // This will be sorted!

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading month from file for %s - %d/%d: %s",
                    username, month, year, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    /**
     * Write month data from cache to file using CheckRegisterDataService
     */
    private boolean writeMonthToFile(RegisterCheckCacheEntry cacheEntry) {
        try {
            if (cacheEntry == null || !cacheEntry.isValid()) {
                LoggerUtil.warn(this.getClass(), "Cannot write invalid cache entry to file");
                return false;
            }

            List<RegisterCheckEntry> entries = cacheEntry.getAllEntries();

            LoggerUtil.debug(this.getClass(), String.format("Writing %d entries from cache to file for %s - %d/%d",
                    entries.size(), cacheEntry.getUsername(), cacheEntry.getMonth(), cacheEntry.getYear()));

            // Use CheckRegisterDataService to write - this handles all the file operations, backup, and sync
            checkRegisterDataService.writeUserCheckRegisterWithSyncAndBackup(cacheEntry.getUsername(), cacheEntry.getUserId(), entries, cacheEntry.getYear(), cacheEntry.getMonth());

            // Mark cache as clean after successful write
            cacheEntry.markClean();

            LoggerUtil.debug(this.getClass(), String.format("Successfully wrote %d entries to file from cache for %s - %d/%d",
                    entries.size(), cacheEntry.getUsername(), cacheEntry.getMonth(), cacheEntry.getYear()));

            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error writing cache to file for %s - %d/%d: %s", cacheEntry.getUsername(), cacheEntry.getMonth(), cacheEntry.getYear(), e.getMessage()), e);
            return false;
        }
    }

    /**
     * Ensure specific month is loaded in cache
     */
    private void ensureMonthLoaded(String username, Integer userId, int year, int month) {
        String monthKey = createMonthKey(username, year, month);

        if (!checkRegisterCache.containsKey(monthKey)) {
            loadMonthFromFile(username, userId, year, month);
        }
    }

    /**
     * Create month key for cache indexing
     */
    private String createMonthKey(String username, int year, int month) {
        return String.format("%s-%d-%02d", username, year, month);
    }
}