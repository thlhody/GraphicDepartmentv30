package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterSummary {
    @JsonProperty("totalEntries")
    private Integer totalEntries;

    @JsonProperty("averageArticleNumbers")
    private Double averageArticleNumbers;

    @JsonProperty("averageGraphicComplexity")
    private Double averageGraphicComplexity;

    @JsonProperty("workedDays")
    private Integer workedDays;

    // Additional helper methods if needed
    public boolean isValid() {
        return totalEntries != null &&
                averageArticleNumbers != null &&
                averageGraphicComplexity != null &&
                workedDays != null;
    }
}