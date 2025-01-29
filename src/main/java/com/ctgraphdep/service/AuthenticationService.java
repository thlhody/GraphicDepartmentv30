package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.AuthenticationStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.security.CustomUserDetailsService;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import groovy.util.logging.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final SessionRecoveryService sessionRecoveryService;
    private final CustomUserDetailsService userDetailsService;  // Add this

    public AuthenticationService(
            DataAccessService dataAccess,
            PathConfig pathConfig, UserService userService,
            PasswordEncoder passwordEncoder,
            SessionRecoveryService sessionRecoveryService,
            CustomUserDetailsService userDetailsService) {  // Add this
        this.dataAccess = dataAccess;
        this.pathConfig = pathConfig;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.sessionRecoveryService = sessionRecoveryService;
        this.userDetailsService = userDetailsService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public AuthenticationStatus getAuthenticationStatus() {
        try {
            // Get status directly from PathConfig
            boolean networkAvailable = pathConfig.isNetworkAvailable();

            // Always check local path availability as fallback
            Path localUsersPath = dataAccess.getLocalUsersPath();
            boolean offlineModeAvailable = Files.exists(localUsersPath) &&
                    Files.size(localUsersPath) > 3;

            String status = networkAvailable ? "ONLINE" :
                    offlineModeAvailable ? "OFFLINE" : "UNAVAILABLE";

            LoggerUtil.info(this.getClass(),
                    String.format("Authentication Status: Network=%b, Local=%b, Status=%s",
                            networkAvailable, offlineModeAvailable, status));

            return new AuthenticationStatus(networkAvailable, offlineModeAvailable, status);

        } catch (IOException e) {
            LoggerUtil.error(this.getClass(),
                    "Error checking authentication status: " + e.getMessage());

            return new AuthenticationStatus(false, false, "UNAVAILABLE");
        }
    }

    public UserDetails authenticateUser(String username, String password, boolean offlineMode) {
        // Get the full user object without sanitization
        UserDetails userDetails = offlineMode ?
                userDetailsService.loadUserByUsernameOffline(username) :
                userDetailsService.loadUserByUsername(username);

        // Add debug logs to track password handling
        LoggerUtil.debug(this.getClass(),
                String.format("Authenticating user: %s, Encoded password from details: %s",
                        username, userDetails.getPassword()));

        LoggerUtil.debug(this.getClass(),
                String.format("Password matcher result: %b",
                        passwordEncoder.matches(password, userDetails.getPassword())));

        if (passwordEncoder.matches(password, userDetails.getPassword())) {
            LoggerUtil.info(this.getClass(),
                    String.format("Successfully authenticated user %s", username));
            return userDetails;
        }

        LoggerUtil.warn(this.getClass(), "Invalid credentials for user: " + username);
        throw new BadCredentialsException("Invalid credentials");
    }

    public void handleSuccessfulLogin(String username, boolean rememberMe) {
        try {
            // Use the authentication details directly instead of searching again
            Optional<User> userOptional = userService.getUserByUsername(username);

            if (userOptional.isEmpty()) {
                // As a fallback, try local storage
                userOptional = getUserFromLocalStorage(username);
            }

            if (userOptional.isPresent()) {
                User user = userOptional.get();

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
            } else {
                throw new UsernameNotFoundException("User not found after authentication");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error handling login for user %s: %s",
                            username, e.getMessage()));
            throw new RuntimeException("Failed to handle login", e);
        }
    }

    // Add this helper method to the AuthenticationService
    private Optional<User> getUserFromLocalStorage(String username) {
        try {
            Path localUsersPath = dataAccess.getLocalUsersPath();
            if (Files.exists(localUsersPath) && Files.size(localUsersPath) > 3) {
                List<User> localUsers = dataAccess.readFile(localUsersPath,
                        new TypeReference<List<User>>() {}, false);
                return localUsers.stream()
                        .filter(u -> u.getUsername().equals(username))
                        .findFirst();
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(),
                    "Error reading local users file: " + e.getMessage());
        }
        return Optional.empty();
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
            Path localUsersPath = dataAccess.getLocalUsersPath(); // New method needed in DataAccessService

            // Create a new list with just this user
            List<User> singleUserList = new ArrayList<>();
            singleUserList.add(user);

            // Save only this user's data locally
            dataAccess.writeFile(localUsersPath, singleUserList);

            LoggerUtil.info(this.getClass(),
                    String.format("Stored single user data locally for: %s", user.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error storing user data locally: %s", e.getMessage()));
            throw new RuntimeException("Failed to store user data locally", e);
        }
    }
}