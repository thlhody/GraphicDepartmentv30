package com.ctgraphdep.model.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class UserStatusDTO {
    private String username;
    private Integer userId;
    private String name;
    private String status;
    private String lastActive;
    private String role; // Added role field

    // Helper method to check role (similar to User class)
    public boolean hasRole(String roleToCheck) {
        if (role == null) return false;

        // Strip ROLE_ prefix from both sides for consistent comparison
        String normalizedRoleToCheck = roleToCheck.replace("ROLE_", "");
        String normalizedUserRole = role.replace("ROLE_", "");

        return normalizedUserRole.equals(normalizedRoleToCheck);
    }
}