package com.ctgraphdep.model.dto.statistics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChartDataDTO {
    private List<String> labels;
    private List<Integer> data;
    private List<String> colors;
}