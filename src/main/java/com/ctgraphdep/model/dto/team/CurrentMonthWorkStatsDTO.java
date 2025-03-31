package com.ctgraphdep.model.dto.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalTime;

/**
 * Represents work time statistics for the current month
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentMonthWorkStatsDTO {
    @JsonProperty("averageStartTime")
    private LocalTime averageStartTime;

    @JsonProperty("averageEndTime")
    private LocalTime averageEndTime;
}