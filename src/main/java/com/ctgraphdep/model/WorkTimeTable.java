package com.ctgraphdep.model;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkTimeTable {

    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("workDate")
    private LocalDate workDate;

    @JsonProperty("dayStartTime")
    private LocalDateTime dayStartTime;

    @JsonProperty("dayEndTime")
    private LocalDateTime dayEndTime;

    @JsonProperty("temporaryStopCount")
    private Integer temporaryStopCount;

    @JsonProperty("lunchBreakDeducted")
    private boolean lunchBreakDeducted;

    @JsonProperty("timeOffType")
    private String timeOffType;

    @JsonProperty("totalWorkedMinutes")
    private Integer totalWorkedMinutes;

    @JsonProperty("totalTemporaryStopMinutes")
    private Integer totalTemporaryStopMinutes;

    // Getter for temporaryStops - ensure backward compatibility
    @JsonProperty("temporaryStops")
    private List<TemporaryStop> temporaryStops;

    @JsonProperty("totalOvertimeMinutes")
    private Integer totalOvertimeMinutes;

    @JsonProperty("adminSync")
    private String adminSync;


    /**
     * FIXED JsonSetter that preserves new timestamped statuses while cleaning old enum values.
     * Preserves: USER_INPUT, USER_IN_PROCESS, ADMIN_INPUT, ADMIN_FINAL, TEAM_FINAL, DELETE
     * Preserves: USER_EDITED_[epoch], ADMIN_EDITED_[epoch], TEAM_EDITED_[epoch]
     * Converts: All old SyncStatusMerge enum values → USER_INPUT
     */
    @JsonSetter("adminSync")
    public void setAdminSyncFromJson(Object value) {
        if (value instanceof String stringValue) {
            // Preserve new format statuses
            if (isNewFormatStatus(stringValue)) {
                this.adminSync = stringValue;
            } else {
                // Convert old enum values to USER_INPUT for cleanup
                this.adminSync = convertOldStatusForCleanup(stringValue);
            }
        } else {
            // Any non-string value → USER_INPUT
            this.adminSync = MergingStatusConstants.USER_INPUT;
        }
    }

    /**
     * Check if status is in new format (should be preserved)
     */
    private boolean isNewFormatStatus(String status) {
        return MergingStatusConstants.USER_INPUT.equals(status) ||
                MergingStatusConstants.USER_IN_PROCESS.equals(status) ||
                MergingStatusConstants.ADMIN_INPUT.equals(status) ||
                MergingStatusConstants.ADMIN_FINAL.equals(status) ||
                MergingStatusConstants.TEAM_FINAL.equals(status) ||
                MergingStatusConstants.isTimestampedEditStatus(status);
    }

    /**
     * Convert old/unknown statuses to USER_INPUT, preserve valid new format statuses
     */
    private String convertOldStatusForCleanup(String oldStatus) {
        if (oldStatus == null) {
            return MergingStatusConstants.USER_INPUT;
        }

        // Preserve valid new format statuses
        if (isNewFormatStatus(oldStatus)) {
            return oldStatus;
        }

        // Convert old/unknown statuses to USER_INPUT
        return MergingStatusConstants.USER_INPUT;
    }

    // Standard getter - ensure never null
    public String getAdminSync() {
        if (adminSync == null || adminSync.trim().isEmpty()) {
            return MergingStatusConstants.USER_INPUT;
        }
        return adminSync;
    }
}