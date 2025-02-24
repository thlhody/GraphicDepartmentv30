package com.ctgraphdep.model.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Represents a summary of the month's work
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthSummary {
    @JsonProperty("totalWorkDays")
    private int totalWorkDays;

    @JsonProperty("processedOrders")
    private int processedOrders;

    @JsonProperty("uniqueClients")
    private int uniqueClients;

    @JsonProperty("averageComplexity")
    private double averageComplexity;

    @JsonProperty("averageArticleNumbers")
    private double averageArticleNumbers;
}
