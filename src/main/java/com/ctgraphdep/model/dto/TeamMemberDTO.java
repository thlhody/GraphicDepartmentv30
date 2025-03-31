package com.ctgraphdep.model.dto;

import com.ctgraphdep.model.dto.team.CurrentMonthWorkStatsDTO;
import com.ctgraphdep.model.dto.team.SessionDetailsDTO;
import com.ctgraphdep.model.dto.team.TeamMemberRegisterStatsDTO;
import com.ctgraphdep.model.dto.team.TimeOffListDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Represents a detailed Team Member model with comprehensive information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberDTO {
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

    @JsonProperty("currentMonthWorkStatsDTO")
    private CurrentMonthWorkStatsDTO currentMonthWorkStatsDTO;

    @JsonProperty("timeOffListDTO")
    private TimeOffListDTO timeOffListDTO;

    @JsonProperty("sessionDetailsDTO")
    private SessionDetailsDTO sessionDetailsDTO;

    @JsonProperty("registerStats")
    private TeamMemberRegisterStatsDTO registerStats;
}