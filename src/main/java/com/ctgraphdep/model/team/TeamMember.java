package com.ctgraphdep.model.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Represents a detailed Team Member model with comprehensive information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMember {
    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("username")
    private String username;

    // Added for display
    @JsonProperty("name")
    private String name;

    @JsonProperty("employeeId")
    private Integer employeeId;

    @JsonProperty("schedule")
    private Integer schedule;

    @JsonProperty("role")
    private String role;

    @JsonProperty("currentMonthWorkStats")
    private CurrentMonthWorkStats currentMonthWorkStats;

    @JsonProperty("timeOffList")
    private TimeOffList timeOffList;

    @JsonProperty("sessionDetails")
    private SessionDetails sessionDetails;

    @JsonProperty("registerStats")
    private TeamMemberRegisterStats registerStats;
}