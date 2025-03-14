package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.AuthenticationStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.security.CustomUserDetailsService;
import com.ctgraphdep.utils.LoggerUtil;
import groovy.util.logging.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AuthenticationService {

    private final DataAccessService dataAccess;
    private final PathConfig pathConfig;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;

    public AuthenticationService(
            DataAccessService dataAccess,
            PathConfig pathConfig, UserService userService,
            PasswordEncoder passwordEncoder,
            CustomUserDetailsService userDetailsService) {
        this.dataAccess = dataAccess;
        this.pathConfig = pathConfig;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public AuthenticationStatus getAuthenticationStatus() {
        try {
            // Use PathConfig for network status
            boolean networkAvailable = pathConfig.isNetworkAvailable();

            // Use DataAccessService for checking local files
            List<User> localUsers = dataAccess.readLocalUsers();
            boolean offlineModeAvailable = localUsers != null && !localUsers.isEmpty();

            String status = networkAvailable ? "ONLINE" :
                    offlineModeAvailable ? "OFFLINE" : "UNAVAILABLE";

            LoggerUtil.info(this.getClass(),
                    String.format("Authentication Status: Network=%b, Local=%b, Status=%s",
                            networkAvailable, offlineModeAvailable, status));

            return new AuthenticationStatus(networkAvailable, offlineModeAvailable, status);
        } catch (Exception e) {
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
            Optional<User> userOptional = userService.getUserByUsername(username);

            if (userOptional.isEmpty()) {
                userOptional = getUserFromLocalStorage(username);
            }

            if (userOptional.isPresent()) {
                User user = userOptional.get();

                // Only create local directories and store user data if rememberMe is true
                if (rememberMe) {
                    try {
                        ensureLocalDirectories(user.isAdmin());
                        storeUserDataLocally(user);
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(),
                                "Failed to handle local storage: " + e.getMessage() +
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

    private Optional<User> getUserFromLocalStorage(String username) {
        try {
            List<User> localUsers = dataAccess.readLocalUsers();
            if (localUsers != null) {
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
            // PathConfig already handles base directory creation in its init()
            // We don't need to manually create directories anymore
            // Just verify they exist
            verifyUserDirectories();
            if (isAdmin) {
                verifyAdminDirectories();
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error verifying local directories: " + e.getMessage());
            throw new RuntimeException("Failed to verify local directories", e);
        }
    }

    private void verifyUserDirectories() throws IOException {
        if (!Files.exists(pathConfig.getLocalSessionPath("test", 0).getParent()) ||
                !Files.exists(pathConfig.getLocalWorktimePath("test", 0, 0).getParent()) ||
                !Files.exists(pathConfig.getLocalRegisterPath("test", 0, 0, 0).getParent())) {
            throw new IOException("Required user directories not found");
        }
    }

    private void verifyAdminDirectories() throws IOException {
        if (!Files.exists(pathConfig.getLocalAdminWorktimePath(0, 0).getParent()) ||
                !Files.exists(pathConfig.getLocalAdminRegisterPath("test", 0, 0, 0).getParent()) ||
                !Files.exists(pathConfig.getLocalBonusPath(0, 0).getParent())) {
            throw new IOException("Required admin directories not found");
        }
    }

    private void storeUserDataLocally(User user) {
        try {
            // Get the full, user data
            Optional<User> fullUserData = userService.getCompleteUserByUsername(user.getUsername());

            if (fullUserData.isPresent()) {
                // Store the complete user object including password
                dataAccess.writeLocalUsers(fullUserData.get());
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
}