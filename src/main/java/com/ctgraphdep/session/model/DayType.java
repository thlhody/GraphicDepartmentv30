package com.ctgraphdep.session.model;

import com.ctgraphdep.config.WorkCode;

// Enum representing different types of work days for overtime calculation logic
public enum DayType {


    REGULAR_DAY,        // Regular work day - normal overtime rules apply
    NATIONAL_HOLIDAY,   // National Holiday (SN) - all work time becomes overtime, rounded down to full hours
    TIME_OFF,           // Time Off (CO) - all work time becomes overtime, rounded down to full hours
    MEDICAL_LEAVE,      // Medical Leave (CM) - all work time becomes overtime, rounded down to full hours
    WEEKEND;            // Weekend (W) - all work time becomes overtime, rounded down to full hours

    // Check if this day type requires special overtime logic
    public boolean requiresSpecialOvertimeLogic() {
        return this != REGULAR_DAY;
    }

    // Get the corresponding time off code for this day type
    public String getTimeOffCode() {
        return switch (this) {
            case NATIONAL_HOLIDAY -> WorkCode.NATIONAL_HOLIDAY_CODE;
            case TIME_OFF ->  WorkCode.TIME_OFF_CODE;
            case MEDICAL_LEAVE -> WorkCode.MEDICAL_LEAVE_CODE;
            case WEEKEND -> WorkCode.WEEKEND_CODE;
            case REGULAR_DAY -> null;
        };
    }

    // Create DayType from time off code
    public static DayType fromTimeOffCode(String timeOffCode) {
        if (timeOffCode == null || timeOffCode.trim().isEmpty()) {
            return REGULAR_DAY;
        }

        return switch (timeOffCode.trim().toUpperCase()) {
            case WorkCode.NATIONAL_HOLIDAY_CODE -> NATIONAL_HOLIDAY;
            case WorkCode.TIME_OFF_CODE -> TIME_OFF;
            case WorkCode.MEDICAL_LEAVE_CODE -> MEDICAL_LEAVE;
            case WorkCode.WEEKEND_CODE -> WEEKEND;
            default -> REGULAR_DAY;
        };
    }

    //Get human-readable description
    public String getDescription() {
        return switch (this) {
            case REGULAR_DAY -> WorkCode.REGULAR_WORK_DAY;
            case NATIONAL_HOLIDAY -> WorkCode.NATIONAL_HOLIDAY_CODE_LONG;
            case TIME_OFF -> WorkCode.TIME_OFF_CODE_LONG;
            case MEDICAL_LEAVE -> WorkCode.MEDICAL_LEAVE_CODE_LONG;
            case WEEKEND -> WorkCode.WEEKEND_CODE_LONG;
        };
    }
}