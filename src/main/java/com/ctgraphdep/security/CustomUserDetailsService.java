package com.ctgraphdep.security;

import com.ctgraphdep.model.User;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final DataAccessService dataAccess;

    public CustomUserDetailsService(DataAccessService dataAccess) {
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        LoggerUtil.debug(this.getClass(),
                String.format("Loading user from network: %s", username));

        try {
            // Read from network users.json
            List<User> users = dataAccess.readUsersNetwork();

            return users.stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst()
                    .map(CustomUserDetails::new)
                    .orElseThrow(() -> {
                        LoggerUtil.warn(this.getClass(),
                                String.format("User not found in network storage: %s", username));
                        return new UsernameNotFoundException("User not found: " + username);
                    });

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error reading network users file: %s", e.getMessage()));
            throw new UsernameNotFoundException("Error accessing user data", e);
        }
    }

    public UserDetails loadUserByUsernameOffline(String username) {
        LoggerUtil.debug(this.getClass(),
                String.format("Loading user from local storage: %s", username));

        try {
            // Read from local local_users.json
            List<User> localUsers = dataAccess.readLocalUser();

            if (localUsers != null && !localUsers.isEmpty()) {
                return localUsers.stream()
                        .filter(u -> u.getUsername().equals(username))
                        .findFirst()
                        .map(CustomUserDetails::new)
                        .orElseThrow(() -> {
                            LoggerUtil.warn(this.getClass(),
                                    String.format("User not found in local storage: %s", username));
                            return new UsernameNotFoundException("User not found in local storage");
                        });
            }

            LoggerUtil.warn(this.getClass(), "No users found in local storage");
            throw new UsernameNotFoundException("No local user data available");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error reading local users file: %s", e.getMessage()));
            throw new UsernameNotFoundException("Error accessing local user data", e);
        }
    }
}
