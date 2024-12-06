package com.ctgraphdep.model.statistics;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class UserRegisterStats {
    private int totalEntries;
    private double averageComplexity;
    private double averageArticles;
    private Map<String, Integer> actionTypeDistribution;
}
