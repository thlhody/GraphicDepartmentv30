package com.ctgraphdep.worktime.display.mappers;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import org.springframework.stereotype.Component;

/**
 * Centralized mapper for time-off type labels and status information.
 * This class provides THE SINGLE SOURCE OF TRUTH for:
 * - Time-off type labels (SN → "Sărbătoare Națională", CO → "Concediu Odihnă", etc.)
 * - Status labels (USER_INPUT → "User Completed", ADMIN_EDITED_[ts] → "Admin Modified", etc.)
 * - Status CSS classes (for frontend styling)
 * - Special day type identification
 * Previously scattered across WorktimeDisplayService as multiple private methods.
 */
@Component
public class TimeOffLabelMapper {

    /**
     * Get human-readable display label for time-off type code.
     * @param timeOffType Time-off type code (e.g., "SN", "CO", "CM", "ZS")
     * @return Human-readable label (e.g., "Sărbătoare Națională", "Concediu Odihnă")
     */
    public String getTimeOffLabel(String timeOffType) {
        if (timeOffType == null) {
            return null;
        }

        return switch (timeOffType) {
            case WorkCode.NATIONAL_HOLIDAY_CODE -> WorkCode.NATIONAL_HOLIDAY_CODE_LONG;
            case WorkCode.TIME_OFF_CODE -> WorkCode.TIME_OFF_CODE_LONG;
            case WorkCode.MEDICAL_LEAVE_CODE -> WorkCode.MEDICAL_LEAVE_CODE_LONG;
            case WorkCode.RECOVERY_LEAVE_CODE -> WorkCode.RECOVERY_LEAVE_CODE_LONG;
            case WorkCode.UNPAID_LEAVE_CODE -> WorkCode.UNPAID_LEAVE_CODE_LONG;
            case WorkCode.SPECIAL_EVENT_CODE -> WorkCode.SPECIAL_EVENT_CODE_LONG;
            case WorkCode.WEEKEND_CODE -> WorkCode.WEEKEND_CODE_LONG;
            case WorkCode.SHORT_DAY_CODE -> WorkCode.SHORT_DAY_CODE_LONG;
            case WorkCode.DELEGATION_CODE -> WorkCode.DELEGATION_CODE_LONG;
            default -> timeOffType;  // Fallback to code itself if unknown
        };
    }

    /**
     * Get human-readable status label for admin sync status.
     * Handles both base statuses and timestamped edit statuses.
     * @param adminSync Status code (e.g., "USER_INPUT", "ADMIN_EDITED_1234567890")
     * @return Human-readable status label (e.g., "User Completed", "Admin Modified")
     */
    public String getStatusLabel(String adminSync) {
        if (adminSync == null) {
            return null;
        }

        // Handle base statuses (without timestamps)
        switch (adminSync) {
            case MergingStatusConstants.USER_INPUT:
                return "User Completed";
            case MergingStatusConstants.USER_IN_PROCESS:
                return "In Progress";
            case MergingStatusConstants.ADMIN_FINAL:
                return "Admin Final";
            case MergingStatusConstants.TEAM_FINAL:
                return "Team Final";
            case MergingStatusConstants.ADMIN_INPUT:
                return "Admin Input";
            case MergingStatusConstants.TEAM_INPUT:
                return "Team Input";
        }

        // Handle timestamped edit statuses (e.g., "ADMIN_EDITED_1234567890")
        if (MergingStatusConstants.isTimestampedEditStatus(adminSync)) {
            String editorType = MergingStatusConstants.getEditorType(adminSync);
            return editorType + " Modified";
        }

        // Fallback for unrecognized statuses
        return "Unknown Status";
    }

    /**
     * Get CSS class for status display (for frontend styling).
     * Maps status codes to Bootstrap color classes.
     * @param adminSync Status code
     * @return CSS class name (e.g., "text-success", "text-warning", "text-danger")
     */
    public String getStatusClass(String adminSync) {
        if (adminSync == null) {
            return "text-muted";
        }

        // Handle base statuses
        switch (adminSync) {
            case MergingStatusConstants.USER_INPUT:
                return "text-success";
            case MergingStatusConstants.USER_IN_PROCESS:
                return "text-info";
            case MergingStatusConstants.ADMIN_FINAL:
                return "text-danger";
            case MergingStatusConstants.TEAM_FINAL:
                return "text-warning";
            case MergingStatusConstants.ADMIN_INPUT:
            case MergingStatusConstants.TEAM_INPUT:
                return "text-primary";
        }

        // Handle timestamped edit statuses
        if (MergingStatusConstants.isTimestampedEditStatus(adminSync)) {
            String editorType = MergingStatusConstants.getEditorType(adminSync);
            return switch (editorType) {
                case SecurityConstants.ROLE_USER -> "text-primary";
                case SecurityConstants.ROLE_ADMIN -> "text-warning";
                case SecurityConstants.ROLE_TEAM_LEADER -> "text-info";
                default -> "text-muted";
            };
        }

        // Fallback for unrecognized statuses - use danger to highlight issues
        return "text-danger";
    }

    /**
     * Check if a time-off type is a "special day" type.
     * Special day types are those that can have overtime work performed on them.
     * Examples: SN:5 (holiday with 5 hours work), CO:6 (vacation with 6 hours work)
     * @param timeOffType Time-off type code (e.g., "SN", "CO", "CM", "CE", "W")
     * @return true if this type supports overtime work, false otherwise
     */
    public boolean isSpecialDayType(String timeOffType) {
        if (timeOffType == null) {
            return false;
        }

        return WorkCode.NATIONAL_HOLIDAY_CODE.equals(timeOffType) ||
               WorkCode.TIME_OFF_CODE.equals(timeOffType) ||
               WorkCode.MEDICAL_LEAVE_CODE.equals(timeOffType) ||
               WorkCode.SPECIAL_EVENT_CODE.equals(timeOffType) ||
               WorkCode.WEEKEND_CODE.equals(timeOffType);
    }
}
