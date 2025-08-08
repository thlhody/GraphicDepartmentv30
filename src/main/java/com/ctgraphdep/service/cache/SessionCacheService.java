package com.ctgraphdep.service.cache;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.fileOperations.data.SessionDataService;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Single Source of Truth for Session Data.
 * Architecture:
 * - Primary data access layer for ALL session operations
 * - Write Strategy: Cache first → File second (always writes to file)
 * - Read Strategy: Cache → File/Local → Network → Default (create new session)
 * - Cache refresh on every write to maintain file=cache consistency
 * - Supports single user per application instance
 * - Daily reset at midnight via SessionMidnightHandler
 * Key Features:
 * - Comprehensive fallback strategy for maximum reliability
 * - Write-through pattern ensures data persistence
 * - Thread-safe operations using ReentrantReadWriteLock
 * - Automatic cache refresh after writes
 * - Emergency session creation when all sources fail
 * - Integration with status service for consistency
 */
@Service
public class SessionCacheService {

    private final SessionDataService sessionDataService;
    private final MainDefaultUserContextService mainDefaultUserContextService;

    // Thread-safe cache entry - single user per instance
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private volatile SessionCacheEntry currentSessionEntry;

    @Autowired
    public SessionCacheService(SessionDataService sessionDataService, MainDefaultUserContextService mainDefaultUserContextService) {
        this.sessionDataService = sessionDataService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // PRIMARY READ OPERATIONS WITH COMPREHENSIVE FALLBACK
    // ========================================================================

    // Primary method for reading session data with comprehensive fallback. Read Strategy: Cache → File/Local → Network → Default (create new)
    public WorkUsersSessionsStates readSessionWithFallback(String username, Integer userId) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Reading session with fallback for user: %s", username));

            // Validate this is for current user
            if (isNotCurrentUser(username)) {
                LoggerUtil.warn(this.getClass(), String.format("Session read rejected for non-current user: %s (current: %s)", username, getCurrentUsername()));
                return createDefaultSession(username, userId);
            }

            // Step 1: Try cache first (fastest)
            WorkUsersSessionsStates cachedSession = readFromCacheOnly(username);
            if (cachedSession != null) {
                LoggerUtil.debug(this.getClass(), String.format("Cache hit for user: %s", username));
                return cachedSession;
            }

            // Step 2: Cache miss - try file/local
            LoggerUtil.info(this.getClass(), String.format("Cache miss for user: %s, trying file", username));
            WorkUsersSessionsStates fileSession = readFromFileWithFallback(username, userId);
            if (fileSession != null) {
                // Refresh cache with file data
                refreshCacheFromSession(fileSession);
                LoggerUtil.info(this.getClass(), String.format("File read successful, cache refreshed for user: %s", username));
                return fileSession;
            }

            // Step 3: File failed - try network
            LoggerUtil.warn(this.getClass(), String.format("File read failed for user: %s, trying network", username));
            WorkUsersSessionsStates networkSession = readFromNetworkWithFallback(username, userId);
            if (networkSession != null) {
                // Write network data to local file and refresh cache
                boolean fileSaved = writeSessionToFile(networkSession);
                if (fileSaved) {
                    refreshCacheFromSession(networkSession);
                    LoggerUtil.info(this.getClass(), String.format("Network read successful, saved to file and cache for user: %s", username));
                    return networkSession;
                }
                // Even if file save failed, at least refresh cache with network data
                refreshCacheFromSession(networkSession);
                LoggerUtil.warn(this.getClass(), String.format("Network read successful but file save failed for user: %s", username));
                return networkSession;
            }

            // Step 4: All sources failed - create default session
            LoggerUtil.error(this.getClass(), String.format("ALL data sources failed for user: %s, creating default session", username));
            WorkUsersSessionsStates defaultSession = createDefaultSession(username, userId);

            // Try to save default session to establish file
            boolean defaultSaved = writeSessionToFile(defaultSession);
            refreshCacheFromSession(defaultSession);
            if (defaultSaved) {
                LoggerUtil.info(this.getClass(), String.format("Default session created and saved for user: %s", username));
            } else {
                // Even if file save failed, cache the default session
                LoggerUtil.warn(this.getClass(), String.format("Default session created but file save failed for user: %s", username));
            }

            return defaultSession;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Critical error in readSessionWithFallback for user %s: %s",
                    username, e.getMessage()), e);

            // Ultimate emergency fallback
            WorkUsersSessionsStates emergencySession = createDefaultSession(username, userId);
            try {
                refreshCacheFromSession(emergencySession);
            } catch (Exception cacheError) {
                LoggerUtil.error(this.getClass(), "Even emergency cache refresh failed", cacheError);
            }
            return emergencySession;
        }
    }

    // Check if user has an active session (online or temporary stop)
    public boolean hasActiveSession(String username, Integer userId) {
        try {
            WorkUsersSessionsStates session = readSessionWithFallback(username, userId);
            return session != null &&
                    (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                            WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking active session for %s: %s",
                    username, e.getMessage()), e);
            return false;
        }
    }

    // ========================================================================
    // PRIMARY WRITE OPERATIONS WITH WRITE-THROUGH PATTERN
    // ========================================================================

    // Primary method for writing session data with write-through pattern.  Write Strategy: Cache first → File second (always writes to file)
    // Cache is refreshed after every write to maintain consistency
    public boolean writeSessionWithWriteThrough(WorkUsersSessionsStates session) {
        if (session == null) {
            LoggerUtil.warn(this.getClass(), "Cannot write null session");
            return false;
        }

        String username = session.getUsername();

        try {
            LoggerUtil.info(this.getClass(), String.format("Writing session with write-through for user: %s", username));

            // Validate this is for current user
            if (isNotCurrentUser(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Session write rejected for non-current user: %s", username));
                return false;
            }

            boolean cacheSuccess = false;
            boolean fileSuccess = false;

            // Step 1: Write to cache first
            try {
                refreshCacheFromSession(session);
                cacheSuccess = true;
                LoggerUtil.debug(this.getClass(), String.format("Cache write successful for user: %s", username));
            } catch (Exception cacheError) {
                LoggerUtil.error(this.getClass(), String.format("Cache write failed for user %s: %s",
                        username, cacheError.getMessage()), cacheError);
            }

            // Step 2: Write to file (always attempt, regardless of cache result)
            try {
                fileSuccess = writeSessionToFile(session);
                if (fileSuccess) {
                    LoggerUtil.debug(this.getClass(), String.format("File write successful for user: %s", username));
                } else {
                    LoggerUtil.warn(this.getClass(), String.format("File write failed for user: %s", username));
                }
            } catch (Exception fileError) {
                LoggerUtil.error(this.getClass(), String.format("File write error for user %s: %s",
                        username, fileError.getMessage()), fileError);
            }

            // Step 3: Refresh cache from file to ensure consistency (if file write succeeded)
            if (fileSuccess && !cacheSuccess) {
                try {
                    WorkUsersSessionsStates fileSession = readFromFileWithFallback(username, session.getUserId());
                    if (fileSession != null) {
                        refreshCacheFromSession(fileSession);
                        LoggerUtil.info(this.getClass(), String.format("Cache refreshed from file after write for user: %s", username));
                        cacheSuccess = true;
                    }
                } catch (Exception refreshError) {
                    LoggerUtil.warn(this.getClass(), String.format("Cache refresh from file failed for user %s: %s",
                            username, refreshError.getMessage()));
                }
            }

            // Return success if either cache or file succeeded (preferably both)
            boolean overallSuccess = cacheSuccess || fileSuccess;

            if (overallSuccess) {
                if (cacheSuccess && fileSuccess) {
                    LoggerUtil.info(this.getClass(), String.format("Session write-through fully successful for user: %s", username));
                } else if (cacheSuccess) {
                    LoggerUtil.warn(this.getClass(), String.format("Session write-through partial success (cache only) for user: %s", username));
                } else {
                    LoggerUtil.warn(this.getClass(), String.format("Session write-through partial success (file only) for user: %s", username));
                }
            } else {
                LoggerUtil.error(this.getClass(), String.format("Session write-through completely failed for user: %s", username));
            }

            return overallSuccess;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Critical error in writeSessionWithWriteThrough for user %s: %s",
                    username, e.getMessage()), e);
            return false;
        }
    }

    // Update session calculations with optional cache-only mode
    public boolean updateSessionCalculationsWithWriteThrough(WorkUsersSessionsStates session, boolean cacheOnly) {
        if (session == null) {
            LoggerUtil.warn(this.getClass(), "Cannot update calculations for null session");
            return false;
        }

        String username = session.getUsername();

        try {
            LoggerUtil.debug(this.getClass(), String.format("Updating session calculations for %s (cache-only: %b)", username, cacheOnly));

            if (cacheOnly) {
                // Cache-only mode: just update calculated values
                refreshCacheFromSession(session);
                LoggerUtil.debug(this.getClass(), String.format("Session calculations updated in cache-only mode for user: %s", username));
                return true;
            } else {
                // Normal mode: full write-through
                return writeSessionWithWriteThrough(session);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating session calculations for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    // ========================================================================
    // CACHE MANAGEMENT OPERATIONS
    // ========================================================================

    // Force refresh cache from file
    public boolean forceRefreshFromFile(String username, Integer userId) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Force refreshing cache from file for user: %s", username));

            // Clear current cache
            invalidateUserSession(username);

            // Read from file and refresh cache
            WorkUsersSessionsStates fileSession = readFromFileWithFallback(username, userId);
            if (fileSession != null) {
                refreshCacheFromSession(fileSession);
                LoggerUtil.info(this.getClass(), String.format("Cache force refresh successful for user: %s", username));
                return true;
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Cache force refresh failed - no file data for user: %s", username));
                return false;
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error force refreshing cache for %s: %s",
                    username, e.getMessage()), e);
            return false;
        }
    }

    // Invalidate user session cache
    public void invalidateUserSession(String username) {
        cacheLock.writeLock().lock();
        try {
            if (currentSessionEntry != null && username.equals(currentSessionEntry.getUsername())) {
                currentSessionEntry.clear();
                currentSessionEntry = null;
                LoggerUtil.info(this.getClass(), String.format("Invalidated session cache for user: %s", username));
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    // Clear entire cache (for midnight reset)
    public void clearAllCache() {
        cacheLock.writeLock().lock();
        try {
            if (currentSessionEntry != null) {
                currentSessionEntry.clear();
                currentSessionEntry = null;
            }
            LoggerUtil.info(this.getClass(), "Cleared entire session cache");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    // Check if session cache is healthy
    public boolean isSessionCacheHealthy(String username) {
        cacheLock.readLock().lock();
        try {
            return currentSessionEntry != null &&
                    currentSessionEntry.isValid() &&
                    username.equals(currentSessionEntry.getUsername());
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    //Read from cache only (no fallback)
    private WorkUsersSessionsStates readFromCacheOnly(String username) {
        cacheLock.readLock().lock();
        try {
            if (currentSessionEntry != null &&
                    currentSessionEntry.isValid() &&
                    username.equals(currentSessionEntry.getUsername())) {

                return currentSessionEntry.toCombinedSession();
            }
            return null;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    // Read from file with fallback to network
    private WorkUsersSessionsStates readFromFileWithFallback(String username, Integer userId) {
        try {
            // Try local file first
            WorkUsersSessionsStates session = sessionDataService.readLocalSessionFile(username, userId);
            if (session != null) {
                LoggerUtil.debug(this.getClass(), String.format("Local file read successful for user: %s", username));
                return session;
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Local file read failed for user %s: %s",
                    username, e.getMessage()));
        }

        try {
            // Fallback to local read-only
            WorkUsersSessionsStates session = sessionDataService.readLocalSessionFileReadOnly(username, userId);
            if (session != null) {
                LoggerUtil.debug(this.getClass(), String.format("Local read-only successful for user: %s", username));
                return session;
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Local read-only failed for user %s: %s",
                    username, e.getMessage()));
        }

        LoggerUtil.debug(this.getClass(), String.format("All file read attempts failed for user: %s", username));
        return null;
    }

    //Read from network - let SessionDataService handle all network logic internally
    private WorkUsersSessionsStates readFromNetworkWithFallback(String username, Integer userId) {
        try {
            // SessionDataService handles network availability checking internally
            WorkUsersSessionsStates session = sessionDataService.readNetworkSessionFileReadOnly(username, userId);
            if (session != null) {
                LoggerUtil.debug(this.getClass(), String.format("Network read successful for user: %s", username));
                return session;
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Network read failed for user %s: %s",
                    username, e.getMessage()));
        }

        LoggerUtil.debug(this.getClass(), String.format("Network read returned null for user: %s", username));
        return null;
    }

    // Write session to file
    private boolean writeSessionToFile(WorkUsersSessionsStates session) {
        try {
            sessionDataService.writeLocalSessionFile(session);
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("File write failed for user %s: %s",
                    session.getUsername(), e.getMessage()), e);
            return false;
        }
    }

    // Refresh cache from session data
    private void refreshCacheFromSession(WorkUsersSessionsStates session) {
        cacheLock.writeLock().lock();
        try {
            if (currentSessionEntry == null) {
                currentSessionEntry = new SessionCacheEntry();
            }
            currentSessionEntry.initializeFromFile(session);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    // Create default session when all sources fail
    private WorkUsersSessionsStates createDefaultSession(String username, Integer userId) {
        WorkUsersSessionsStates defaultSession = new WorkUsersSessionsStates();
        defaultSession.setUserId(userId);
        defaultSession.setUsername(username);
        defaultSession.setSessionStatus(WorkCode.WORK_OFFLINE);
        defaultSession.setDayStartTime(null);
        defaultSession.setDayEndTime(null);
        defaultSession.setCurrentStartTime(null);
        defaultSession.setTotalWorkedMinutes(0);
        defaultSession.setFinalWorkedMinutes(0);
        defaultSession.setTotalOvertimeMinutes(0);
        defaultSession.setLunchBreakDeducted(false);
        defaultSession.setWorkdayCompleted(false);
        defaultSession.setTemporaryStopCount(0);
        defaultSession.setTotalTemporaryStopMinutes(0);
        defaultSession.setTemporaryStops(new ArrayList<>());
        defaultSession.setLastTemporaryStopTime(null);
        defaultSession.setLastActivity(LocalDateTime.now());

        LoggerUtil.info(this.getClass(), String.format("Created default session for user: %s", username));
        return defaultSession;
    }

    // Check if username is current user
    private boolean isNotCurrentUser(String username) {
        try {
            String currentUsername = getCurrentUsername();
            return username == null || !username.equals(currentUsername);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error checking current user: %s", e.getMessage()));
            return true;
        }
    }

    // Get current username
    private String getCurrentUsername() {
        return mainDefaultUserContextService.getCurrentUsername();
    }

    // ========================================================================
    // DIAGNOSTIC AND MONITORING METHODS
    // ========================================================================

    // Get cache status for monitoring
    public String getCacheStatus() {
        cacheLock.readLock().lock();
        try {
            StringBuilder status = new StringBuilder();
            status.append("SessionCache Status:\n");

            if (currentSessionEntry != null && currentSessionEntry.isValid()) {
                status.append("Cache Entry: Valid\n");
                status.append("User: ").append(currentSessionEntry.getUsername()).append("\n");
                status.append("Status: ").append(currentSessionEntry.getSessionStatus()).append("\n");
                status.append("Age: ").append(currentSessionEntry.getCacheAge()).append("ms\n");
                status.append("Last Update: ").append(currentSessionEntry.getLastCalculationUpdate()).append("ms ago\n");
            } else {
                status.append("Cache Entry: Invalid or Empty\n");
            }

            return status.toString();
        } finally {
            cacheLock.readLock().unlock();
        }
    }

}