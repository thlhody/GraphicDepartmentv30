package com.ctgraphdep.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterSummaryDTO {

    private Integer totalEntries;

    private Double averageArticleNumbers;

    private Double averageGraphicComplexity;

    private Integer workedDays;

    // Additional helper methods if needed
    public boolean isValid() {
        return totalEntries != null &&
                averageArticleNumbers != null &&
                averageGraphicComplexity != null &&
                workedDays != null;
    }
}