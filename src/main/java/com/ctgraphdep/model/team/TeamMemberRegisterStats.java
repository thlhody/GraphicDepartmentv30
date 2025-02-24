package com.ctgraphdep.model.team;

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
public class TeamMemberRegisterStats {
    @JsonProperty("monthSummary")
    private MonthSummary monthSummary;

    @JsonProperty("clientSpecificStats")
    private Map<String, ClientDetailedStats> clientSpecificStats;
}