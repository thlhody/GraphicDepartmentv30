package com.ctgraphdep.model.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

/**
 * Represents detailed statistics for a specific action type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionTypeStats {
    @JsonProperty("count")
    private int count;

    @JsonProperty("averageComplexity")
    private double averageComplexity;

    @JsonProperty("averageArticleNumbers")
    private double averageArticleNumbers;

    @JsonProperty("printPrepTypeDistribution")
    private Map<String, Integer> printPrepTypeDistribution;
}