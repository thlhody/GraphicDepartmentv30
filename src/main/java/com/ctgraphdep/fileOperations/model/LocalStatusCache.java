package com.ctgraphdep.fileOperations.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Status of a local cache.
 * Used to track the status of files that are cached locally.
 */
@Data
public class LocalStatusCache {
    private LocalDateTime lastNetworkSync;
    private List<UserStatus> userStatuses = new ArrayList<>();

    /**
     * Status of a user in the local cache
     */
    @Data
    public static class UserStatus {
        private String username;
        private String status;
        private LocalDateTime timestamp;
    }

    /**
     * Add or update a user status in the cache
     * @param username The username
     * @param status The status to set
     * @param timestamp The timestamp of the status update
     */
    public void updateUserStatus(String username, String status, LocalDateTime timestamp) {
        // Find existing status or create new one
        UserStatus userStatus = userStatuses.stream()
                .filter(s -> s.getUsername().equals(username))
                .findFirst()
                .orElse(new UserStatus());

        userStatus.setUsername(username);
        userStatus.setStatus(status);
        userStatus.setTimestamp(timestamp);

        // Add to list if new
        if (!userStatuses.contains(userStatus)) {
            userStatuses.add(userStatus);
        }
    }

    /**
     * Get a user's status from the cache
     * @param username The username
     * @return The user's status, or null if not found
     */
    public String getUserStatus(String username) {
        return userStatuses.stream()
                .filter(s -> s.getUsername().equals(username))
                .findFirst()
                .map(UserStatus::getStatus)
                .orElse(null);
    }
}
