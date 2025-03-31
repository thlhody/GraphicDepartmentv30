package com.ctgraphdep.service;

import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementService {
    private final DataAccessService dataAccess;
    private final HolidayManagementService holidayService;
    private final PasswordEncoder passwordEncoder;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public UserManagementService(DataAccessService dataAccess, HolidayManagementService holidayService, PasswordEncoder passwordEncoder) {
        this.dataAccess = dataAccess;
        this.holidayService = holidayService;
        this.passwordEncoder = passwordEncoder;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public List<User> getAllUsers() {
        lock.readLock().lock();
        try {
            return dataAccess.readUsersNetwork();
        } finally {
            lock.readLock().unlock();
        }
    }


    public List<User> getNonAdminUsers() {
        return getAllUsers().stream()
                .filter(user -> !user.hasRole("ADMIN"))  // Use hasRole method instead
                .collect(Collectors.toList());
    }

    public Optional<User> getUserById(Integer userId) {
        return getAllUsers().stream()
                .filter(user -> user.getUserId().equals(userId))
                .findFirst();
    }

    public Optional<User> getUserByUsername(String username) {
        return getAllUsers().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst();
    }

    public void saveUser(User user, Integer paidHolidayDays) {
        validateNewUser(user);
        lock.writeLock().lock();
        try {
            List<User> users = getAllUsers();

            // Set new user ID
            int maxId = users.stream()
                    .mapToInt(User::getUserId)
                    .max()
                    .orElse(0);
            user.setUserId(maxId + 1);

            // Encrypt password before saving
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }

            users.add(user);
            saveUserList(users);

            // Initialize paid holiday entry for the new user
            ensureHolidayEntry(user, paidHolidayDays);

            LoggerUtil.info(this.getClass(),
                    String.format("Created new user: %s (ID: %d) with %d holiday days",
                            user.getUsername(), user.getUserId(), paidHolidayDays));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean changePassword(Integer userId, String currentPassword, String newPassword) {
        lock.writeLock().lock();
        try {
            List<User> users = getAllUsers();
            int index = findUserIndex(users, userId);

            if (index >= 0) {
                User user = users.get(index);

                // Verify current password
                if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                    LoggerUtil.info(this.getClass(),
                            String.format("Password change failed for user ID %d: incorrect current password",
                                    userId));
                    return false;
                }

                // Update to new password
                user.setPassword(passwordEncoder.encode(newPassword));
                users.set(index, user);
                saveUserList(users);

                LoggerUtil.info(this.getClass(),
                        String.format("Password successfully changed for user ID %d", userId));
                return true;
            }

            LoggerUtil.warn(this.getClass(),
                    String.format("Password change failed: user ID %d not found", userId));
            return false;

        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateUser(User user, Integer paidHolidayDays) {
        validateExistingUser(user);
        lock.writeLock().lock();
        try {
            List<User> users = getAllUsers();

            int index = findUserIndex(users, user.getUserId());
            if (index != -1) {
                User existingUser = users.get(index);

                // Handle password update
                if (user.getPassword() == null || user.getPassword().isEmpty()) {
                    user.setPassword(existingUser.getPassword());
                } else if (!user.getPassword().startsWith("$2a$")) {
                    user.setPassword(passwordEncoder.encode(user.getPassword()));
                }

                // Preserve admin role if exists
                if (existingUser.isAdmin()) {
                    user.setRole("ADMIN");
                }

                users.set(index, user);
                saveUserList(users);

                // Ensure holiday entry exists and update if needed
                if (paidHolidayDays != null) {
                    ensureHolidayEntry(user, paidHolidayDays);
                }

                LoggerUtil.info(this.getClass(),
                        String.format("Updated user: %s (ID: %d) with %d holiday days",
                                user.getUsername(), user.getUserId(), paidHolidayDays));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void ensureHolidayEntry(User user, Integer paidHolidayDays) {
        List<PaidHolidayEntryDTO> holidayEntries = holidayService.getHolidayList();

        // Check if user already has an entry
        boolean hasEntry = holidayEntries.stream()
                .anyMatch(entry -> entry.getUserId().equals(user.getUserId()));

        if (!hasEntry) {
            // Create new entry if user doesn't have one
            PaidHolidayEntryDTO newEntry = PaidHolidayEntryDTO.fromUser(user);
            newEntry.setPaidHolidayDays(paidHolidayDays);
            holidayEntries.add(newEntry);
            holidayService.saveHolidayList(holidayEntries);

            LoggerUtil.info(this.getClass(),
                    String.format("Created new holiday entry for user %s with %d days",
                            user.getUsername(), paidHolidayDays));
        } else {
            // Update existing entry
            holidayService.updateUserHolidayDays(user.getUserId(), paidHolidayDays);

            LoggerUtil.info(this.getClass(),
                    String.format("Updated holiday days for user %s to %d days",
                            user.getUsername(), paidHolidayDays));
        }
    }

    public void deleteUser(Integer userId) {
        lock.writeLock().lock();
        try {
            List<User> users = getAllUsers();

            int index = findUserIndex(users, userId);
            if (index != -1) {
                User userToDelete = users.get(index);
                if (userToDelete.isAdmin()) {
                    throw new IllegalArgumentException("Cannot delete admin user");
                }

                users.remove(index);
                saveUserList(users);

                LoggerUtil.info(this.getClass(),
                        String.format("Deleted user: %s (ID: %d)",
                                userToDelete.getUsername(), userId));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

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
        if ("ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("Cannot assign admin role");
        }
    }

    private void checkUsernameUniqueness(User user) {
        boolean usernameExists = getAllUsers().stream()
                .anyMatch(existingUser ->
                        existingUser.getUsername().equals(user.getUsername()) &&
                                !existingUser.getUserId().equals(user.getUserId()));

        if (usernameExists) {
            throw new IllegalArgumentException("Username already exists");
        }
    }

    private int findUserIndex(List<User> users, Integer userId) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUserId().equals(userId)) {
                return i;
            }
        }
        return -1;
    }

    private void saveUserList(List<User> users) {
        lock.writeLock().lock();
        try {
            dataAccess.writeUsersNetwork(users);
        } finally {
            lock.writeLock().unlock();
        }
    }
}