package com.ctgraphdep.security;

import com.ctgraphdep.model.User;
import com.ctgraphdep.service.DataAccessService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private static final TypeReference<List<User>> USER_LIST_TYPE = new TypeReference<>() {};

    private final UserService userService;
    private final DataAccessService dataAccess;

    public CustomUserDetailsService(UserService userService, DataAccessService dataAccess) {
        this.userService = userService;
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        // First, try network path
        Path networkUsersPath = dataAccess.getUsersPath();
        LoggerUtil.debug(this.getClass(),
                String.format("Attempting to load user from network path: %s", networkUsersPath));

        try {
            // Check network path file
            if (Files.exists(networkUsersPath) && Files.size(networkUsersPath) > 3) {
                List<User> networkUsers = dataAccess.readFile(networkUsersPath, USER_LIST_TYPE, false);
                Optional<User> networkUser = networkUsers.stream()
                        .filter(u -> u.getUsername().equals(username))
                        .findFirst();

                if (networkUser.isPresent()) {
                    LoggerUtil.info(this.getClass(),
                            "Found user in network path: " + username);
                    return new CustomUserDetails(networkUser.get());
                }
            } else {
                LoggerUtil.warn(this.getClass(),
                        "Network users.json is missing or empty");
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(),
                    "Error reading network users file: " + e.getMessage());
        }

        // If network path fails, try local path
        Path localUsersPath = dataAccess.getLocalUsersPath();
        LoggerUtil.debug(this.getClass(),
                String.format("Attempting to load user from local path: %s", localUsersPath));

        try {
            // Check local path file
            if (Files.exists(localUsersPath) && Files.size(localUsersPath) > 3) {
                List<User> localUsers = dataAccess.readFile(localUsersPath, USER_LIST_TYPE, false);
                Optional<User> localUser = localUsers.stream()
                        .filter(u -> u.getUsername().equals(username))
                        .findFirst();

                if (localUser.isPresent()) {
                    LoggerUtil.info(this.getClass(),
                            "Found user in local path: " + username);
                    return new CustomUserDetails(localUser.get());
                }
            } else {
                LoggerUtil.warn(this.getClass(),
                        "Local local_users.json is missing or empty");
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(),
                    "Error reading local users file: " + e.getMessage());
        }

        // If no user found in either location
        LoggerUtil.error(this.getClass(),
                String.format("User not found in network or local storage: %s", username));
        throw new UsernameNotFoundException("User not found in any storage: " + username);
    }

    public UserDetails loadUserByUsernameOffline(String username) {
        // First try local storage
        LoggerUtil.debug(this.getClass(),
                String.format("Attempting offline authentication for %s", username));
        Path localUsersPath = dataAccess.getLocalUsersPath();
        if (dataAccess.fileExists(localUsersPath)) {
            try {
                List<User> localUsers = dataAccess.readFile(localUsersPath, USER_LIST_TYPE, false);
                Optional<User> userOpt = localUsers.stream()
                        .filter(u -> u.getUsername().equals(username))
                        .findFirst();

                if (userOpt.isPresent()) {
                    LoggerUtil.info(this.getClass(),
                            String.format("Loaded user from local storage: %s", username));
                    return new CustomUserDetails(userOpt.get());
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Error reading local user data: %s", e.getMessage()));
            }
        }

        // If not found in local storage, try network path as fallback
        Path usersPath = dataAccess.getUsersPath();
        if (!dataAccess.fileExists(usersPath)) {
            LoggerUtil.error(this.getClass(), "Offline user data file not found");
            throw new UsernameNotFoundException("Offline data not available");
        }

        try {
            List<User> users = dataAccess.readFile(usersPath, USER_LIST_TYPE, false);
            Optional<User> userOpt = users.stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();

            if (userOpt.isPresent()) {
                LoggerUtil.info(this.getClass(),
                        String.format("Loaded user from network storage: %s", username));
                return new CustomUserDetails(userOpt.get());
            }

            LoggerUtil.warn(this.getClass(),
                    String.format("User not found in any storage: %s", username));
            throw new UsernameNotFoundException("User not found in any storage");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error reading user data: %s", e.getMessage()));
            throw new UsernameNotFoundException("Error accessing user data", e);
        }
    }
}