package com.ctgraphdep.validation;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.commands.ValidateHolidayDateCommand;
import com.ctgraphdep.validation.commands.ValidatePeriodCommand;
import com.ctgraphdep.validation.commands.ValidateUserEditDateCommand;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * EXPANDED TimeValidationService - Centralized validation for all time-related operations.
 * Consolidates validation logic from controllers into a single, testable service.
 * Uses existing validation commands where possible and provides consistent ValidationResult responses.
 */
@Getter
@Service
public class TimeValidationService {

    private final TimeValidationFactory validationFactory;

    public TimeValidationService(TimeValidationFactory validationFactory) {
        this.validationFactory = validationFactory;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Execute a time validation command (EXISTING METHOD)
     */
    public <T> T execute(TimeValidationCommand<T> command) {
        String commandName = command.getClass().getSimpleName();
        try {
            LoggerUtil.debug(this.getClass(), "Executing time validation command: " + commandName);
            T result = command.execute();
            LoggerUtil.debug(this.getClass(), "Command executed successfully: " + commandName);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error executing time validation command " + commandName + ": " + e.getMessage(), e);
            throw e;
        }
    }

    // ========================================================================
    // USER FIELD VALIDATION (from UserTimeManagementController)
    // ========================================================================

    /**
     * REFACTORED: Light validation - only basic business rules, no file checking
     * Part 1 of 2-part filter: removes weekends, validates date ranges
     * Part 2 happens in AddTimeOffCommand with actual file checking
     */
    public ValidationResult validateTimeOffRequestLight(String startDate, String endDate, String timeOffType, boolean isSingleDay) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "=== LIGHT VALIDATION START === StartDate: %s, EndDate: %s, Type: %s, SingleDay: %s",
                    startDate, endDate, timeOffType, isSingleDay));

            // 1. VALIDATE TIME OFF TYPE
            LoggerUtil.debug(this.getClass(), "Step 1: Validating time off type...");
            ValidationResult typeResult = validateTimeOffRequestType(timeOffType);
            if (typeResult.isInvalid()) {
                LoggerUtil.warn(this.getClass(), "Type validation failed: " + typeResult.getErrorMessage());
                return typeResult;
            }
            LoggerUtil.debug(this.getClass(), "Step 1: Time off type validation passed");

            // 2. PARSE DATES
            LoggerUtil.debug(this.getClass(), "Step 2: Parsing dates...");
            LocalDate start;
            LocalDate end;
            try {
                start = LocalDate.parse(startDate);
                end = isSingleDay ? start : LocalDate.parse(endDate);
                LoggerUtil.debug(this.getClass(), String.format("Step 2: Dates parsed - start: %s, end: %s", start, end));
            } catch (DateTimeParseException e) {
                LoggerUtil.error(this.getClass(), "Date parsing failed: " + e.getMessage());
                return ValidationResult.invalid("Invalid date format: " + e.getMessage());
            }

            // 3. BUSINESS RULE VALIDATION
            LoggerUtil.debug(this.getClass(), "Step 3: Business rule validation...");
            if (start.isAfter(end)) {
                LoggerUtil.warn(this.getClass(), "Start date is after end date");
                return ValidationResult.invalid("Start date cannot be after end date");
            }

            if (start.until(end).getDays() > 30) {
                LoggerUtil.warn(this.getClass(), "Date range too large: " + start.until(end).getDays() + " days");
                return ValidationResult.invalid("Date range too large (maximum 30 days allowed)");
            }
            LoggerUtil.debug(this.getClass(), "Step 3: Business rules passed");

            // 4. LIGHT DATE VALIDATION - No file checking, just basic rules
            LoggerUtil.debug(this.getClass(), "Step 4: Light date validation (weekends, past/future only)...");
            List<LocalDate> potentialDates = new ArrayList<>();
            List<LocalDate> rejectedWeekends = new ArrayList<>();
            LocalDate current = start;

            while (!current.isAfter(end)) {
                LoggerUtil.debug(this.getClass(), String.format("Step 4: Checking date %s (weekday: %d)",
                        current, current.getDayOfWeek().getValue()));

                // Check basic date validity (past/future limits)
                ValidationResult basicDateResult = validateBasicDateRules(current);
                if (basicDateResult.isInvalid()) {
                    LoggerUtil.warn(this.getClass(), String.format("Basic date validation failed for %s: %s",
                            current, basicDateResult.getErrorMessage()));
                    return ValidationResult.invalid(String.format("Date %s: %s", current, basicDateResult.getErrorMessage()));
                }

                // Handle weekends
                if (current.getDayOfWeek().getValue() >= 6) {
                    rejectedWeekends.add(current);
                    LoggerUtil.debug(this.getClass(), String.format("Step 4: Rejecting weekend %s", current));

                    // For single day requests, weekend is an error
                    if (isSingleDay) {
                        LoggerUtil.warn(this.getClass(), String.format("Single day request on weekend %s", current));
                        return ValidationResult.invalid(String.format("Cannot request time off on weekends. Date: %s (%s)",
                                current, current.getDayOfWeek().toString()));
                    }
                    // For multi-day requests, just skip weekends
                } else {
                    // Weekday - add to potential dates for further processing
                    potentialDates.add(current);
                    LoggerUtil.debug(this.getClass(), String.format("Step 4: Weekend/basic validation passed for %s", current));
                }
                current = current.plusDays(1);
            }

            if (potentialDates.isEmpty()) {
                String message = "No valid weekdays found in the selected date range";
                if (!rejectedWeekends.isEmpty()) {
                    message += String.format(" (rejected %d weekends)", rejectedWeekends.size());
                }
                LoggerUtil.warn(this.getClass(), message);
                return ValidationResult.invalid(message);
            }

            LoggerUtil.info(this.getClass(), String.format("=== LIGHT VALIDATION SUCCESS === %d potential dates passed basic validation: %s",
                    potentialDates.size(), potentialDates));

            if (!rejectedWeekends.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("Rejected weekends: %s", rejectedWeekends));
            }

            return ValidationResult.valid();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Light time off request validation error: " + e.getMessage(), e);
            return ValidationResult.invalid("Validation failed: " + e.getMessage());
        }
    }

    /**
     * NEW: Basic date rules validation - past/future limits only
     */
    private ValidationResult validateBasicDateRules(LocalDate date) {
        try {
            // Get current date using standard time
            var getTimeCommand = validationFactory.createGetStandardTimeValuesCommand();
            var timeValues = execute(getTimeCommand);
            LocalDate today = timeValues.getCurrentDate();


            LocalDate earliestAllowed = today.minusMonths(1);
            if (date.isBefore(earliestAllowed)) {
                return ValidationResult.invalid(String.format("Cannot request time off for dates more than 1 month in the past. Date: %s", date));
            }

            // 2. Check if date is too far in the future (6 months limit)
            LocalDate latestAllowed = today.plusMonths(6);
            if (date.isAfter(latestAllowed)) {
                return ValidationResult.invalid(String.format("Cannot request time off more than 6 months in advance. Date: %s (max: %s)", date, latestAllowed));
            }

            return ValidationResult.valid();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in basic date validation for %s: %s", date, e.getMessage()));
            return ValidationResult.invalid("Date validation failed: " + e.getMessage());
        }
    }

    /**
     * COMPREHENSIVE validation for user field updates.
     * Moved from UserTimeManagementController.validateUserFieldUpdate()
     */
    public ValidationResult validateUserFieldUpdate(LocalDate date, String field, String value, User currentUser, String existingTimeOffType) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Validating user field update: date=%s, field=%s, value=%s, user=%s",
                    date, field, value, currentUser.getUsername()));

            // 1. BASIC PARAMETER VALIDATION
            if (field == null || field.trim().isEmpty()) {
                return ValidationResult.invalid("Field name cannot be empty");
            }

            // 2. DATE VALIDATION using existing command
            ValidateUserEditDateCommand dateCommand = validationFactory.createValidateUserEditDateCommand(
                    date, field, existingTimeOffType, false); // false = not admin

            try {
                ValidateUserEditDateCommand.ValidationResult dateResult = execute(dateCommand);
                if (dateResult.isInvalid()) {
                    return ValidationResult.invalid(dateResult.getReason());
                }
            } catch (Exception e) {
                return ValidationResult.invalid("Date validation failed: " + e.getMessage());
            }

            // 3. FIELD-SPECIFIC VALIDATION - FIXED FOR TIME OFF REMOVAL
            return switch (field.toLowerCase()) {
                case "starttime", "endtime" -> validateTimeFieldFormat(value);
                case "timeoff" -> {
                    // TIME OFF FIELD VALIDATION - Handle both addition and removal
                    if (value == null || value.trim().isEmpty()) {
                        // TIME OFF REMOVAL - Use different date range validation
                        yield validateTimeOffRemovalDateRange(date);
                    } else {
                        // TIME OFF ADDITION - Use existing validation
                        yield validateTimeOffField(value, date);
                    }
                }
                case "tempstop" -> validateTempStopField(value);
                default -> ValidationResult.invalid("Unknown field type: " + field);
            };
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "User field validation error: " + e.getMessage(), e);
            return ValidationResult.invalid("Validation failed: " + e.getMessage());
        }
    }

    /**
     * NEW: Validate time off removal date range (previous month + current + future)
     */
    private ValidationResult validateTimeOffRemovalDateRange(LocalDate date) {
        try {
            // Get current date using standard time
            var getTimeCommand = validationFactory.createGetStandardTimeValuesCommand();
            var timeValues = execute(getTimeCommand);
            LocalDate today = timeValues.getCurrentDate();

            // Calculate the earliest allowed date (first day of previous month)
            LocalDate earliestAllowed = today.withDayOfMonth(1).minusMonths(1);

            if (date.isBefore(earliestAllowed)) {
                // Calculate which month the date is in for better error message
                String dateMonth = date.getMonth().toString() + " " + date.getYear();
                String earliestMonth = earliestAllowed.getMonth().toString() + " " + earliestAllowed.getYear();

                return ValidationResult.invalid(String.format(
                        "Cannot remove time off from %s. You can only remove time off from %s onwards (previous month + current + future).",
                        dateMonth, earliestMonth));
            }

            return ValidationResult.valid();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in time off removal date validation for %s: %s", date, e.getMessage()));
            return ValidationResult.invalid("Date validation failed: " + e.getMessage());
        }
    }

    /**
     * Validate time field format (HH:mm)
     * Moved from UserTimeManagementController.validateTimeField()
     */
    public ValidationResult validateTimeFieldFormat(String value) {
        if (value != null && !value.trim().isEmpty()) {
            if (!value.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                return ValidationResult.invalid("Invalid time format. Use HH:mm (e.g., 09:00)");
            }
        }
        return ValidationResult.valid();
    }

    /**
     * Validate time off field for users
     * Moved from UserTimeManagementController.validateTimeOffField()
     */
    public ValidationResult validateTimeOffField(String value, LocalDate date) {
        if (value != null && !value.trim().isEmpty()) {
            String timeOffType = value.trim().toUpperCase();

            // 1. VALIDATE TIME OFF TYPE
            if (!WorkCode.TIME_OFF_CODE.equals(timeOffType) && !WorkCode.MEDICAL_LEAVE_CODE.equals(timeOffType)) {
                return ValidationResult.invalid("Users can only add CO (vacation) or CM (medical) time off");
            }

            // 2. WEEKEND VALIDATION using existing command
            if (date.getDayOfWeek().getValue() >= 6) {
                ValidateHolidayDateCommand holidayCommand = validationFactory.createValidateHolidayDateCommand(date);
                try {
                    execute(holidayCommand);
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("weekends")) {
                        return ValidationResult.invalid("Cannot add time off on weekends");
                    }
                }
            }
        }
        return ValidationResult.valid();
    }

    /**
     * Validate temporary stop field (minutes 0-720)
     */
    public ValidationResult validateTempStopField(String value) {
        if (value != null && !value.trim().isEmpty()) {
            try {
                int minutes = Integer.parseInt(value.trim());
                if (minutes < 0) {
                    return ValidationResult.invalid("Temporary stop minutes cannot be negative");
                }
                if (minutes > 720) {
                    return ValidationResult.invalid("Temporary stop cannot exceed 12 hours (720 minutes)");
                }
            } catch (NumberFormatException e) {
                return ValidationResult.invalid("Invalid temporary stop format. Use whole numbers (e.g., 60)");
            }
        }
        return ValidationResult.valid();
    }

    // ========================================================================
    // ADMIN VALIDATION (from AdminWorkTimeController)
    // ========================================================================

    /**
     * FIXED: Admin-specific worktime update validation with support for all special day work formats
     * Now supports: SN:5, CO:6, CM:4, W:8 (previously only SN:5 worked)
     */
    public ValidationResult validateAdminWorktimeUpdate(LocalDate date, String value) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Validating admin worktime update: date=%s, value=%s", date, value));

            // 1. DATE VALIDATION using existing command (admin has wider range)
            try {
                ValidatePeriodCommand periodCommand = validationFactory.createValidatePeriodCommand(
                        date.getYear(), date.getMonthValue(), 60); // 5 years for admin
                execute(periodCommand);
            } catch (IllegalArgumentException e) {
                return ValidationResult.invalid("Date is outside admin editable range: " + e.getMessage());
            }

            // 2. VALUE VALIDATION
            if (value == null || value.trim().isEmpty()) {
                return ValidationResult.invalid("Value cannot be empty");
            }

            String trimmedValue = value.trim().toUpperCase();

            // 3. ENHANCED VALUE TYPE VALIDATION - Now supports all special day work formats
            if (isSpecialDayWorkFormat(trimmedValue)) {
                // FIXED: Handle all special day work formats (SN:5, CO:6, CM:4, W:8)
                return validateSpecialDayWorkFormat(trimmedValue);
            } else if (trimmedValue.matches("^\\d+$")) {
                return validateWorkHoursRange(trimmedValue);
            } else if (trimmedValue.matches("^(CO|CM|SN|CR|CN|CE|D|REMOVE|BLANK)$")) {
                return validateTimeOffOrRemove(trimmedValue);
            } else {
                return ValidationResult.invalid("Invalid value format. Use: hours (8), time off (CO/CM/SN), special day work (SN:7.5, CO:6, CM:4, W:8), BLANK, or REMOVE");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Admin worktime validation error: " + e.getMessage(), e);
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    /**
     * NEW: Check if value matches special day work format for any type
     * Supports: SN:5, CO:6, CM:4, W:8
     */
    private boolean isSpecialDayWorkFormat(String value) {
        return value.matches("^(SN|CO|CM|W):\\d+(\\.\\d+)?$");
    }

    /**
     * NEW: Unified validation for all special day work formats
     * Replaces validateSNWorkTimeFormat() with comprehensive validation for all types
     */
    private ValidationResult validateSpecialDayWorkFormat(String value) {
        LoggerUtil.debug(this.getClass(), String.format("Validating special day work format: %s", value));

        String[] parts = value.split(":");
        if (parts.length != 2) {
            return ValidationResult.invalid("Invalid special day work format. Use TYPE:hours (e.g., SN:7.5, CO:6, CM:4, W:8)");
        }

        String type = parts[0];
        if (!type.matches("^(SN|CO|CM|W)$")) {
            return ValidationResult.invalid("Invalid special day type. Use SN (National Holiday), CO (Time Off), CM (Medical Leave), or W (Weekend)");
        }

        try {
            double hours = Double.parseDouble(parts[1]);
            if (hours < 0.5 || hours > 24) {
                return ValidationResult.invalid(String.format("%s work hours must be between 0.5 and 24 (input: %.1f)", type, hours));
            }

            // Log successful validation
            LoggerUtil.debug(this.getClass(), String.format("Special day work format validated: %s = %.1f hours", type, hours));
            return ValidationResult.valid();

        } catch (NumberFormatException e) {
            return ValidationResult.invalid(String.format("Invalid hours format for %s. Use decimal numbers (e.g., %s:7.5)", type, type));
        }
    }

    /**
     * Validate work hours range (admin sets specific hours)
     * Moved from AdminWorkTimeController.validateWorkHours()
     */
    public ValidationResult validateWorkHoursRange(String value) {
        try {
            int hours = Integer.parseInt(value);
            if (hours < 1 || hours > 16) {
                return ValidationResult.invalid("Work hours must be between 1 and 16");
            }
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("Invalid hours format");
        }

        return ValidationResult.valid();
    }

    /**
     * Validate holiday addition (admin-specific rules)
     * Moved from AdminWorkTimeController.validateHolidayAddition()
     */
    public ValidationResult validateHolidayAddition(LocalDate holidayDate) {
        try {
            // Period validation (admin can add for future periods)
            ValidatePeriodCommand periodCommand = validationFactory.createValidatePeriodCommand(
                    holidayDate.getYear(), holidayDate.getMonthValue(), 24); // 2 years ahead
            execute(periodCommand);

            // Holiday date validation (weekend check, etc.)
            ValidateHolidayDateCommand holidayCommand = validationFactory.createValidateHolidayDateCommand(holidayDate);
            execute(holidayCommand);

            return ValidationResult.valid();

        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("weekends")) {
                return ValidationResult.invalid("Cannot add holidays on weekends");
            } else if (e.getMessage().contains("past months")) {
                return ValidationResult.invalid("Cannot add holidays for past months");
            } else {
                return ValidationResult.invalid("Invalid holiday date: " + e.getMessage());
            }
        } catch (Exception e) {
            return ValidationResult.invalid("Holiday validation failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * UPDATED: Validate time off or remove operation (admin) - Added BLANK support
     */
    private ValidationResult validateTimeOffOrRemove(String value) {
        if (!WorkCode.TIME_OFF_CODE.equals(value) && !WorkCode.MEDICAL_LEAVE_CODE.equals(value) && !WorkCode.NATIONAL_HOLIDAY_CODE.equals(value)
                && !WorkCode.RECOVERY_LEAVE_CODE.equals(value) && !WorkCode.UNPAID_LEAVE_CODE.equals(value) && !WorkCode.DELEGATION_CODE.equals(value)
                && !WorkCode.SPECIAL_EVENT_CODE.equals(value) && !"REMOVE".equals(value) && !"BLANK".equals(value)) {
            return ValidationResult.invalid("Invalid operation. Use CO, CM, SN, BLANK, or REMOVE");
        }
        return ValidationResult.valid();
    }

    /**
     * Validate time off request type
     * Moved from UserTimeManagementController.validateTimeOffRequestType()
     */
    private ValidationResult validateTimeOffRequestType(String timeOffType) {
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            return ValidationResult.invalid("Time off type is required");
        }

        if (!WorkCode.TIME_OFF_CODE.equals(timeOffType) && !WorkCode.MEDICAL_LEAVE_CODE.equals(timeOffType) &&
                !WorkCode.RECOVERY_LEAVE_CODE.equals(timeOffType) && !WorkCode.UNPAID_LEAVE_CODE.equals(timeOffType)
                && !WorkCode.DELEGATION_CODE.equals(timeOffType) && !WorkCode.SPECIAL_EVENT_CODE.equals(timeOffType)) {
            return ValidationResult.invalid("Invalid time off type. Users can only request CO (vacation) or CM (medical)");
        }

        return ValidationResult.valid();
    }
}