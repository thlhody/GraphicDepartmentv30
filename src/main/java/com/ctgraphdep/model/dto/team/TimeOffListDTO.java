package com.ctgraphdep.model.dto.team;

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
public class TimeOffListDTO {
    @JsonProperty("timeOffCO")
    private List<TimeOffEntryDTO> timeOffCO;

    @JsonProperty("timeOffCM")
    private List<TimeOffEntryDTO> timeOffCM;

    @JsonProperty("timeOffSN")
    private List<TimeOffEntryDTO> timeOffSN;
}
