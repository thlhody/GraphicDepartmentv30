package com.ctgraphdep.validation;

import com.ctgraphdep.service.WorktimeManagementService;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Validator component for time-off requests.
 * Centralizes validation logic for time-off requests.
 */
@Component
public class TimeOffRequestValidator {

    private final TimeValidationService timeValidationService;
    private final WorktimeManagementService worktimeManagementService;

    public TimeOffRequestValidator(
            TimeValidationService timeValidationService,
            WorktimeManagementService worktimeManagementService) {
        this.timeValidationService = timeValidationService;
        this.worktimeManagementService = worktimeManagementService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Validates a time-off request.
     *
     * @param startDate The start date of the time-off period
     * @param endDate The end date of the time-off period
     * @param timeOffType The type of time-off (CO, CM)
     * @param availableDays Available paid days for CO requests
     * @return ValidationResult containing the result of the validation
     */
    public ValidationResult validateRequest(
            LocalDate startDate,
            LocalDate endDate,
            String timeOffType,
            int availableDays) {

        List<String> errors = new ArrayList<>();

        // Validate required fields
        if (startDate == null) {
            errors.add("Start date is required");
        }

        if (endDate == null) {
            errors.add("End date is required");
        }

        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            errors.add("Time-off type is required");
        } else if (!Arrays.asList("CO", "CM").contains(timeOffType)) {
            errors.add("Invalid time-off type: " + timeOffType);
        }

        // Exit early if basic validation fails
        if (!errors.isEmpty()) {
            return new ValidationResult(false, errors, 0);
        }

        // Validate date range
        if (endDate.isBefore(startDate)) {
            errors.add("End date must be after start date");
            return new ValidationResult(false, errors, 0);
        }

        // Get standardized date from validation service
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);

        LocalDate today = timeValues.getCurrentDate();
        LocalDate maxAllowedDate = today.plusMonths(6);
        LocalDate retroactiveCutoff = today.minusMonths(1);  // Allow up to 1 month back

        // Validate future and past date constraints
        if (startDate.isBefore(retroactiveCutoff)) {
            errors.add("Cannot request time off more than a month in the past");
        }

        if (startDate.isAfter(maxAllowedDate)) {
            errors.add("Cannot request time off more than 6 months in advance");
        }

        if (!errors.isEmpty()) {
            return new ValidationResult(false, errors, 0);
        }

        // Calculate eligible workdays
        int eligibleDays = calculateEligibleDays(startDate, endDate);

        // Validate paid leave availability for CO type
        if ("CO".equals(timeOffType) && eligibleDays > availableDays) {
            errors.add(String.format("Insufficient paid holiday days. Needed: %d, Available: %d", eligibleDays, availableDays));
            return new ValidationResult(false, errors, eligibleDays);
        }

        return new ValidationResult(true, errors, eligibleDays);
    }

    /**
     * Calculates the number of eligible workdays between two dates, excluding weekends and holidays.
     */
    private int calculateEligibleDays(LocalDate startDate, LocalDate endDate) {
        return CalculateWorkHoursUtil.calculateWorkDays(startDate, endDate, worktimeManagementService);
    }

    /**
     * Result class for validation results.
     */
    @Getter
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final int eligibleDays;

        public ValidationResult(boolean valid, List<String> errors, int eligibleDays) {
            this.valid = valid;
            this.errors = errors;
            this.eligibleDays = eligibleDays;
        }

        /**
         * Returns the first error message, or empty string if no errors.
         */
        public String getErrorMessage() {
            if (errors == null || errors.isEmpty()) {
                return "";
            }
            return errors.get(0);
        }
    }
}