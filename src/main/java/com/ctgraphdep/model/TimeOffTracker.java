package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Model for tracking user time off requests across all months for a specific year.
 */
@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeOffTracker {

    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("requests")
    @Builder.Default
    private List<TimeOffRequest> requests = new ArrayList<>();

    @JsonProperty("lastSyncTime")
    private LocalDateTime lastSyncTime;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("availableHolidayDays")
    private int availableHolidayDays;

    @JsonProperty("usedHolidayDays")
    private int usedHolidayDays;

    /**
     * Represents a single time off request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeOffRequest {
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
}