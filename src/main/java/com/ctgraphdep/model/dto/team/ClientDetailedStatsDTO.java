package com.ctgraphdep.model.dto.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

/**
 * Represents detailed statistics for a specific client
 * Enhanced version of ClientDetailedStatsDTO with more detailed action type metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDetailedStatsDTO {
    @JsonProperty("totalOrders")
    private int totalOrders;

    @JsonProperty("averageComplexity")
    private double averageComplexity;

    @JsonProperty("averageArticleNumbers")
    private double averageArticleNumbers;

    @JsonProperty("actionTypeStats")
    private Map<String, ActionTypeStatsDTO> actionTypeStats;

    @JsonProperty("printPrepTypeDistribution")
    private Map<String, Integer> printPrepTypeDistribution;
}
