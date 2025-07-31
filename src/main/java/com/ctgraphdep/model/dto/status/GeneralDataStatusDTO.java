package com.ctgraphdep.model.dto.status;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Universal Status DTO for displaying status information across all services.
 * Provides a consistent, human-readable representation of Universal Merge statuses.
 * This DTO decodes the raw status string into meaningful information for frontend display:
 * - Role information (Admin, User, TL_Checking)
 * - Action type (Input, Edited, Final, Active)
 * - Timing information (when was it edited)
 * - Boolean flags for easy frontend logic
 * Used by: WorkTime, CheckRegister, and other entities with Universal Merge status
 */
@Data
@Builder
public class GeneralDataStatusDTO {

    // ========================================================================
    // RAW STATUS DATA
    // ========================================================================

    /** Original raw status string (e.g., "USER_EDITED_1641234567890") */
    private String rawStatus;

    /** Whether this entry should be displayed (false for DELETE status) */
    private boolean isDisplayable;

    // ========================================================================
    // ROLE INFORMATION
    // ========================================================================

    /** Who performed the action: "Admin", "User", "TL_Checking" */
    private String roleName;

    /** Role type for programmatic use: "ADMIN", "USER", "TEAM" */
    private String roleType;

    // ========================================================================
    // ACTION INFORMATION
    // ========================================================================

    /** What action was performed: "Input", "Edited", "Final", "Active" */
    private String actionType;

    /** Full human-readable description: "User Input", "Admin Edited", "TL_Checking Final" */
    private String fullDescription;

    // ========================================================================
    // TIMING INFORMATION (for EDITED statuses)
    // ========================================================================

    /** When was this entry last edited (null if not an edit) */
    private LocalDateTime editedDateTime;

    /** Formatted edit time for display: "Jan 15, 2025 14:30" */
    private String editedTimeDisplay;

    /** How long ago was it edited: "2 hours ago", "3 days ago" */
    private String editedTimeAgo;

    // ========================================================================
    // BOOLEAN FLAGS (for easy frontend logic)
    // ========================================================================

    /** True if status is ADMIN_FINAL or TEAM_FINAL */
    private boolean isFinal;

    /** True if status is *_INPUT */
    private boolean isInput;

    /** True if status is USER_IN_PROCESS */
    private boolean isUserInProcess;

    /** True if status is *_EDITED_* */
    private boolean isEdited;

    /** True if entry can be modified (not final) */
    private boolean isModifiable;

    /** True if current user owns this entry (for permissions) */
    private boolean isOwnedByCurrentUser;

    // ========================================================================
    // DISPLAY INFORMATION
    // ========================================================================

    /** CSS class for styling: "text-success", "text-warning", etc. */
    private String cssClass;

    /** Icon class for display: "bi-check-circle", "bi-pencil", etc. */
    private String iconClass;

    /** Bootstrap badge class: "bg-success", "bg-warning", etc. */
    private String badgeClass;

    /** Tooltip text with full information */
    private String tooltipText;

    /** Priority level for sorting (4=Final, 3=Edited, 2=Active, 1=Input) */
    private int priorityLevel;

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Create DTO for non-displayable status (DELETE)
     */
    public static GeneralDataStatusDTO createNonDisplayable(String rawStatus) {
        return GeneralDataStatusDTO.builder()
                .rawStatus(rawStatus)
                .isDisplayable(false)
                .roleName("System")
                .roleType("SYSTEM")
                .actionType("Delete")
                .fullDescription("Marked for deletion")
                .isFinal(false)
                .isInput(false)
                .isUserInProcess(false)
                .isEdited(false)
                .isModifiable(false)
                .isOwnedByCurrentUser(false)
                .cssClass("text-muted")
                .iconClass("bi-trash")
                .badgeClass("bg-secondary")
                .tooltipText("Entry marked for deletion")
                .priorityLevel(0)
                .build();
    }

    /**
     * Create DTO for unknown/null status
     */
    public static GeneralDataStatusDTO createUnknown() {
        return GeneralDataStatusDTO.builder()
                .rawStatus("UNKNOWN")
                .isDisplayable(true)
                .roleName("Unknown")
                .roleType("UNKNOWN")
                .actionType("Unknown")
                .fullDescription("Unknown status")
                .isFinal(false)
                .isInput(false)
                .isUserInProcess(false)
                .isEdited(false)
                .isModifiable(true)
                .isOwnedByCurrentUser(false)
                .cssClass("text-muted")
                .iconClass("bi-question-circle")
                .badgeClass("bg-secondary")
                .tooltipText("Status information unavailable")
                .priorityLevel(0)
                .build();
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Get short display text for compact views
     */
    public String getShortDisplay() {
        if (!isDisplayable) return "";

        if (isUserInProcess) return "Active";
        if (isFinal) return roleName.charAt(0) + "F"; // "AF", "TF"
        if (isEdited) return roleName.charAt(0) + "E"; // "AE", "UE", "TE"
        if (isInput) return roleName.charAt(0) + "I"; // "AI", "UI", "TI"

        return "?";
    }

    /**
     * Get medium display text for normal views
     */
    public String getMediumDisplay() {
        if (!isDisplayable) return "";

        if (isUserInProcess) return "Active";
        if (isFinal) return roleName + " Final";
        if (isEdited) return roleName + " Edit";
        if (isInput) return roleName + " Input";

        return fullDescription;
    }

    /**
     * Get full display text with timing for detailed views
     */
    public String getFullDisplay() {
        if (!isDisplayable) return "";

        String base = fullDescription;

        if (isEdited && editedTimeAgo != null) {
            base += " (" + editedTimeAgo + ")";
        }

        return base;
    }

    /**
     * Check if this status has higher priority than another
     */
    public boolean hasHigherPriorityThan(GeneralDataStatusDTO other) {
        if (other == null) return true;
        return this.priorityLevel > other.priorityLevel;
    }

    /**
     * Check if this status indicates the entry is locked for editing
     */
    public boolean isLocked() {
        return isFinal || (!isOwnedByCurrentUser && isUserInProcess);
    }
}