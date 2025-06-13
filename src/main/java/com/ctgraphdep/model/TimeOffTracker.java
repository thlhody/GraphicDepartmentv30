package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

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


}