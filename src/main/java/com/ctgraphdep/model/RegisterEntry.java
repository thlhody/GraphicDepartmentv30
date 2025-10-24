package com.ctgraphdep.model;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RegisterEntry {
    @JsonProperty("entryId")
    private Integer entryId;

    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("date")
    private LocalDate date;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("productionId")
    private String productionId;

    @JsonProperty("omsId")
    private String omsId;

    @JsonProperty("clientName")
    private String clientName;

    @JsonProperty("actionType")
    private String actionType;

    @JsonProperty("printPrepTypes")
    @Builder.Default
    private List<String> printPrepTypes = new ArrayList<>();

    @JsonProperty("colorsProfile")
    private String colorsProfile;

    @JsonProperty("articleNumbers")
    private Integer articleNumbers;

    @JsonProperty("graphicComplexity")
    private Double graphicComplexity;

    @JsonProperty("observations")
    private String observations;

    @JsonProperty("adminSync")
    private String adminSync;

    public List<String> getPrintPrepTypes() {
        // Return a new ArrayList to avoid modification of the internal list
        // Also ensure we don't have duplicates by using a LinkedHashSet
        return new ArrayList<>(new LinkedHashSet<>(
                printPrepTypes
        ));
    }

    /**
     * Get display-friendly status text for UI
     * Converts technical status codes to user-friendly labels
     * Note: @JsonIgnore prevents this from being serialized to JSON files
     */
    @SuppressWarnings("unused")
    @JsonIgnore
    public String getStatusDisplay() {
        if (adminSync == null) {
            return "Unknown";
        }

        // Check for timestamped statuses first
        if (MergingStatusConstants.isUserEditedStatus(adminSync)) {
            return "User Edited";
        }
        if (MergingStatusConstants.isAdminEditedStatus(adminSync)) {
            return "Admin Edited";
        }
        if (MergingStatusConstants.isTeamEditedStatus(adminSync)) {
            return "Team Edited";
        }

        // Check for deletion statuses
        if (MergingStatusConstants.isDeletedStatus(adminSync)) {
            return "Deleted";
        }

        // Base statuses
        return switch (adminSync) {
            case MergingStatusConstants.USER_INPUT -> "In Process";
            case MergingStatusConstants.ADMIN_INPUT -> "Admin Created";
            case MergingStatusConstants.TEAM_INPUT -> "Team Created";
            case MergingStatusConstants.USER_IN_PROCESS -> "Working";
            case MergingStatusConstants.ADMIN_FINAL -> "Admin Final";
            case MergingStatusConstants.TEAM_FINAL -> "Team Final";
            default -> adminSync; // Fallback to raw status
        };
    }

    /**
     * Get Bootstrap badge CSS class for status
     * Note: @JsonIgnore prevents this from being serialized to JSON files
     */
    @SuppressWarnings("unused")
    @JsonIgnore
    public String getStatusBadgeClass() {
        if (adminSync == null) {
            return "bg-secondary";
        }

        // Check for timestamped statuses first
        if (MergingStatusConstants.isAdminEditedStatus(adminSync)) {
            return "bg-primary"; // Blue for admin
        }
        if (MergingStatusConstants.isTeamEditedStatus(adminSync)) {
            return "bg-info"; // Cyan for team
        }
        if (MergingStatusConstants.isUserEditedStatus(adminSync)) {
            return "bg-warning"; // Yellow for user edited
        }

        // Check for deletion statuses
        if (MergingStatusConstants.isDeletedStatus(adminSync)) {
            return "bg-danger";
        }

        // Base statuses
        return switch (adminSync) {
            case MergingStatusConstants.USER_INPUT -> "bg-success"; // Green for in process
            case MergingStatusConstants.ADMIN_INPUT, MergingStatusConstants.ADMIN_FINAL -> "bg-primary"; // Blue for admin
            case MergingStatusConstants.TEAM_INPUT, MergingStatusConstants.TEAM_FINAL -> "bg-info"; // Cyan for team
            case MergingStatusConstants.USER_IN_PROCESS -> "bg-warning"; // Yellow for working
            default -> "bg-secondary"; // Gray for unknown
        };
    }
}