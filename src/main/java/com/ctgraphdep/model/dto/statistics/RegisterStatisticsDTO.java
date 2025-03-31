package com.ctgraphdep.model.dto.statistics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterStatisticsDTO {
    private ChartDataDTO clientDistribution;
    private ChartDataDTO actionTypeDistribution;
    private ChartDataDTO printPrepTypeDistribution;
    private int totalEntries;
    private double averageArticles;
    private double averageComplexity;
}