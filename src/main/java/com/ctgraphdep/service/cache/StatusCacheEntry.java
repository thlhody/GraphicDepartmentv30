package com.ctgraphdep.service.cache;

import com.ctgraphdep.model.UserStatusInfo;
import com.ctgraphdep.model.dto.UserStatusDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe cache entry for user status data.
 * Manages status information with thread-safe operations.
 */
@Data
public class StatusCacheEntry {

    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // === USER STATUS DATA ===
    private String username;
    private Integer userId;
    private String name;
    private String status;
    private LocalDateTime lastActive;
    private String role;

    // === CACHE METADATA ===
    private long lastUpdated;
    private boolean initialized;

    /**
     * Default constructor
     */
    public StatusCacheEntry() {
        this.initialized = false;
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Initialize cache entry from UserStatusInfo (thread-safe)
     * @param statusInfo Status data from local_status.json
     */
    public void initializeFromStatusInfo(UserStatusInfo statusInfo) {
        lock.writeLock().lock();
        try {
            if (statusInfo == null) {
                this.initialized = false;
                return;
            }

            // Update all status fields
            this.username = statusInfo.getUsername();
            this.userId = statusInfo.getUserId();
            this.name = statusInfo.getName();
            this.status = statusInfo.getStatus();
            this.lastActive = statusInfo.getLastActive();
            this.role = statusInfo.getRole();

            // Update metadata
            this.lastUpdated = System.currentTimeMillis();
            this.initialized = true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update only status and timestamp (thread-safe)
     * Called by commands and network flag sync
     * @param newStatus New status value
     * @param timestamp New last active timestamp
     */
    public void updateStatus(String newStatus, LocalDateTime timestamp) {
        lock.writeLock().lock();
        try {
            if (!initialized) {
                return;
            }

            // Update only status-related fields
            this.status = newStatus;
            this.lastActive = timestamp;

            // Update metadata
            this.lastUpdated = System.currentTimeMillis();

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update user information (name, role) from UserService data (thread-safe)
     * @param name User's display name
     * @param role User's role
     */
    public void updateUserInfo(String name, String role) {
        lock.writeLock().lock();
        try {
            if (!initialized) {
                return;
            }

            this.name = name;
            this.role = role;
            this.lastUpdated = System.currentTimeMillis();

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get complete UserStatusInfo object (thread-safe)
     * @return Complete UserStatusInfo object
     */
    public UserStatusInfo toUserStatusInfo() {
        lock.readLock().lock();
        try {
            if (!initialized) {
                return null;
            }

            UserStatusInfo statusInfo = new UserStatusInfo();
            statusInfo.setUsername(this.username);
            statusInfo.setUserId(this.userId);
            statusInfo.setName(this.name);
            statusInfo.setStatus(this.status);
            statusInfo.setLastActive(this.lastActive);
            statusInfo.setRole(this.role);

            return statusInfo;

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get UserStatusDTO for UI display (thread-safe)
     * @return UserStatusDTO with formatted values
     */
    public UserStatusDTO toUserStatusDTO() {
        lock.readLock().lock();
        try {
            if (!initialized) {
                return null;
            }

            return UserStatusDTO.builder()
                    .username(this.username)
                    .userId(this.userId)
                    .name(this.name)
                    .status(this.status)
                    .lastActive(formatDateTime(this.lastActive))
                    .role(this.role)
                    .build();

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
     * Create a complete status entry from basic user data (thread-safe)
     * Used for initial setup from UserService
     * @param username Username
     * @param userId User ID
     * @param name Display name
     * @param role User role
     * @param defaultStatus Default status to set
     */
    public void initializeFromUserData(String username, Integer userId, String name, String role, String defaultStatus) {
        lock.writeLock().lock();
        try {
            this.username = username;
            this.userId = userId;
            this.name = name;
            this.role = role;
            this.status = defaultStatus;
            this.lastActive = null; // No activity yet

            this.lastUpdated = System.currentTimeMillis();
            this.initialized = true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clear cache entry (for midnight reset)
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            this.username = null;
            this.userId = null;
            this.name = null;
            this.status = null;
            this.lastActive = null;
            this.role = null;

            this.lastUpdated = 0;
            this.initialized = false;

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
     * Format datetime for display
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "--/--/---- :: --:--";
        }
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}