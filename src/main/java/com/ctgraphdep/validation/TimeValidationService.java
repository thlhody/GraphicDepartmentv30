package com.ctgraphdep.validation;

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
     * COMPREHENSIVE validation for user field updates.
     * Moved from UserTimeManagementController.validateUserFieldUpdate()
     */
    public ValidationResult validateUserFieldUpdate(LocalDate date, String field, String value, User currentUser) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Validating user field update: date=%s, field=%s, value=%s, user=%s",
                    date, field, value, currentUser.getUsername()));

            // 1. BASIC PARAMETER VALIDATION
            if (field == null || field.trim().isEmpty()) {
                return ValidationResult.invalid("Field name cannot be empty");
            }

            // 2. DATE VALIDATION using existing command
            String existingTimeOffType = getExistingTimeOffType(date, currentUser);
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

            // 3. FIELD-SPECIFIC VALIDATION
            return switch (field.toLowerCase()) {
                case "starttime", "endtime" -> validateTimeFieldFormat(value);
                case "timeoff" -> validateTimeOffField(value, date);
                case "tempstop" -> validateTempStopField(value);  // ← ADD THIS LINE
                default -> ValidationResult.invalid("Unknown field type: " + field);
            };
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "User field validation error: " + e.getMessage(), e);
            return ValidationResult.invalid("Validation failed: " + e.getMessage());
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
            if (!"CO".equals(timeOffType) && !"CM".equals(timeOffType)) {
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
     * Validate and parse date range for time off requests
     * Moved from UserTimeManagementController.validateAndParseDateRange()
     */
    public ValidationResult validateTimeOffRequest(String startDate, String endDate, String timeOffType, boolean isSingleDay) {
        try {
            // 1. VALIDATE TIME OFF TYPE
            ValidationResult typeResult = validateTimeOffRequestType(timeOffType);
            if (typeResult.isInvalid()) {
                return typeResult;
            }

            // 2. PARSE DATES
            LocalDate start;
            LocalDate end;
            try {
                start = LocalDate.parse(startDate);
                end = isSingleDay ? start : LocalDate.parse(endDate);
            } catch (DateTimeParseException e) {
                return ValidationResult.invalid("Invalid date format: " + e.getMessage());
            }

            // 3. BUSINESS RULE VALIDATION
            if (start.isAfter(end)) {
                return ValidationResult.invalid("Start date cannot be after end date");
            }

            if (start.until(end).getDays() > 30) {
                return ValidationResult.invalid("Date range too large (maximum 30 days allowed)");
            }

            // 4. VALIDATE INDIVIDUAL DATES
            List<LocalDate> validDates = new ArrayList<>();
            LocalDate current = start;

            while (!current.isAfter(end)) {
                // Skip weekends
                if (current.getDayOfWeek().getValue() < 6) {
                    ValidationResult dateResult = validateTimeOffRequestDate(current);
                    if (dateResult.isInvalid()) {
                        return ValidationResult.invalid(String.format("Date %s: %s", current, dateResult.getErrorMessage()));
                    }
                    validDates.add(current);
                }
                current = current.plusDays(1);
            }

            if (validDates.isEmpty()) {
                return ValidationResult.invalid("No valid weekdays found in the selected date range");
            }

            LoggerUtil.debug(this.getClass(), String.format("Time off request validated: %d dates", validDates.size()));
            return ValidationResult.valid();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Time off request validation error: " + e.getMessage(), e);
            return ValidationResult.invalid("Validation failed: " + e.getMessage());
        }
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
     * Admin-specific worktime update validation (more permissive than user validation)
     * Moved from AdminWorkTimeController.validateAdminWorktimeUpdate()
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

            // 3. VALUE TYPE VALIDATION
            if (trimmedValue.startsWith("SN:")) {
                return validateSNWorkTimeFormat(trimmedValue);
            } else if (trimmedValue.matches("^\\d+$")) {
                return validateWorkHoursRange(trimmedValue);
            } else if (trimmedValue.matches("^(CO|CM|SN|REMOVE)$")) {
                return validateTimeOffOrRemove(trimmedValue);
            } else {
                return ValidationResult.invalid("Invalid value format. Use: hours (8), time off (CO/CM/SN), SN work (SN:7.5), or REMOVE");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Admin worktime validation error: " + e.getMessage(), e);
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validate SN work time format (admin only)
     * Moved from AdminWorkTimeController.validateSNWorkTime()
     */
    public ValidationResult validateSNWorkTimeFormat(String value) {
        String[] parts = value.split(":");
        if (parts.length != 2 || !parts[0].equals("SN")) {
            return ValidationResult.invalid("Invalid SN format. Use SN:hours (e.g., SN:7.5)");
        }

        try {
            double hours = Double.parseDouble(parts[1]);
            if (hours < 0.5 || hours > 24) {
                return ValidationResult.invalid("SN work hours must be between 0.5 and 24");
            }
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("Invalid hours format. Use decimal numbers (e.g., SN:7.5)");
        }

        return ValidationResult.valid();
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
     * Validate time off or remove operation (admin)
     */
    private ValidationResult validateTimeOffOrRemove(String value) {
        if (!"CO".equals(value) && !"CM".equals(value) && !"SN".equals(value) && !"REMOVE".equals(value)) {
            return ValidationResult.invalid("Invalid operation. Use CO, CM, SN, or REMOVE");
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

        if (!"CO".equals(timeOffType) && !"CM".equals(timeOffType)) {
            return ValidationResult.invalid("Invalid time off type. Users can only request CO (vacation) or CM (medical)");
        }

        return ValidationResult.valid();
    }

    /**
     * Validate individual date for time off request
     * Moved from UserTimeManagementController.validateTimeOffRequestDate()
     */
    private ValidationResult validateTimeOffRequestDate(LocalDate date) {
        try {
            // Use existing date validation command
            ValidateUserEditDateCommand command = validationFactory.createValidateUserEditDateCommand(date, "timeOff", null, false);
            ValidateUserEditDateCommand.ValidationResult result = execute(command);

            if (result.isInvalid()) {
                return ValidationResult.invalid(result.getReason());
            }

            // Weekend validation using existing command
            if (date.getDayOfWeek().getValue() >= 6) {
                ValidateHolidayDateCommand holidayCommand = validationFactory.createValidateHolidayDateCommand(date);
                try {
                    execute(holidayCommand);
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("weekends")) {
                        return ValidationResult.invalid("Cannot request time off on weekends");
                    }
                }
            }

            return ValidationResult.valid();

        } catch (Exception e) {
            return ValidationResult.invalid("Date validation failed: " + e.getMessage());
        }
    }

    /**
     * Helper to get existing time off type for a date and user
     * TODO: This needs to be implemented to check existing worktime data
     */
    private String getExistingTimeOffType(LocalDate date, User user) {
        // TODO: Implement logic to check existing time off type from worktime data
        LoggerUtil.info(this.getClass(), user.getUsername()+" "+date);
        // For now, return null (no existing time off)
        return null;
    }
}