package com.ctgraphdep.model.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Represents a collection of Time Off entries for different leave types
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeOffList {
    @JsonProperty("timeOffCO")
    private List<TimeOffEntry> timeOffCO;

    @JsonProperty("timeOffCM")
    private List<TimeOffEntry> timeOffCM;

    @JsonProperty("timeOffSN")
    private List<TimeOffEntry> timeOffSN;
}
