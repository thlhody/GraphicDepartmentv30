package com.ctgraphdep.session.cache;

import com.ctgraphdep.fileOperations.data.SessionDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.security.UserContextService;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe session cache service.
 * Manages in-memory session data to reduce file I/O operations.
 */
@Service
public class SessionCacheService {

    private final SessionDataService sessionDataService;
    private final UserContextService userContextService;

    // Thread-safe cache - username as key
    private final ConcurrentHashMap<String, SessionCacheEntry> sessionCache = new ConcurrentHashMap<>();

    // Global cache lock for operations that affect multiple entries
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    @Autowired
    public SessionCacheService(SessionDataService sessionDataService, UserContextService userContextService) {
        this.sessionDataService = sessionDataService;
        this.userContextService = userContextService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void initializeCache() {
        try {
            LoggerUtil.info(this.getClass(), "Initializing session cache...");

            // Try to find current active user and initialize cache
            String activeUser = userContextService.getCurrentUsername();
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
            WorkUsersSessionsStates sessionFromFile = sessionDataService.readLocalSessionFile(username, userId);

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
                return sessionDataService.readLocalSessionFile(username, userId);
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

            sessionCache.forEach((username, entry) -> status.append("User: ").append(username)
                    .append(", Valid: ").append(entry.isValid())
                    .append(", Age: ").append(entry.getCacheAge()).append("ms")
                    .append(", Calc Age: ").append(entry.getCalculationAge()).append("ms\n"));

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
            // Get userId from user context instead of scanning files
            User currentUser = userContextService.getCurrentUser();
            if (currentUser != null && currentUser.getUsername().equals(username)) {
                Integer userId = currentUser.getUserId();

                WorkUsersSessionsStates session = sessionDataService.readLocalSessionFile(username, userId);
                if (session != null) {
                    refreshCacheFromFile(username, session);
                    LoggerUtil.info(this.getClass(), "Initialized cache for user: " + username);
                } else {
                    LoggerUtil.info(this.getClass(), "No session file found for user: " + username);
                }
            } else {
                LoggerUtil.warn(this.getClass(), "Cannot initialize cache: user context mismatch for " + username);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing cache for user " + username + ": " + e.getMessage(), e);
        }
    }
}