package com.ctgraphdep.model.dto.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

/**
 * Represents detailed statistics for a team member's register entries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberRegisterStatsDTO {
    @JsonProperty("monthSummaryDTO")
    private MonthSummaryDTO monthSummaryDTO;

    @JsonProperty("clientSpecificStats")
    private Map<String, ClientDetailedStatsDTO> clientSpecificStats;
}