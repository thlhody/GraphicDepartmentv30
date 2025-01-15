package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.AuthenticationStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.security.CustomUserDetails;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import groovy.util.logging.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AuthenticationService {

    private static final TypeReference<List<User>> USER_LIST_TYPE = new TypeReference<>() {};
    private final DataAccessService dataAccess;
    private final PathConfig pathConfig;
    private final PasswordEncoder passwordEncoder;
    private final SessionRecoveryService sessionRecoveryService;


    public AuthenticationService(
            DataAccessService dataAccess, PathConfig pathConfig,
            PasswordEncoder passwordEncoder, SessionRecoveryService sessionRecoveryService) {
        this.dataAccess = dataAccess;
        this.pathConfig = pathConfig;
        this.passwordEncoder = passwordEncoder;
        this.sessionRecoveryService = sessionRecoveryService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public AuthenticationStatus getAuthenticationStatus() {
        boolean networkAvailable = dataAccess.fileExists(dataAccess.getUsersPath());
        boolean offlineModeAvailable = isOfflineModeAvailable();

        return new AuthenticationStatus(
                networkAvailable,
                offlineModeAvailable,
                networkAvailable ? "ONLINE" : "OFFLINE"
        );
    }

    public UserDetails authenticateUser(String username, String password, boolean offlineMode) {
        UserDetails userDetails = offlineMode ?
                loadUserOffline(username) :
                loadUser(username);

        if (passwordEncoder.matches(password, userDetails.getPassword())) {
            LoggerUtil.info(this.getClass(),
                    String.format("Successfully authenticated user %s", username));
            return userDetails;
        }

        LoggerUtil.warn(this.getClass(), "Invalid credentials for user: " + username);
        throw new BadCredentialsException("Invalid credentials");
    }

    public UserDetails loadUser(String username) {
        Optional<User> user = findUserInFile(dataAccess.getUsersPath(), username);
        if (user.isEmpty()) {
            LoggerUtil.warn(this.getClass(), "User not found: " + username);
            throw new UsernameNotFoundException("User not found: " + username);
        }

        LoggerUtil.info(this.getClass(), "Loading user details for: " + username);
        return new CustomUserDetails(user.get());
    }

    public UserDetails loadUserOffline(String username) {
        Path offlineUsersPath = dataAccess.getUsersPath();
        Optional<User> user = findUserInFile(offlineUsersPath, username);

        if (user.isEmpty()) {
            LoggerUtil.warn(this.getClass(),
                    "User not found in offline storage: " + username);
            throw new UsernameNotFoundException(
                    "User not found in offline storage: " + username);
        }

        LoggerUtil.info(this.getClass(), "Loaded user from offline storage: " + username);
        return new CustomUserDetails(user.get());
    }

    public void handleSuccessfulLogin(String username, boolean rememberMe) {
        try {
            CustomUserDetails userDetails = (CustomUserDetails) loadUser(username);
            User user = userDetails.getUser();

            try {
                // Create local directories
                ensureLocalDirectories(user.isAdmin());
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        "Failed to create local directories: " + e.getMessage() +
                                " - continuing with login");
            }

            // Store user data locally if remember me is enabled
            if (rememberMe) {
                try {
                    storeUserDataLocally(user);
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(),
                            "Failed to store user data locally: " + e.getMessage() +
                                    " - continuing with login");
                }
            }

            // Add session recovery here
            if (!user.isAdmin()) {
                try {
                    sessionRecoveryService.recoverSession(user.getUsername(), user.getUserId());
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(),
                            "Failed to recover session: " + e.getMessage() +
                                    " - continuing with login");
                }
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Successfully handled login for user: %s (rememberMe: %s)",
                            username, rememberMe));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error handling login for user %s: %s",
                            username, e.getMessage()));
            throw new RuntimeException("Failed to handle login", e);
        }
    }

    private Optional<User> findUserInFile(Path filePath, String username) {
        try {
            LoggerUtil.debug(this.getClass(),
                    String.format("Looking for user %s in file: %s", username, filePath));

            List<User> users = dataAccess.readFile(filePath, USER_LIST_TYPE, true);

            LoggerUtil.debug(this.getClass(),
                    String.format("Found %d users in file", users.size()));

            Optional<User> foundUser = users.stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();

            LoggerUtil.debug(this.getClass(),
                    String.format("User %s %s", username,
                            foundUser.isPresent() ? "found" : "not found"));

            return foundUser;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error reading user data from %s: %s",
                            filePath, e.getMessage()));
            return Optional.empty();
        }
    }

    private boolean isOfflineModeAvailable() {
        return dataAccess.fileExists(dataAccess.getUsersPath());
    }

    private void ensureLocalDirectories(boolean isAdmin) {
        try {
            List<Path> directories = Arrays.asList(
                    pathConfig.getUserSessionDir(),
                    pathConfig.getUserWorktimeDir(),
                    pathConfig.getUserRegisterDir()
            );

            // Add admin directories if user is admin
            if (isAdmin) {
                List<Path> adminDirs = Arrays.asList(
                        pathConfig.getAdminWorktimeDir(),
                        pathConfig.getAdminRegisterDir(),
                        pathConfig.getAdminBonusDir(),
                        pathConfig.getLoginDir()
                );
                directories = new ArrayList<>(directories);
                directories.addAll(adminDirs);
            }

            for (Path dir : directories) {
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                    LoggerUtil.debug(this.getClass(), "Created directory: " + dir);
                }
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Created %d local directories successfully", directories.size()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error creating local directories: " + e.getMessage());
            throw new RuntimeException("Failed to create local directories", e);
        }
    }

    private void storeUserDataLocally(User user) {
        try {
            Path usersPath = dataAccess.getUsersPath();
            List<User> users = dataAccess.readFile(usersPath, USER_LIST_TYPE, true);

            // Remove existing user if present
            users.removeIf(u -> u.getUsername().equals(user.getUsername()));

            // Add updated user
            users.add(user);

            // Save updated user list
            dataAccess.writeFile(usersPath, users);

            LoggerUtil.info(this.getClass(),
                    String.format("Stored user data locally for: %s", user.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error storing user data locally: %s", e.getMessage()));
            throw new RuntimeException("Failed to store user data locally", e);
        }
    }
}