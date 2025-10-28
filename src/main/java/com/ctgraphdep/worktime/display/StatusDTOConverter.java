package com.ctgraphdep.worktime.display;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.dto.status.GeneralDataStatusDTO;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Service to convert raw Universal Merge status strings into GeneralDataStatusDTO objects.
 * Handles all the complex logic of parsing statuses, extracting timestamps, and determining display information.
 */
@Service
public class StatusDTOConverter {

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    /**
     * Convert raw status string to comprehensive DTO
     *
     * @param rawStatus The raw status string from entity
     * @param currentUserId Current user's ID for ownership determination
     * @param entryUserId The user ID who owns the entry
     * @return Comprehensive status DTO
     */
    public GeneralDataStatusDTO convertToDTO(String rawStatus, Integer currentUserId, Integer entryUserId) {
        try {
            //LoggerUtil.debug(this.getClass(), String.format("Converting status: %s", rawStatus));

            // Handle null or empty status
            if (rawStatus == null || rawStatus.trim().isEmpty()) {
                return GeneralDataStatusDTO.createUnknown();
            }

            // Handle DELETED statuses (tombstones - not displayable)
            if (MergingStatusConstants.isUserDeletedStatus(rawStatus) ||
                MergingStatusConstants.isAdminDeletedStatus(rawStatus) ||
                MergingStatusConstants.isTeamDeletedStatus(rawStatus)) {
                return GeneralDataStatusDTO.createNonDisplayable(rawStatus);
            }

            // Determine ownership
            boolean isOwnedByCurrentUser = currentUserId != null && currentUserId.equals(entryUserId);

            // Parse the status
            StatusInfo statusInfo = parseStatus(rawStatus);

            // Build the DTO
            return GeneralDataStatusDTO.builder()
                    .rawStatus(rawStatus)
                    .isDisplayable(true)
                    .roleName(statusInfo.roleName)
                    .roleType(statusInfo.roleType)
                    .actionType(statusInfo.actionType)
                    .fullDescription(statusInfo.fullDescription)
                    .editedDateTime(statusInfo.editedDateTime)
                    .editedTimeDisplay(statusInfo.editedTimeDisplay)
                    .editedTimeAgo(statusInfo.editedTimeAgo)
                    .isFinal(statusInfo.isFinal)
                    .isInput(statusInfo.isInput)
                    .isUserInProcess(statusInfo.isUserInProcess)
                    .isEdited(statusInfo.isEdited)
                    .isModifiable(statusInfo.isModifiable)
                    .isOwnedByCurrentUser(isOwnedByCurrentUser)
                    .cssClass(statusInfo.cssClass)
                    .iconClass(statusInfo.iconClass)
                    .badgeClass(statusInfo.badgeClass)
                    .tooltipText(buildTooltipText(statusInfo))
                    .priorityLevel(statusInfo.priorityLevel)
                    .build();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error converting status %s: %s", rawStatus, e.getMessage()), e);
            return GeneralDataStatusDTO.createUnknown();
        }
    }

    /**
     * Parse raw status into structured information
     */
    private StatusInfo parseStatus(String rawStatus) {
        StatusInfo info = new StatusInfo();
        info.rawStatus = rawStatus;

        // Handle timestamped edit statuses first
        if (MergingStatusConstants.isTimestampedEditStatus(rawStatus)) {
            parseEditedStatus(rawStatus, info);
            return info;
        }

        // Handle base statuses
        switch (rawStatus) {
            case MergingStatusConstants.USER_INPUT:
                info.roleName = "User";
                info.roleType = "USER";
                info.actionType = "Input";
                info.fullDescription = "User Input";
                info.isInput = true;
                info.isModifiable = true;
                info.cssClass = "text-success";
                info.iconClass = "bi-check-circle";
                info.badgeClass = "bg-success";
                info.priorityLevel = 1;
                break;

            case MergingStatusConstants.ADMIN_INPUT:
                info.roleName = "Admin";
                info.roleType = "ADMIN";
                info.actionType = "Input";
                info.fullDescription = "Admin Input";
                info.isInput = true;
                info.isModifiable = true;
                info.cssClass = "text-warning";
                info.iconClass = "bi-shield-check";
                info.badgeClass = "bg-warning";
                info.priorityLevel = 1;
                break;

            case MergingStatusConstants.TEAM_INPUT:
                info.roleName = "TL_Checking";
                info.roleType = "TEAM_LEADER";
                info.actionType = "Input";
                info.fullDescription = "TL_Checking Input";
                info.isInput = true;
                info.isModifiable = true;
                info.cssClass = "text-info";
                info.iconClass = "bi-people-fill";
                info.badgeClass = "bg-info";
                info.priorityLevel = 1;
                break;

            case MergingStatusConstants.USER_IN_PROCESS:
                info.roleName = "User";
                info.roleType = "USER";
                info.actionType = "Active";
                info.fullDescription = "User Active";
                info.isUserInProcess = true;
                info.isModifiable = false; // Protected while in process
                info.cssClass = "text-primary";
                info.iconClass = "bi-clock-history";
                info.badgeClass = "bg-primary";
                info.priorityLevel = 2;
                break;

            case MergingStatusConstants.ADMIN_FINAL:
                info.roleName = "Admin";
                info.roleType = "ADMIN";
                info.actionType = "Final";
                info.fullDescription = "Admin Final";
                info.isFinal = true;
                info.isModifiable = false;
                info.cssClass = "text-danger";
                info.iconClass = "bi-lock-fill";
                info.badgeClass = "bg-danger";
                info.priorityLevel = 4;
                break;

            case MergingStatusConstants.TEAM_FINAL:
                info.roleName = "TL_Checking";
                info.roleType = "TEAM";
                info.actionType = "Final";
                info.fullDescription = "TL_Checking Final";
                info.isFinal = true;
                info.isModifiable = false;
                info.cssClass = "text-dark";
                info.iconClass = "bi-lock";
                info.badgeClass = "bg-dark";
                info.priorityLevel = 4;
                break;

            default:
                // Unknown status
                info.roleName = "Unknown";
                info.roleType = "UNKNOWN";
                info.actionType = "Unknown";
                info.fullDescription = "Unknown Status";
                info.isModifiable = true;
                info.cssClass = "text-muted";
                info.iconClass = "bi-question-circle";
                info.badgeClass = "bg-secondary";
                info.priorityLevel = 0;
                break;
        }

        return info;
    }

    /**
     * Parse timestamped edit status (e.g., "USER_EDITED_1641234567890")
     */
    private void parseEditedStatus(String rawStatus, StatusInfo info) {
        // Determine role from prefix
        if (MergingStatusConstants.isUserEditedStatus(rawStatus)) {
            info.roleName = "User";
            info.roleType = "USER";
            info.cssClass = "text-primary";
            info.badgeClass = "bg-primary";
        } else if (MergingStatusConstants.isAdminEditedStatus(rawStatus)) {
            info.roleName = "Admin";
            info.roleType = "ADMIN";
            info.cssClass = "text-warning";
            info.badgeClass = "bg-warning";
        } else if (MergingStatusConstants.isTeamEditedStatus(rawStatus)) {
            info.roleName = "TL_Checking";
            info.roleType = "TEAM";
            info.cssClass = "text-info";
            info.badgeClass = "bg-info";
        }

        // Set action and flags
        info.actionType = "Edited";
        info.fullDescription = info.roleName + " Edited";
        info.isEdited = true;
        info.isModifiable = true;
        info.iconClass = "bi-pencil-fill";
        info.priorityLevel = 3;

        // Extract and format timestamp
        long minutesSinceEpoch = MergingStatusConstants.extractTimestamp(rawStatus);
        if (minutesSinceEpoch > 0) {
            try {
                LocalDateTime editTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(minutesSinceEpoch * 60),
                        ZoneId.systemDefault()
                );

                info.editedDateTime = editTime;
                info.editedTimeDisplay = editTime.format(DISPLAY_FORMATTER);
                info.editedTimeAgo = formatTimeAgo(editTime);

                // Update full description with timing
                info.fullDescription = String.format("%s Edited (%s)", info.roleName, info.editedTimeAgo);

            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Failed to parse timestamp %d from status %s", minutesSinceEpoch, rawStatus));
                info.editedTimeDisplay = "Invalid timestamp";
                info.editedTimeAgo = "Unknown time";
            }
        }
    }

    /**
     * Format time difference in human-readable format
     */
    private String formatTimeAgo(LocalDateTime editTime) {
        LocalDateTime now = LocalDateTime.now();

        long minutes = ChronoUnit.MINUTES.between(editTime, now);
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + "m ago";

        long hours = ChronoUnit.HOURS.between(editTime, now);
        if (hours < 24) return hours + "h ago";

        long days = ChronoUnit.DAYS.between(editTime, now);
        if (days < 7) return days + "d ago";

        long weeks = days / 7;
        if (weeks < 4) return weeks + "w ago";

        long months = ChronoUnit.MONTHS.between(editTime, now);
        if (months < 12) return months + " mo ago";

        long years = ChronoUnit.YEARS.between(editTime, now);
        return years + "y ago";
    }

    /**
     * Build comprehensive tooltip text
     */
    private String buildTooltipText(StatusInfo info) {
        StringBuilder tooltip = new StringBuilder();

        tooltip.append(info.fullDescription);

        if (info.editedDateTime != null) {
            tooltip.append("\nEdited on: ").append(info.editedTimeDisplay);
        }

        if (info.isFinal) {
            tooltip.append("\nStatus: Finalized (no further changes allowed)");
        } else if (info.isUserInProcess) {
            tooltip.append("\nStatus: Being actively edited by user");
        } else if (info.isModifiable) {
            tooltip.append("\nStatus: Can be modified");
        }

        return tooltip.toString();
    }

    // ========================================================================
    // HELPER CLASS
    // ========================================================================

    /**
     * Internal class to hold parsed status information
     */
    private static class StatusInfo {
        String rawStatus;
        String roleName;
        String roleType;
        String actionType;
        String fullDescription;
        LocalDateTime editedDateTime;
        String editedTimeDisplay;
        String editedTimeAgo;
        boolean isFinal;
        boolean isInput;
        boolean isUserInProcess;
        boolean isEdited;
        boolean isModifiable;
        String cssClass;
        String iconClass;
        String badgeClass;
        int priorityLevel;
    }
}