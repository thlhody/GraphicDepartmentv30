package com.ctgraphdep.session.cache;

import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe session cache service.
 * Manages in-memory session data to reduce file I/O operations.
 */
@Service
public class SessionCacheService {

    private static final long CACHE_VALIDATION_THRESHOLD_MS = 5000; // 5 seconds

    private final DataAccessService dataAccessService;

    // Thread-safe cache - username as key
    private final ConcurrentHashMap<String, SessionCacheEntry> sessionCache = new ConcurrentHashMap<>();

    // Global cache lock for operations that affect multiple entries
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    @Autowired
    public SessionCacheService(DataAccessService dataAccessService) {
        this.dataAccessService = dataAccessService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void initializeCache() {
        try {
            LoggerUtil.info(this.getClass(), "Initializing session cache...");

            // Try to find current active user and initialize cache
            String activeUser = getCurrentActiveUser();
            if (activeUser != null) {
                LoggerUtil.info(this.getClass(), "Found active user: " + activeUser + ", initializing cache");
                initializeCacheForUser(activeUser);
            } else {
                LoggerUtil.info(this.getClass(), "No active user found, cache will be initialized on first access");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during cache initialization: " + e.getMessage(), e);
        }
    }

    /**
     * Read session from cache (primary method for all reads)
     * Falls back to file if cache miss
     * @param username The username
     * @param userId The user ID
     * @return Session data from cache or file
     */
    public WorkUsersSessionsStates readSession(String username, Integer userId) {
        try {
            // Try cache first
            SessionCacheEntry cacheEntry = sessionCache.get(username);

            if (cacheEntry != null && cacheEntry.isValid()) {
                LoggerUtil.debug(this.getClass(), "Cache hit for user: " + username);
                return cacheEntry.toCombinedSession();
            }

            // Cache miss - read from file and populate cache
            LoggerUtil.debug(this.getClass(), "Cache miss for user: " + username + ", reading from file");
            WorkUsersSessionsStates sessionFromFile = dataAccessService.readLocalSessionFile(username, userId);

            if (sessionFromFile != null) {
                refreshCacheFromFile(username, sessionFromFile);
                LoggerUtil.debug(this.getClass(), "Populated cache for user: " + username);
                return sessionFromFile;
            }

            LoggerUtil.debug(this.getClass(), "No session found for user: " + username);
            return null;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading session for user " + username + ": " + e.getMessage(), e);
            // Fallback to direct file read on error
            try {
                return dataAccessService.readLocalSessionFile(username, userId);
            } catch (Exception fe) {
                LoggerUtil.error(this.getClass(), "Fallback file read also failed: " + fe.getMessage(), fe);
                return null;
            }
        }
    }

    /**
     * Refresh cache from file data (called after commands write to file)
     * @param username The username
     * @param sessionData Updated session data from file
     */
    public void refreshCacheFromFile(String username, WorkUsersSessionsStates sessionData) {
        try {
            if (sessionData == null) {
                LoggerUtil.warn(this.getClass(), "Cannot refresh cache with null session data for user: " + username);
                return;
            }

            SessionCacheEntry cacheEntry = sessionCache.computeIfAbsent(username, k -> new SessionCacheEntry());
            cacheEntry.initializeFromFile(sessionData);

            LoggerUtil.debug(this.getClass(), "Refreshed cache from file for user: " + username);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error refreshing cache for user " + username + ": " + e.getMessage(), e);
        }
    }

    /**
     * Update only calculated values in cache (called by SessionMonitorService)
     * Does NOT write to file
     * @param username The username
     * @param calculatedSession Session with updated calculations
     */
    public void updateCalculatedValues(String username, WorkUsersSessionsStates calculatedSession) {
        try {
            if (calculatedSession == null) {
                LoggerUtil.warn(this.getClass(), "Cannot update cache with null calculated session for user: " + username);
                return;
            }

            SessionCacheEntry cacheEntry = sessionCache.get(username);
            if (cacheEntry != null && cacheEntry.isValid()) {
                cacheEntry.updateCalculatedValues(calculatedSession);
                LoggerUtil.debug(this.getClass(), "Updated calculated values in cache for user: " + username);
            } else {
                LoggerUtil.warn(this.getClass(), "No valid cache entry found for user: " + username + " when updating calculated values");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating calculated values for user " + username + ": " + e.getMessage(), e);
        }
    }

    /**
     * Check if cache should be validated against file (startup only)
     * @param username The username
     * @param userId The user ID
     * @return true if cache needs validation/refresh
     */
    public boolean shouldValidateCache(String username, Integer userId) {
        SessionCacheEntry cacheEntry = sessionCache.get(username);

        if (cacheEntry == null || !cacheEntry.isValid()) {
            return true; // No cache or invalid cache
        }

        // Only validate on startup or if cache is very old
        if (cacheEntry.getCacheAge() < CACHE_VALIDATION_THRESHOLD_MS) {
            return false; // Cache is fresh
        }

        try {
            // Check if file is newer than cache
            Path sessionFile = dataAccessService.getLocalSessionPath(username, userId);
            if (Files.exists(sessionFile)) {
                long fileModTime = Files.getLastModifiedTime(sessionFile).toMillis();
                return fileModTime > cacheEntry.getLastFileRead();
            }
        } catch (IOException e) {
            LoggerUtil.warn(this.getClass(), "Error checking file modification time for user " + username + ": " + e.getMessage());
        }

        return false;
    }

    /**
     * Clear cache for specific user (midnight reset)
     * @param username The username
     */
    public void clearUserCache(String username) {
        globalLock.writeLock().lock();
        try {
            SessionCacheEntry cacheEntry = sessionCache.get(username);
            if (cacheEntry != null) {
                cacheEntry.clear();
                LoggerUtil.info(this.getClass(), "Cleared cache for user: " + username);
            }
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Clear entire cache (full reset)
     */
    public void clearAllCache() {
        globalLock.writeLock().lock();
        try {
            sessionCache.clear();
            LoggerUtil.info(this.getClass(), "Cleared entire session cache");
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Get cache statistics for monitoring
     * @return Cache status information
     */
    public String getCacheStatus() {
        globalLock.readLock().lock();
        try {
            StringBuilder status = new StringBuilder();
            status.append("Session Cache Status:\n");
            status.append("Total cached users: ").append(sessionCache.size()).append("\n");

            sessionCache.forEach((username, entry) -> {
                status.append("User: ").append(username)
                        .append(", Valid: ").append(entry.isValid())
                        .append(", Age: ").append(entry.getCacheAge()).append("ms")
                        .append(", Calc Age: ").append(entry.getCalculationAge()).append("ms\n");
            });

            return status.toString();
        } finally {
            globalLock.readLock().unlock();
        }
    }

    /**
     * Initialize cache for specific user
     * @param username The username to initialize cache for
     */
    private void initializeCacheForUser(String username) {
        try {
            // Extract userId from session filename or use a default approach
            Integer userId = extractUserIdFromSession(username);
            if (userId != null) {
                WorkUsersSessionsStates session = dataAccessService.readLocalSessionFile(username, userId);
                if (session != null) {
                    refreshCacheFromFile(username, session);
                    LoggerUtil.info(this.getClass(), "Initialized cache for user: " + username);
                } else {
                    LoggerUtil.info(this.getClass(), "No session file found for user: " + username);
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing cache for user " + username + ": " + e.getMessage(), e);
        }
    }

    /**
     * Get currently active user by scanning session files
     * @return Username of active user or null
     */
    private String getCurrentActiveUser() {
        try {
            Path sessionDir = dataAccessService.getLocalSessionPath("", 0).getParent();
            if (!Files.exists(sessionDir)) {
                return null;
            }

            return Files.list(sessionDir)
                    .filter(path -> path.getFileName().toString().startsWith("session_") &&
                            path.getFileName().toString().endsWith(".json"))
                    .max((p1, p2) -> {
                        try {
                            return Files.getLastModifiedTime(p1).compareTo(Files.getLastModifiedTime(p2));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .map(this::extractUsernameFromPath)
                    .orElse(null);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error finding active user: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract username from session file path
     * @param sessionPath Path to session file
     * @return Extracted username or null
     */
    private String extractUsernameFromPath(Path sessionPath) {
        try {
            String filename = sessionPath.getFileName().toString();
            // Format: session_username_userId.json
            String[] parts = filename.replace("session_", "").replace(".json", "").split("_");
            return parts.length >= 1 ? parts[0] : null;
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error extracting username from path: " + sessionPath);
            return null;
        }
    }

    /**
     * Extract userId from session filename
     * @param username The username
     * @return UserId or null if not found
     */
    private Integer extractUserIdFromSession(String username) {
        try {
            Path sessionDir = dataAccessService.getLocalSessionPath("", 0).getParent();
            if (!Files.exists(sessionDir)) {
                return null;
            }

            return Files.list(sessionDir)
                    .filter(path -> path.getFileName().toString().startsWith("session_" + username + "_"))
                    .findFirst()
                    .map(path -> {
                        try {
                            String filename = path.getFileName().toString();
                            String[] parts = filename.replace("session_", "").replace(".json", "").split("_");
                            return parts.length >= 2 ? Integer.parseInt(parts[1]) : null;
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .orElse(null);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error extracting userId for username " + username + ": " + e.getMessage(), e);
            return null;
        }
    }
}