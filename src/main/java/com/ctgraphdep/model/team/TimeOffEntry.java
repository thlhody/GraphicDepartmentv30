package com.ctgraphdep.model.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Represents a Time Off entry for different types of leave
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeOffEntry {
    @JsonProperty("timeOffType")
    private String timeOffType; // CO, CM, SN etc.

    @JsonProperty("days")
    private List<LocalDate> days;
}