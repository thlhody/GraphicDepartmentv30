package com.ctgraphdep.service;

import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    private final DataAccessService dataAccess;
    private final PasswordEncoder passwordEncoder;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final TypeReference<List<User>> USER_LIST_TYPE = new TypeReference<>() {};

    @Autowired
    public UserServiceImpl(DataAccessService dataAccess, PasswordEncoder passwordEncoder) {
        this.dataAccess = dataAccess;
        this.passwordEncoder = passwordEncoder;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Override
    public Optional<User> getUserByUsername(String username) {
        lock.readLock().lock();
        try {
            Optional<User> user = getAllUsers().stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();

            LoggerUtil.debug(this.getClass(),
                    String.format("Found user by username '%s': %s",
                            username,
                            user.isPresent() ? "Yes" : "No"));

            if (user.isPresent()) {
                LoggerUtil.debug(this.getClass(),
                        String.format("User details - ID: %d, Name: %s",
                                user.get().getUserId(),
                                user.get().getName()));

                User sanitized = sanitizeUser(user.get());
                assert sanitized != null;
                LoggerUtil.debug(this.getClass(),
                        String.format("Sanitized user details - ID: %d, Name: %s",
                                sanitized.getUserId(),
                                sanitized.getName()));

                return Optional.of(sanitized);
            }

            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<User> getUserById(Integer userId) {
        lock.readLock().lock();
        try {
            return getAllUsers().stream()
                    .filter(user -> user.getUserId().equals(userId))
                    .findFirst()
                    .map(this::sanitizeUser);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<User> getAdminUser() {
        lock.readLock().lock();
        try {
            return getAllUsers().stream()
                    .filter(User::isAdmin)  // Assuming isAdmin() is a method in the User class
                    .findFirst()
                    .map(this::sanitizeUser);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<User> getAllUsers() {
        lock.readLock().lock();
        try {
            return dataAccess.readFile(dataAccess.getUsersPath(), USER_LIST_TYPE, true);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<User> getNonAdminUsers(List<User> allUsers) {
        return allUsers.stream()
                .filter(user -> !user.isAdmin())
                .map(this::sanitizeUser)
                .collect(Collectors.toList());
    }

    @Override
    public String getPasswordHash(String username) {
        return "";
    }

    @Override
    public User saveUser(User user) {
        lock.writeLock().lock();
        try {
            List<User> users = getAllUsers();
            validateNewUser(user, users);

            if (user.getUserId() == null) {
                // New user
                user.setUserId(generateNextUserId(users));
                encryptPassword(user, null);
                users.add(user);
            } else {
                // Update existing user
                int index = findUserIndex(users, user.getUserId());
                if (index >= 0) {
                    User existingUser = users.get(index);
                    encryptPassword(user, existingUser);
                    users.set(index, user);
                } else {
                    throw new IllegalArgumentException("User not found for update: " + user.getUserId());
                }
            }

            saveUsers(users);
            return sanitizeUser(user);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean updateUser(User user) {
        lock.writeLock().lock();
        try {
            List<User> users = getAllUsers();
            int index = findUserIndex(users, user.getUserId());

            if (index >= 0) {
                validateUserUpdate(user, users.get(index), users);
                User existingUser = users.get(index);
                encryptPassword(user, existingUser);
                users.set(index, user);
                saveUsers(users);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteUser(Integer userId) {
        lock.writeLock().lock();
        try {
            List<User> users = getAllUsers();
            Optional<User> userToDelete = users.stream()
                    .filter(u -> u.getUserId().equals(userId))
                    .findFirst();

            if (userToDelete.isPresent()) {
                if (userToDelete.get().isAdmin()) {
                    throw new IllegalArgumentException("Cannot delete admin user");
                }
                users.removeIf(user -> user.getUserId().equals(userId));
                saveUsers(users);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean changePassword(Integer userId, String currentPassword, String newPassword) {
        lock.writeLock().lock();
        try {
            List<User> users = getAllUsers();
            int index = findUserIndex(users, userId);

            if (index >= 0) {
                User user = users.get(index);
                if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                    return false;
                }

                user.setPassword(passwordEncoder.encode(newPassword));
                users.set(index, user);
                saveUsers(users);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean validateCredentials(String username, String password) {
        return getUserByUsername(username)
                .map(user -> passwordEncoder.matches(password, user.getPassword()))
                .orElse(false);
    }

    private void saveUsers(List<User> users) {
        try {
            dataAccess.writeFile(dataAccess.getUsersPath(), users);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to save users: " + e.getMessage());
            throw new RuntimeException("Failed to save users", e);
        }
    }

    private void validateNewUser(User user, List<User> existingUsers) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        boolean usernameExists = existingUsers.stream()
                .anyMatch(existing -> existing.getUsername().equals(user.getUsername()) &&
                        !existing.getUserId().equals(user.getUserId()));

        if (usernameExists) {
            throw new IllegalArgumentException("Username already exists");
        }
    }

    private void validateUserUpdate(User user, User existingUser, List<User> allUsers) {
        if (existingUser.isAdmin() && !user.isAdmin()) {
            throw new IllegalArgumentException("Cannot remove admin role");
        }

        if (!existingUser.isAdmin() && user.isAdmin()) {
            throw new IllegalArgumentException("Cannot grant admin role");
        }

        boolean usernameExists = allUsers.stream()
                .anyMatch(other -> other.getUsername().equals(user.getUsername()) &&
                        !other.getUserId().equals(user.getUserId()));

        if (usernameExists) {
            throw new IllegalArgumentException("Username already exists");
        }
    }

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

    private Integer generateNextUserId(List<User> users) {
        return users.stream()
                .mapToInt(User::getUserId)
                .max()
                .orElse(0) + 1;
    }

    private int findUserIndex(List<User> users, Integer userId) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUserId().equals(userId)) {
                return i;
            }
        }
        return -1;
    }

    private User sanitizeUser(User user) {
        if (user == null) {
            LoggerUtil.error(this.getClass(), "Attempting to sanitize null user");
            return null;
        }

        try {
            User sanitized = new User();
            sanitized.setUserId(user.getUserId());
            sanitized.setUsername(user.getUsername());
            sanitized.setName(user.getName());       // Make sure this is preserved
            sanitized.setEmployeeId(user.getEmployeeId());
            sanitized.setSchedule(user.getSchedule());
            sanitized.setRole(user.getRole());

            // Verify sanitized user
            if (sanitized.getName() == null) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Sanitized user '%s' has null name (original name: %s)",
                                sanitized.getUsername(),
                                user.getName()));
            }

            // Log sanitization results
            LoggerUtil.debug(this.getClass(),
                    String.format("Found user by username '%s': Yes", user.getUsername()));
            LoggerUtil.debug(this.getClass(),
                    String.format("User details - ID: %d, Name: %s",
                            user.getUserId(), user.getName()));
            LoggerUtil.debug(this.getClass(),
                    String.format("Sanitized user details - ID: %d, Name: %s",
                            sanitized.getUserId(), sanitized.getName()));

            return sanitized;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error sanitizing user: %s", e.getMessage()));
            throw new RuntimeException("Failed to sanitize user", e);
        }
    }

    public Optional<User> findByEmployeeId(Integer employeeId) {
        return getAllUsers().stream()
                .filter(user -> employeeId.equals(user.getEmployeeId()))
                .findFirst();
    }

    // Add this method to UserServiceImpl
    private void validateUserData(User user) {
        LoggerUtil.debug(this.getClass(),
                String.format("Validating user data for '%s':", user.getUsername()));
        LoggerUtil.debug(this.getClass(),
                String.format("- ID: %d", user.getUserId()));
        LoggerUtil.debug(this.getClass(),
                String.format("- Name: %s", user.getName()));
        LoggerUtil.debug(this.getClass(),
                String.format("- Role: %s", user.getRole()));
    }
}