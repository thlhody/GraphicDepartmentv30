package com.ctgraphdep.model.db;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Model class representing a user's session status stored in an individual JSON file.
 * Each user has their own status file to eliminate concurrent write issues.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatusRecord {
    private String username;
    private Integer userId;
    private String name;
    private String status;
    private LocalDateTime lastActive;
    private LocalDateTime lastUpdated;
}