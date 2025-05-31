package com.ctgraphdep.security;

import com.ctgraphdep.fileOperations.data.UserDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * REFACTORED CustomUserDetailsService using UserDataService for authentication.
 * Handles both online (network first) and offline (local only) authentication modes.
 * All password-related operations go through UserDataService for security.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserDataService userDataService;

    public CustomUserDetailsService(UserDataService userDataService) {
        this.userDataService = userDataService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Load user for online authentication (network first, local fallback).
     * This is the main Spring Security authentication method.
     * @param username Username to authenticate
     * @return UserDetails for authentication
     * @throws UsernameNotFoundException if user not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LoggerUtil.debug(this.getClass(), String.format("Loading user for online authentication: %s", username));

        try {
            // Use UserDataService to find user (network first, local fallback)
            Optional<User> userOptional = userDataService.findUserByUsernameForAuthentication(username);

            if (userOptional.isPresent()) {
                User user = userOptional.get();
                LoggerUtil.debug(this.getClass(), String.format("Successfully loaded user for authentication: %s (ID: %d, Role: %s)", user.getUsername(), user.getUserId(), user.getRole()));

                return new CustomUserDetails(user);
            } else {
                LoggerUtil.warn(this.getClass(), String.format("User not found for online authentication: %s", username));
                throw new UsernameNotFoundException("User not found: " + username);
            }

        } catch (UsernameNotFoundException e) {
            // Re-throw UsernameNotFoundException as-is
            throw e;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during online authentication for user %s: %s", username, e.getMessage()), e);
            throw new UsernameNotFoundException("Error accessing user data for authentication", e);
        }
    }

    /**
     * Load user for offline authentication (local storage only).
     * Used when network is unavailable and user wants to log in with cached credentials.
     *
     * @param username Username to authenticate
     * @return UserDetails for authentication
     * @throws UsernameNotFoundException if user not found in local storage
     */
    public UserDetails loadUserByUsernameOffline(String username) throws UsernameNotFoundException {
        LoggerUtil.debug(this.getClass(), String.format("Loading user for offline authentication: %s", username));

        try {
            // Get all local users from UserDataService
            List<User> localUsers = userDataService.getAllLocalUsersForAuthentication();

            if (localUsers.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "No users found in local storage for offline authentication");
                throw new UsernameNotFoundException("No local user data available for offline authentication");
            }

            // Find the specific user
            Optional<User> userOptional = localUsers.stream().filter(user -> username.equals(user.getUsername())).findFirst();

            if (userOptional.isPresent()) {
                User user = userOptional.get();
                LoggerUtil.info(this.getClass(), String.format("Successfully loaded user for offline authentication: %s (ID: %d, Role: %s)", user.getUsername(), user.getUserId(), user.getRole()));

                return new CustomUserDetails(user);
            } else {
                LoggerUtil.warn(this.getClass(), String.format("User not found in local storage for offline authentication: %s", username));
                throw new UsernameNotFoundException("User not found in local storage: " + username);
            }

        } catch (UsernameNotFoundException e) {
            // Re-throw UsernameNotFoundException as-is
            throw e;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during offline authentication for user %s: %s", username, e.getMessage()), e);
            throw new UsernameNotFoundException("Error accessing local user data for authentication", e);
        }
    }
}