package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a single time off request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeOffRequest {
    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("date")
    private LocalDate date;

    @JsonProperty("timeOffType")
    private String timeOffType;

    @JsonProperty("status")
    private String status;

    @JsonProperty("eligibleDays")
    private int eligibleDays;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("lastUpdated")
    private LocalDateTime lastUpdated;

    @JsonProperty("notes")
    private String notes;
}