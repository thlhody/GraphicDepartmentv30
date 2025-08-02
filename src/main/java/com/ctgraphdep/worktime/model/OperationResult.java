package com.ctgraphdep.worktime.model;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import lombok.Getter;

import java.util.List;

@Getter
public class OperationResult {
    private final boolean success;
    private final String message;
    private final String operationType;
    private final Object data; // Can be WorkTimeTable, List<WorkTimeTable>, etc.
    private final OperationSideEffects sideEffects;

    private OperationResult(boolean success, String message, String operationType, Object data, OperationSideEffects sideEffects) {
        this.success = success;
        this.message = message;
        this.operationType = operationType;
        this.data = data;
        this.sideEffects = sideEffects;
    }

    // ========================================================================
    // FACTORY METHODS - Success Results
    // ========================================================================

    public static OperationResult success(String message, String operationType) {
        return new OperationResult(true, message, operationType, null, null);
    }

    public static OperationResult success(String message, String operationType, WorkTimeTable entry) {
        return new OperationResult(true, message, operationType, entry, null);
    }

    public static OperationResult success(String message, String operationType, List<WorkTimeTable> entries) {
        return new OperationResult(true, message, operationType, entries, null);
    }

    public static OperationResult success(String message, String operationType, Object data) {
        return new OperationResult(true, message, operationType, data, null);
    }

    public static OperationResult successWithSideEffects(String message, String operationType, Object data, OperationSideEffects sideEffects) {
        return new OperationResult(true, message, operationType, data, sideEffects);
    }

    // ========================================================================
    // FACTORY METHODS - Failure Results
    // ========================================================================

    public static OperationResult failure(String message, String operationType) {
        return new OperationResult(false, message, operationType, null, null);
    }

    public static OperationResult failure(String message, String operationType, String reason) {
        String fullMessage = message + (reason != null ? ": " + reason : "");
        return new OperationResult(false, fullMessage, operationType, null, null);
    }

    public static OperationResult validationFailure(String validationMessage, String operationType) {
        return new OperationResult(false, "Validation failed: " + validationMessage, operationType, null, null);
    }

    public static OperationResult permissionFailure(String operationType, String reason) {
        return new OperationResult(false, "Permission denied: " + reason, operationType, null, null);
    }

    // ========================================================================
    // DATA ACCESS HELPERS
    // ========================================================================

    /**
     * Get result data as WorkTimeTable entry
     */
    public WorkTimeTable getEntryData() {
        if (data instanceof WorkTimeTable) {
            return (WorkTimeTable) data;
        }
        return null;
    }

    /**
     * Get result data as list of WorkTimeTable entries
     */
    @SuppressWarnings("unchecked")
    public List<WorkTimeTable> getEntriesData() {
        if (data instanceof List<?>) {
            return (List<WorkTimeTable>) data;
        }
        return null;
    }

    /**
     * Get result data as list of TeamMemberDTO entries
     */
    @SuppressWarnings("unchecked")
    public List<TeamMemberDTO> getTeamMembersData() {
        if (data instanceof List<?>) {
            return (List<TeamMemberDTO>) data;
        }
        return null;
    }

    /**
     * Check if operation had side effects
     */
    public boolean hasSideEffects() {
        return sideEffects != null;
    }

    // ========================================================================
    // OPERATION TYPES - Constants
    // ========================================================================

    public static class OperationType {
        // Time field updates
        public static final String UPDATE_START_TIME = "UPDATE_START_TIME";
        public static final String UPDATE_END_TIME = "UPDATE_END_TIME";
        public static final String ADD_TEMPORARY_STOP = "ADD_TEMPORARY_STOP";
        public static final String REMOVE_TEMPORARY_STOP = "REMOVE_TEMPORARY_STOP";

        // Time off operations
        public static final String ADD_TIME_OFF = "ADD_TIME_OFF";
        public static final String REMOVE_TIME_OFF = "REMOVE_TIME_OFF";
        public static final String REMOVE_FIELD = "REMOVE_FIELD";
        public static final String REMOVE_ENTRY = "REMOVE_ENTRY";

        // Admin operations
        public static final String ADMIN_UPDATE = "ADMIN_UPDATE";
        public static final String ADMIN_DELETE = "ADMIN_DELETE";
        public static final String ADD_NATIONAL_HOLIDAY = "ADD_NATIONAL_HOLIDAY";
        public static final String FINALIZE_WORKTIME = "FINALIZE_WORKTIME";

        public static final String UPDATE_HOLIDAY_BALANCE = "UPDATE_HOLIDAY_BALANCE";
        public static final String CONSOLIDATE_WORKTIME = "CONSOLIDATE_WORKTIME";
        public static final String LOAD_USER_WORKTIME = "LOAD_USER_WORKTIME";

        public static final String DELETE_ENTRY = "DELETE_ENTRY";
        public static final String RESET_SPECIAL_DAY = "RESET_SPECIAL_DAY";
    }

    // ========================================================================
    // SIDE EFFECTS TRACKING
    // ========================================================================

    @Getter
    public static class OperationSideEffects {
        private final boolean holidayBalanceChanged;
        private final Integer oldHolidayBalance;
        private final Integer newHolidayBalance;
        private final boolean cacheInvalidated;
        private final String invalidatedCacheKey;
        private final boolean fileUpdated;
        private final String updatedFilePath;

        private OperationSideEffects(Builder builder) {
            this.holidayBalanceChanged = builder.holidayBalanceChanged;
            this.oldHolidayBalance = builder.oldHolidayBalance;
            this.newHolidayBalance = builder.newHolidayBalance;
            this.cacheInvalidated = builder.cacheInvalidated;
            this.invalidatedCacheKey = builder.invalidatedCacheKey;
            this.fileUpdated = builder.fileUpdated;
            this.updatedFilePath = builder.updatedFilePath;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean holidayBalanceChanged = false;
            private Integer oldHolidayBalance;
            private Integer newHolidayBalance;
            private boolean cacheInvalidated = false;
            private String invalidatedCacheKey;
            private boolean fileUpdated = false;
            private String updatedFilePath;

            public Builder holidayBalanceChanged(Integer oldBalance, Integer newBalance) {
                this.holidayBalanceChanged = true;
                this.oldHolidayBalance = oldBalance;
                this.newHolidayBalance = newBalance;
                return this;
            }

            public Builder cacheInvalidated(String cacheKey) {
                this.cacheInvalidated = true;
                this.invalidatedCacheKey = cacheKey;
                return this;
            }

            public Builder fileUpdated(String filePath) {
                this.fileUpdated = true;
                this.updatedFilePath = filePath;
                return this;
            }

            public OperationSideEffects build() {
                return new OperationSideEffects(this);
            }
        }
    }

    @Override
    public String toString() {
        return String.format("OperationResult{success=%s, type=%s, message='%s', hasSideEffects=%s}", success, operationType, message, hasSideEffects());
    }
}