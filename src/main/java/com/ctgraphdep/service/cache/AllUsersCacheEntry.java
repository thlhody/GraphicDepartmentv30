package com.ctgraphdep.service.cache;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UserStatusInfo;
import com.ctgraphdep.model.dto.UserStatusDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe cache entry for user status data.
 * Enhanced to store complete user information for cache-based operations.
 */
@Data
public class AllUsersCacheEntry {

    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // === USER STATUS DATA ===
    private String username;
    private Integer userId;
    private String name;
    private String status;
    private LocalDateTime lastActive;
    private String role;

    // === ADDITIONAL USER DATA (for complete User object conversion) ===
    private Integer employeeId;
    private Integer schedule;
    private Integer paidHolidayDays;

    // === CACHE METADATA ===
    private long lastUpdated;
    private boolean initialized;

    /**
     * Default constructor
     */
    public AllUsersCacheEntry() {
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

            // Note: UserStatusInfo doesn't contain employeeId, schedule, paidHolidayDays
            // These will need to be set separately or loaded from User data

            // Update metadata
            this.lastUpdated = System.currentTimeMillis();
            this.initialized = true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Initialize cache entry from complete User data (thread-safe)
     * This is the preferred method for complete user information
     * @param user Complete user object
     * @param defaultStatus Default status to set if no current status
     */
    public void initializeFromCompleteUser(User user, String defaultStatus) {
        lock.writeLock().lock();
        try {
            if (user == null) {
                this.initialized = false;
                return;
            }

            // Set all user fields
            this.username = user.getUsername();
            this.userId = user.getUserId();
            this.name = user.getName();
            this.role = user.getRole();
            this.employeeId = user.getEmployeeId();
            this.schedule = user.getSchedule();
            this.paidHolidayDays = user.getPaidHolidayDays();

            // Set default status (will be updated by network flags)
            this.status = defaultStatus;
            this.lastActive = null; // No activity yet

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
     * Update user information (name, role, employeeId, schedule, holidayDays) from UserService data (thread-safe)
     * @param name User's display name
     * @param role User's role
     * @param employeeId Employee ID
     * @param schedule Work schedule
     * @param paidHolidayDays Paid holiday days
     */
    public void updateUserInfo(String name, String role, Integer employeeId, Integer schedule, Integer paidHolidayDays) {
        lock.writeLock().lock();
        try {
            if (!initialized) {
                return;
            }

            this.name = name;
            this.role = role;
            this.employeeId = employeeId;
            this.schedule = schedule;
            this.paidHolidayDays = paidHolidayDays;
            this.lastUpdated = System.currentTimeMillis();

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update user information from User object (thread-safe)
     * @param user Complete user object with updated information
     */
    public void updateFromUser(User user) {
        lock.writeLock().lock();
        try {
            if (!initialized || user == null) {
                return;
            }

            // Update all user info (keep current status and lastActive)
            this.name = user.getName();
            this.role = user.getRole();
            this.employeeId = user.getEmployeeId();
            this.schedule = user.getSchedule();
            this.paidHolidayDays = user.getPaidHolidayDays();
            this.lastUpdated = System.currentTimeMillis();

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Convert cache entry to complete User object (thread-safe)
     * This is the key method for cache-based user operations
     * @return Complete User object without password
     */
    public User toUser() {
        lock.readLock().lock();
        try {
            if (!initialized) {
                return null;
            }

            User user = new User();
            user.setUserId(this.userId);
            user.setUsername(this.username);
            user.setName(this.name);
            user.setRole(this.role);
            user.setEmployeeId(this.employeeId);
            user.setSchedule(this.schedule);
            user.setPaidHolidayDays(this.paidHolidayDays);

            // Don't set password - this is cache data
            user.setPassword(null);

            return user;

        } finally {
            lock.readLock().unlock();
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

            // These will need to be populated separately or from complete User data
            this.employeeId = null;
            this.schedule = null;
            this.paidHolidayDays = null;

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
            this.employeeId = null;
            this.schedule = null;
            this.paidHolidayDays = null;

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
     * Check if entry has complete user data (not just status data)
     * @return true if has employeeId, schedule, paidHolidayDays
     */
    public boolean hasCompleteUserData() {
        lock.readLock().lock();
        try {
            return initialized && employeeId != null && schedule != null && paidHolidayDays != null;
        } finally {
            lock.readLock().unlock();
        }
    }

    // === PRIVATE HELPER METHODS ===

    /**
     * Format datetime for display
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "--/--/---- :: --:--";
        }
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Check if role indicates admin user
     */
    private boolean isAdminRole(String role) {
        return role != null && (role.equals("ROLE_ADMIN") || role.contains("ADMIN"));
    }
}