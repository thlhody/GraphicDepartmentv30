package com.ctgraphdep.model.dto.team;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents the current session details for a team member
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDetailsDTO {
    @JsonProperty("status")
    private String status;

    @JsonProperty("dayStartTime")
    private LocalDateTime dayStartTime;

    @JsonProperty("dayEndTime")
    private LocalDateTime dayEndTime;

}