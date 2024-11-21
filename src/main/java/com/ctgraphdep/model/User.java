package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("employeeId")
    private Integer employeeId;

    @JsonProperty("schedule")
    private Integer schedule;

    @JsonProperty("username")
    private String username;

    @JsonProperty("password")
    private String password;

    @JsonProperty("role")
    private String role;

    public boolean hasRole(String roleToCheck) {
        if (role == null) return false;

        // Handle both with and without ROLE_ prefix
        String normalizedRole = roleToCheck.startsWith("ROLE_") ?
                roleToCheck : "ROLE_" + roleToCheck;
        String userRole = role.startsWith("ROLE_") ?
                role : "ROLE_" + role;

        return userRole.equals(normalizedRole);
    }

    // Update isAdmin method to use consistent role checking
    public boolean isAdmin() {
        return hasRole("ADMIN"); // This will now check for both "ADMIN" and "ROLE_ADMIN"
    }
}