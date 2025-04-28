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

    @JsonProperty("paidHolidayDays")
    private Integer paidHolidayDays;

    @JsonProperty("username")
    private String username;

    @JsonProperty("password")
    private String password;

    @JsonProperty("role")
    private String role;

    public boolean hasRole(String roleToCheck) {
        if (role == null) return false;
        // Strip ROLE_ prefix from both sides for consistent comparison
        String normalizedRoleToCheck = roleToCheck.replace("ROLE_", "");
        String normalizedUserRole = role.replace("ROLE_", "");
        return normalizedUserRole.equals(normalizedRoleToCheck);
    }

    // Update isAdmin method to use consistent role checking
    public boolean isAdmin() {
        return hasRole("ADMIN"); // This will now check for both "ADMIN" and "ROLE_ADMIN"
    }
}