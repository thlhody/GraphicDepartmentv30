package com.ctgraphdep.service;

import com.ctgraphdep.fileOperations.data.UserDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * REFACTORED UserServiceImpl using AllUsersCacheService + UserDataService.
 * User operations: Local writes with network sync + cache updates.
 * Key Changes:
 * - All reads from AllUsersCacheService (cache-based, no file I/O)
 * - User writes via UserDataService. User*() methods (local → network sync)
 * - Cache updates after successful writes (write-through pattern)
 * - No more sanitization (cache provides clean User objects)
 * - No more batch operations (individual user operations only)
 */
@Service
public class UserServiceImpl implements UserService {
    private final UserDataService userDataService;           // NEW - User file operations
    private final AllUsersCacheService allUsersCacheService;     // NEW - Cache operations
    private final MainDefaultUserContextService mainDefaultUserContextService;     // NEW - Current user context
    private final PasswordEncoder passwordEncoder;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Autowired
    public UserServiceImpl(UserDataService userDataService, AllUsersCacheService allUsersCacheService, MainDefaultUserContextService mainDefaultUserContextService, PasswordEncoder passwordEncoder) {
        this.userDataService = userDataService;
        this.allUsersCacheService = allUsersCacheService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        this.passwordEncoder = passwordEncoder;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * REFACTORED: Get user by username from cache (no passwords)
     */
    @Override
    public Optional<User> getUserByUsername(String username) {
        Optional<User> user = allUsersCacheService.getUserAsUserObject(username);

        if (user.isPresent()) {
            LoggerUtil.debug(this.getClass(), String.format("Retrieved user from cache: %s (ID: %d)", username, user.get().getUserId()));
        } else {
            LoggerUtil.debug(this.getClass(), "User not found in cache: " + username);
        }
        return user;
    }

    /**
     * REFACTORED: Get user by ID from cache
     */
    @Override
    public Optional<User> getUserById(Integer userId) {
        Optional<User> user = allUsersCacheService.getUserByIdAsUserObject(userId);

        if (user.isPresent()) {
            LoggerUtil.debug(this.getClass(), String.format("Retrieved user by ID from cache: %d", userId));
        } else {
            LoggerUtil.debug(this.getClass(), "User not found by ID in cache: " + userId);
        }
        return user;
    }

    /**
     * REFACTORED: Get all users from cache
     */
    @Override
    public List<User> getAllUsers() {
        List<User> users = allUsersCacheService.getAllUsersAsUserObjects();

        LoggerUtil.debug(this.getClass(), String.format("Retrieved %d users from cache", users.size()));

        return users;
    }

    /**
     * REFACTORED: Get non-admin users from cache
     */
    @Override
    public List<User> getNonAdminUsers(List<User> allUsers) {
        // Use cache method instead of filtering provided list
        return allUsersCacheService.getNonAdminUsersAsUserObjects();
    }

    /**
     * REFACTORED: Update user with local write + network sync + cache update
     */
    @Override
    public boolean updateUser(User user) {
        lock.writeLock().lock();
        try {
            String username = user.getUsername();
            Integer userId = user.getUserId();

            // Get current user context to determine if this its own data
            String currentUsername = mainDefaultUserContextService.getCurrentUsername();

            // Validate user can update this data
            if (!currentUsername.equals(username)) {
                LoggerUtil.warn(this.getClass(), String.format("User %s attempted to update data for %s - access denied", currentUsername, username));
                return false;
            }

            // Get existing user data to preserve password
            Optional<User> existingUserOptional = userDataService.userReadLocalReadOnly(username, userId, currentUsername);
            if (existingUserOptional.isEmpty()) {
                LoggerUtil.error(this.getClass(), String.format("User update failed: existing user not found %s-%d", username, userId));
                return false;
            }

            User existingUser = existingUserOptional.get();

            // Validate update permissions
            validateUserUpdate(user, existingUser);

            // Handle password preservation
            encryptPassword(user, existingUser);

            // USER WRITE PATTERN: Local → Network sync
            userDataService.userWriteLocalWithSyncAndBackup(user);

            // Update cache (write-through)
            allUsersCacheService.updateUserInCache(user);

            LoggerUtil.info(this.getClass(), String.format("User successfully updated: %s (ID: %d)", username, userId));
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * REFACTORED: Delete user (admin only - delegates to UserManagementService logic)
     */
    @Override
    public void deleteUser(Integer userId) {

        lock.writeLock().lock();
        try {
            Optional<User> userToDelete = getUserById(userId);
            if (userToDelete.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("Delete failed: user ID %d not found", userId));
                return;
            }

            User user = userToDelete.get();
            if (user.isAdmin()) {
                throw new IllegalArgumentException("Cannot delete admin user");
            }

            String username = user.getUsername();

            // ADMIN DELETE PATTERN: Direct network delete (since this is admin operation)
            boolean deleted = userDataService.adminDeleteUserNetworkOnly(username, userId);

            if (deleted) {
                // Remove from cache
                allUsersCacheService.removeUserFromCache(username);

                LoggerUtil.info(this.getClass(), String.format("Successfully deleted user: %s (ID: %d)", username, userId));
            } else {
                LoggerUtil.error(this.getClass(), String.format("Failed to delete user file: %s-%d", username, userId));
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * REFACTORED: Change password for current user (users can only change their own)
     */
    @Override
    public boolean changePassword(Integer userId, String currentPassword, String newPassword) {
        lock.writeLock().lock();
        try {
            // Get current user context
            String currentUsername = mainDefaultUserContextService.getCurrentUsername();
            Integer currentUserId = mainDefaultUserContextService.getCurrentUserId();

            // Users can only change their own password (unless admin)
            if (!mainDefaultUserContextService.isCurrentUserAdmin() && !userId.equals(currentUserId)) {
                LoggerUtil.warn(this.getClass(), String.format("User %s attempted to change password for user ID %d - access denied", currentUsername, userId));
                return false;
            }

            // Get user from cache first
            Optional<User> userOptional = getUserById(userId);
            if (userOptional.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("Password change failed: user ID %d not found", userId));
                return false;
            }

            User user = userOptional.get();
            String username = user.getUsername();

            // Read complete user data with password
            Optional<User> completeUser = userDataService.userReadLocalReadOnly(username, userId, currentUsername);
            if (completeUser.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("Password change failed: complete user data not found %s-%d", username, userId));
                return false;
            }

            User userWithPassword = completeUser.get();

            // Verify current password
            if (!passwordEncoder.matches(currentPassword, userWithPassword.getPassword())) {
                LoggerUtil.info(this.getClass(), String.format("Password change failed for user ID %d: incorrect current password", userId));
                return false;
            }

            // Update password
            userWithPassword.setPassword(passwordEncoder.encode(newPassword));

            // USER WRITE PATTERN: Local → Network sync
            userDataService.userWriteLocalWithSyncAndBackup(userWithPassword);

            // Update cache (password won't be stored in cache, but other data might have changed)
            allUsersCacheService.updateUserInCache(userWithPassword);

            LoggerUtil.info(this.getClass(), String.format("Successfully changed password for user ID %d", userId));
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Find user by employee ID (cache-based)
     */
    public Optional<User> findByEmployeeId(Integer employeeId) {
        return getAllUsers().stream().filter(user -> employeeId.equals(user.getEmployeeId())).findFirst();
    }

    // ========================================================================
    // VALIDATION METHODS (UPDATED)
    // ========================================================================

    /**
     * REFACTORED: Validate user update using cache data
     */
    private void validateUserUpdate(User user, User existingUser) {
        if (existingUser.isAdmin() && !user.isAdmin()) {
            throw new IllegalArgumentException("Cannot remove admin role");
        }

        if (!existingUser.isAdmin() && user.isAdmin()) {
            throw new IllegalArgumentException("Cannot grant admin role");
        }

        // Check username uniqueness using cache
        List<User> allUsers = getAllUsers();
        boolean usernameExists = allUsers.stream().anyMatch(other -> other.getUsername().equals(user.getUsername()) && !other.getUserId().equals(user.getUserId()));

        if (usernameExists) {
            throw new IllegalArgumentException("Username already exists");
        }
    }

    /**
     * Handle password encryption (preserve existing if not provided)
     */
    private void encryptPassword(User user, User existingUser) {
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            // If updating and no new password provided, keep existing password
            if (existingUser != null) {
                user.setPassword(existingUser.getPassword());
            }
        } else if (!user.getPassword().startsWith("$2a$")) {
            // If password is not already encrypted, encrypt it
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
    }
}