package com.ctgraphdep.model.team;

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
public class CurrentMonthWorkStats {
    @JsonProperty("averageStartTime")
    private LocalTime averageStartTime;

    @JsonProperty("averageEndTime")
    private LocalTime averageEndTime;
}