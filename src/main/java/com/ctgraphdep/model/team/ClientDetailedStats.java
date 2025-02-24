package com.ctgraphdep.model.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

/**
 * Represents detailed statistics for a specific client
 * Enhanced version of ClientDetailedStats with more detailed action type metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDetailedStats {
    @JsonProperty("totalOrders")
    private int totalOrders;

    @JsonProperty("averageComplexity")
    private double averageComplexity;

    @JsonProperty("averageArticleNumbers")
    private double averageArticleNumbers;

    @JsonProperty("actionTypeStats")
    private Map<String, ActionTypeStats> actionTypeStats;

    @JsonProperty("printPrepTypeDistribution")
    private Map<String, Integer> printPrepTypeDistribution;
}
