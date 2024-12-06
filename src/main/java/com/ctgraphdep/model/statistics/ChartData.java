package com.ctgraphdep.model.statistics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChartData {
    private List<String> labels;
    private List<Integer> data;
    private List<String> colors;
}