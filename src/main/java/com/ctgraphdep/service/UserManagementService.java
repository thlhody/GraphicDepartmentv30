package com.ctgraphdep.service;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.fileOperations.data.UserDataService;
import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.cache.StatusCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * REFACTORED UserManagementService using StatusCacheService + UserDataService.
 * Admin operations: Direct network writes with cache sync.
 * Key Changes:
 * - All reads from StatusCacheService (cache-based)
 * - All writes via UserDataService. Admin*() methods (direct network)
 * - Cache updates after successful writes (write-through pattern)
 * - No more batch user operations or sanitization
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementService {
    private final UserDataService userDataService;           // NEW - Direct admin file operations
    private final StatusCacheService statusCacheService;     // NEW - Cache operations
    private final HolidayManagementService holidayManagementService;
    private final PasswordEncoder passwordEncoder;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public UserManagementService(
            UserDataService userDataService,                 // NEW dependency
            StatusCacheService statusCacheService,           // NEW dependency
            HolidayManagementService holidayManagementService,
            PasswordEncoder passwordEncoder) {
        this.userDataService = userDataService;
        this.statusCacheService = statusCacheService;
        this.holidayManagementService = holidayManagementService;
        this.passwordEncoder = passwordEncoder;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * REFACTORED: Get all users from cache instead of file I/O
     */
    public List<User> getAllUsers() {
        lock.readLock().lock();
        try {
            // Get from cache - no file I/O
            List<User> users = statusCacheService.getAllUsersAsUserObjects();
            LoggerUtil.debug(this.getClass(), String.format("Admin retrieved %d users from cache", users.size()));

            return users;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * REFACTORED: Get non-admin users from cache
     */
    public List<User> getNonAdminUsers() {
        return getAllUsers().stream().filter(user -> !user.hasRole(SecurityConstants.ROLE_ADMIN)).collect(Collectors.toList());
    }

    /**
     * REFACTORED: Get user by ID from cache
     */
    public Optional<User> getUserById(Integer userId) {
        return statusCacheService.getUserByIdAsUserObject(userId);
    }

    /**
     * REFACTORED: Get user by username from cache
     */
    public Optional<User> getUserByUsername(String username) {
        return statusCacheService.getUserAsUserObject(username);
    }

    /**
     * REFACTORED: Save new user with admin direct write + cache sync
     */
    public void saveUser(User user, Integer paidHolidayDays) {
        validateNewUser(user);
        lock.writeLock().lock();
        try {
            // Get current users from cache to determine next ID
            List<User> users = getAllUsers();

            // Set new user ID
            int maxId = users.stream().mapToInt(User::getUserId).max().orElse(0);user.setUserId(maxId + 1);

            // Encrypt password before saving
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }

            // ADMIN WRITE PATTERN: Direct network write
            userDataService.adminWriteUserNetworkOnly(user);

            // Update cache with new user (write-through)
            statusCacheService.updateUserInCache(user);

            // Initialize paid holiday entry for the new user
            ensureHolidayEntry(user, paidHolidayDays);

            LoggerUtil.info(this.getClass(), String.format("Admin created new user: %s (ID: %d) with %d holiday days", user.getUsername(), user.getUserId(), paidHolidayDays));

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * REFACTORED: Change password with admin direct write (network only)
     */
    public boolean changePassword(Integer userId, String currentPassword, String newPassword) {
        lock.writeLock().lock();
        try {
            // Get user from cache first
            Optional<User> userOptional = getUserById(userId);
            if (userOptional.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("Admin password change failed: user ID %d not found", userId));
                return false;
            }

            User user = userOptional.get();
            String username = user.getUsername();

            // Read complete user data with password for verification
            Optional<User> completeUser = userDataService.adminReadUserNetworkOnly(username, userId);
            if (completeUser.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("Admin password change failed: complete user data not found %s-%d", username, userId));
                return false;
            }

            User userWithPassword = completeUser.get();

            // Verify current password
            if (!passwordEncoder.matches(currentPassword, userWithPassword.getPassword())) {
                LoggerUtil.info(this.getClass(), String.format("Admin password change failed for user ID %d: incorrect current password", userId));
                return false;
            }

            // Update password and write to network
            userWithPassword.setPassword(passwordEncoder.encode(newPassword));
            userDataService.adminWriteUserNetworkOnly(userWithPassword);

            // Update cache (password won't be stored in cache, but other data might have changed)
            statusCacheService.updateUserInCache(userWithPassword);

            LoggerUtil.info(this.getClass(), String.format("Admin successfully changed password for user ID %d", userId));
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * REFACTORED: Update user with admin direct write + cache sync
     */
    public void updateUser(User user, Integer paidHolidayDays) {
        validateExistingUser(user);
        lock.writeLock().lock();
        try {
            String username = user.getUsername();
            Integer userId = user.getUserId();

            // Get existing user data with password
            Optional<User> existingUserOptional = userDataService.adminReadUserNetworkOnly(username, userId);
            if (existingUserOptional.isEmpty()) {
                throw new RuntimeException("User not found for update: " + username);
            }

            User existingUser = existingUserOptional.get();

            // Handle password update
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                user.setPassword(existingUser.getPassword());
            } else if (!user.getPassword().startsWith("$2a$")) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }

            // Preserve admin role if exists
            if (existingUser.isAdmin()) {
                user.setRole(SecurityConstants.ROLE_ADMIN);
            }

            // ADMIN WRITE PATTERN: Direct network write
            userDataService.adminWriteUserNetworkOnly(user);

            // Update cache (write-through)
            statusCacheService.updateUserInCache(user);

            // Ensure holiday entry exists and update if needed
            if (paidHolidayDays != null) {
                ensureHolidayEntry(user, paidHolidayDays);
            }

            LoggerUtil.info(this.getClass(), String.format("Admin updated user: %s (ID: %d) with %d holiday days", username, userId, paidHolidayDays));

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * REFACTORED: Delete user with admin direct delete + cache sync
     */
    public void deleteUser(Integer userId) {
        lock.writeLock().lock();
        try {
            // Get user from cache first
            Optional<User> userOptional = getUserById(userId);
            if (userOptional.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("Admin delete failed: user ID %d not found", userId));
                return;
            }

            User userToDelete = userOptional.get();
            if (userToDelete.isAdmin()) {
                throw new IllegalArgumentException("Cannot delete admin user");
            }

            String username = userToDelete.getUsername();

            // ADMIN DELETE PATTERN: Direct network delete
            boolean deleted = userDataService.adminDeleteUserNetworkOnly(username, userId);

            if (deleted) {
                // Remove from cache
                statusCacheService.removeUserFromCache(username);

                LoggerUtil.info(this.getClass(), String.format("Admin deleted user: %s (ID: %d)", username, userId));
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Admin delete failed: could not delete user file %s-%d", username, userId));
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========================================================================
    // VALIDATION METHODS (UPDATED TO USE CACHE)
    // ========================================================================

    private void validateNewUser(User user) {
        if (user.getUserId() != null) {
            throw new IllegalArgumentException("New user should not have an ID");
        }
        validateUserData(user);
        checkUsernameUniqueness(user);
    }

    private void validateExistingUser(User user) {
        if (user.getUserId() == null) {
            throw new IllegalArgumentException("Existing user must have an ID");
        }
        validateUserData(user);
        checkUsernameUniqueness(user);
    }

    private void validateUserData(User user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (user.getName() == null || user.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        if (user.getEmployeeId() == null) {
            throw new IllegalArgumentException("Employee ID cannot be null");
        }
        if (user.getSchedule() == null || user.getSchedule() < 1) {
            throw new IllegalArgumentException("Invalid schedule value");
        }
        if (SecurityConstants.ROLE_ADMIN.equals(user.getRole())) {
            throw new IllegalArgumentException("Cannot assign admin role");
        }
    }

    /**
     * REFACTORED: Check username uniqueness using cache
     */
    private void checkUsernameUniqueness(User user) {
        // Get all users from cache for uniqueness check
        List<User> allUsers = getAllUsers();

        boolean usernameExists = allUsers.stream().anyMatch(existingUser -> existingUser.getUsername().equals(user.getUsername()) && !existingUser.getUserId().equals(user.getUserId()));

        if (usernameExists) {
            throw new IllegalArgumentException("Username already exists");
        }
    }

    // ========================================================================
    // HELPER METHODS (UNCHANGED)
    // ========================================================================

    private void ensureHolidayEntry(User user, Integer paidHolidayDays) {
        List<PaidHolidayEntryDTO> holidayEntries = holidayManagementService.loadHolidayList();

        // Check if user already has an entry
        boolean hasEntry = holidayEntries.stream().anyMatch(entry -> entry.getUserId().equals(user.getUserId()));

        if (!hasEntry) {
            // Create new entry if user doesn't have one
            PaidHolidayEntryDTO newEntry = PaidHolidayEntryDTO.fromUser(user);
            newEntry.setPaidHolidayDays(paidHolidayDays);
            holidayEntries.add(newEntry);
            holidayManagementService.saveHolidayList(holidayEntries);

            LoggerUtil.info(this.getClass(), String.format("Created new holiday entry for user %s with %d days", user.getUsername(), paidHolidayDays));
        } else {
            // Update existing entry
            holidayManagementService.updateUserHolidayDays(user.getUserId(), paidHolidayDays);

            LoggerUtil.info(this.getClass(), String.format("Updated holiday days for user %s to %d days", user.getUsername(), paidHolidayDays));
        }
    }
}