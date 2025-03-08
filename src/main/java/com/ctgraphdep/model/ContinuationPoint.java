package com.ctgraphdep.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Model for tracking session continuation points.
 * Used for transferring continuation point data between services and controllers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContinuationPoint {
    private Long id;
    private String username;
    private Integer userId;
    private LocalDateTime timestamp;
    private LocalDate sessionDate;
    private String type;
    private boolean active;
    private boolean resolved;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private Integer overtimeApplied;

    /**
     * Gets a user-friendly description of the continuation point type
     */
    public String getTypeDescription() {
        return switch (type) {
            case "SCHEDULE_END" -> "Schedule End";
            case "HOURLY" -> "Hourly Warning";
            case "TEMP_STOP" -> "Temporary Stop";
            case "MIDNIGHT_END" -> "Midnight End";
            default -> type;
        };
    }

    /**
     * Gets a formatted timestamp for display
     */
    public String getFormattedTimestamp() {
        return timestamp != null ? timestamp.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) : "";
    }
}