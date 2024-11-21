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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private static final TypeReference<List<User>> USER_LIST_TYPE = new TypeReference<>() {};

    private final UserService userService;
    private final DataAccessService dataAccess;

    public CustomUserDetailsService(
            UserService userService,
            DataAccessService dataAccess) {
        this.userService = userService;
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), "Initializing Custom User Details Service");
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userService.getUserByUsername(username)
                .orElseThrow(() -> {
                    LoggerUtil.warn(this.getClass(),
                            String.format("User not found in online mode: %s", username));
                    return new UsernameNotFoundException("User not found: " + username);
                });

        LoggerUtil.info(this.getClass(),
                String.format("Loaded user details for: %s", username));

        return new CustomUserDetails(user);
    }

    public UserDetails loadUserByUsernameOffline(String username) {
        Path usersPath = dataAccess.getUsersPath();

        if (!dataAccess.fileExists(usersPath)) {
            LoggerUtil.error(this.getClass(),
                    "Offline user data file not found");
            throw new UsernameNotFoundException("Offline data not available");
        }

        try {
            List<User> users = dataAccess.readFile(usersPath, USER_LIST_TYPE, false);

            Optional<User> userOpt = users.stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();

            if (userOpt.isPresent()) {
                LoggerUtil.info(this.getClass(),
                        String.format("Loaded user from offline storage: %s", username));
                return new CustomUserDetails(userOpt.get());
            } else {
                LoggerUtil.warn(this.getClass(),
                        String.format("User not found in offline storage: %s", username));
                throw new UsernameNotFoundException(
                        String.format("User not found in offline storage: %s", username));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error reading offline user data: %s", e.getMessage()));
            throw new UsernameNotFoundException("Error accessing offline data", e);
        }
    }

    /**
     * Verify if offline mode is available for a specific user
     */
    public boolean isOfflineModeAvailable(String username) {
        try {
            Path usersPath = dataAccess.getUsersPath();
            if (!dataAccess.fileExists(usersPath)) {
                return false;
            }

            List<User> users = dataAccess.readFile(usersPath, USER_LIST_TYPE, false);
            return users.stream()
                    .anyMatch(u -> u.getUsername().equals(username));

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(),
                    String.format("Error checking offline availability for user %s: %s",
                            username, e.getMessage()));
            return false;
        }
    }
}