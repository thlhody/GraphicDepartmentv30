package com.ctgraphdep.security;

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