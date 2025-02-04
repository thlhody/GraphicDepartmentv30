package com.ctgraphdep.security;

import com.ctgraphdep.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class FileAccessSecurityRules {

    /**
     * Checks if the current user has write permission for a specific user's data
     */
    public boolean canWriteUserData(String targetUsername) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return false;
        }

        // Get current username
        String currentUsername = auth.getName();

        // Users can only write their own data
        return currentUsername.equals(targetUsername);
    }

    /**
     * Checks if the current user has read permission for a specific user's data
     */
    public boolean canReadUserData(String targetUsername, User currentUser) {
        if (currentUser == null) {
            return false;
        }

        // Admin can read all data
        if (currentUser.isAdmin()) {
            return true;
        }

        // Team leaders can read data (but not modify)
        if (currentUser.hasRole("TEAM_LEADER")) {
            return true;
        }

        // Users can only read their own data
        return currentUser.getUsername().equals(targetUsername);
    }

    /**
     * Validates file access operation
     */
    public void validateFileAccess(String targetUsername, boolean isWriteOperation) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new SecurityException("No authenticated user found");
        }

        if (isWriteOperation && !canWriteUserData(targetUsername)) {
            throw new SecurityException("Write access denied for user: " + targetUsername);
        }
    }
}