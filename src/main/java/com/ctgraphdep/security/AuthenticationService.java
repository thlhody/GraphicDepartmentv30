package com.ctgraphdep.security;

import com.ctgraphdep.model.AuthenticationStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AuthenticationService {

    private final DataAccessService dataAccessService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;

    public AuthenticationService(DataAccessService dataAccessService, UserService userService,
            PasswordEncoder passwordEncoder, CustomUserDetailsService userDetailsService) {
        this.dataAccessService = dataAccessService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public AuthenticationStatus getAuthenticationStatus() {
        try {
            // Use PathConfig for network status
            boolean networkAvailable = dataAccessService.isNetworkAvailable();

            // Check if there are any local user files
            boolean offlineModeAvailable = false;
            try {
                List<User> localUsers = dataAccessService.getAllUsers();
                offlineModeAvailable = localUsers != null && !localUsers.isEmpty();
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), "Error checking local users: " + e.getMessage());
            }

            String status = networkAvailable ? "ONLINE" : offlineModeAvailable ? "OFFLINE" : "UNAVAILABLE";

            LoggerUtil.info(this.getClass(), String.format("Authentication Status: Network=%b, Local=%b, Status=%s", networkAvailable, offlineModeAvailable, status));

            return new AuthenticationStatus(networkAvailable, offlineModeAvailable, status);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking authentication status: " + e.getMessage());
            return new AuthenticationStatus(false, false, "UNAVAILABLE");
        }
    }

    private Optional<User> getUserFromLocalStorage(String username) {
        try {
            // Get all users and find the one with matching username
            List<User> localUsers = dataAccessService.getAllUsers();
            if (localUsers != null) {
                return localUsers.stream()
                        .filter(u -> u.getUsername().equals(username))
                        .findFirst();
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error reading local users: " + e.getMessage());
        }
        return Optional.empty();
    }

    private void storeUserDataLocally(User user) {
        try {
            // Get the full user data
            Optional<User> fullUserData = userService.getCompleteUserByUsername(user.getUsername());

            if (fullUserData.isPresent()) {
                // Store the complete user object using the new method
                dataAccessService.writeUser(fullUserData.get());
                LoggerUtil.info(this.getClass(),
                        String.format("Stored complete user data locally for: %s", user.getUsername()));
            } else {
                LoggerUtil.error(this.getClass(),
                        String.format("Could not find complete user data for: %s", user.getUsername()));
                throw new RuntimeException("Failed to store complete user data locally");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error storing user data locally: %s", e.getMessage()));
            throw new RuntimeException("Failed to store user data locally", e);
        }
    }

    public UserDetails authenticateUser(String username, String password, boolean offlineMode) {
        // Get the full user object without sanitization
        UserDetails userDetails = offlineMode ? userDetailsService.loadUserByUsernameOffline(username) : userDetailsService.loadUserByUsername(username);

        // Add debug logs to track password handling
        LoggerUtil.debug(this.getClass(), String.format("Authenticating user: %s, Encoded password from details: %s", username, userDetails.getPassword()));

        LoggerUtil.debug(this.getClass(), String.format("Password matcher result: %b", passwordEncoder.matches(password, userDetails.getPassword())));

        if (passwordEncoder.matches(password, userDetails.getPassword())) {
            LoggerUtil.info(this.getClass(), String.format("Successfully authenticated user %s", username));
            return userDetails;
        }

        LoggerUtil.warn(this.getClass(), "Invalid credentials for user: " + username);
        throw new BadCredentialsException("Invalid credentials");
    }

    public void handleSuccessfulLogin(String username, boolean rememberMe) {
        try {
            Optional<User> userOptional = userService.getUserByUsername(username);

            if (userOptional.isEmpty()) {
                userOptional = getUserFromLocalStorage(username);
            }

            if (userOptional.isPresent()) {
                User user = userOptional.get();

                // Only create local directories and store user data if rememberMe is true
                if (rememberMe) {
                    try {
                        boolean dirsOk = dataAccessService.revalidateLocalDirectories(user.isAdmin());
                        if (!dirsOk) {
                            LoggerUtil.warn(this.getClass(),
                                    "Directory validation failed for user " + username +
                                            " - some operations may fail");
                        }
                        storeUserDataLocally(user);
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), "Failed to handle local storage: " + e.getMessage() + " - continuing with login");
                    }
                }

                LoggerUtil.info(this.getClass(), String.format("Successfully handled login for user: %s (rememberMe: %s)", username, rememberMe));
            } else {
                throw new UsernameNotFoundException("User not found after authentication");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error handling login for user %s: %s", username, e.getMessage()));
            throw new RuntimeException("Failed to handle login", e);
        }
    }
}