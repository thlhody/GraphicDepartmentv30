package com.ctgraphdep.model.statistics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterStatistics {
    private ChartData clientDistribution;
    private ChartData actionTypeDistribution;
    private ChartData printPrepTypeDistribution;
    private int totalEntries;
    private double averageArticles;
    private double averageComplexity;
}