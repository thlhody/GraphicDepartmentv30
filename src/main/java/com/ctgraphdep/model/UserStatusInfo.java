package com.ctgraphdep.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class UserStatusInfo {
    private String username;
    private Integer userId;
    private String name;
    private String status;
    private LocalDateTime lastActive;
    private String role;
    // Helper method to check role (similar to User and UserStatusDTO classes)
    public boolean hasRole(String roleToCheck) {
        if (role == null) return false;

        // Strip ROLE_ prefix from both sides for consistent comparison
        String normalizedRoleToCheck = roleToCheck.replace("ROLE_", "");
        String normalizedUserRole = role.replace("ROLE_", "");

        return normalizedUserRole.equals(normalizedRoleToCheck);
    }
}