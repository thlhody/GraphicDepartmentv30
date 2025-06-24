package com.ctgraphdep.validation.commands;

import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeProvider;
import lombok.Getter;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * CENTRALIZED: Validate User Edit Date Command
 * Eliminates code duplication across all validation points.
 * Business Rules:
 * - Current day: Cannot edit (session monitoring active)
 * - Future dates: Only within current month (1-31 days)
 * - Past dates: Current month + last 7 days of previous month
 * - SN timeOffType: Admin only (users cannot edit)
 * - SN start/end times: Users CAN edit (forgot to register work)
 */
public class ValidateUserEditDateCommand extends BaseTimeValidationCommand<ValidateUserEditDateCommand.ValidationResult> {

    private final LocalDate date;
    private final String field;           // Optional: for field-specific validation
    private final String existingTimeOffType; // Optional: for SN special handling
    private final boolean isAdminUser;

    public ValidateUserEditDateCommand(LocalDate date, TimeProvider timeProvider) {
        this(date, null, null, false, timeProvider);
    }

    public ValidateUserEditDateCommand(LocalDate date, String field, TimeProvider timeProvider) {
        this(date, field, null, false, timeProvider);
    }

    public ValidateUserEditDateCommand(LocalDate date, String field, String existingTimeOffType,
                                       boolean isAdminUser, TimeProvider timeProvider) {
        super(timeProvider);
        this.date = date;
        this.field = field;
        this.existingTimeOffType = existingTimeOffType;
        this.isAdminUser = isAdminUser;
    }

    @Override
    public ValidationResult execute() {
        if (date == null) {
            return ValidationResult.invalid("Date cannot be null");
        }

        try {
            // Use GetStandardTimeValuesCommand for consistent time logic
            GetStandardTimeValuesCommand timeCommand = new GetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeCommand.execute();
            LocalDate today = timeValues.getCurrentDate();

            debug(String.format("Validating edit date %s (field: %s, timeOff: %s, isAdmin: %s)",
                    date, field, existingTimeOffType, isAdminUser));

            // RULE 1: Cannot edit current day (session monitoring active)
            if (date.equals(today)) {
                return ValidationResult.invalid("Cannot edit current day - session monitoring is active");
            }

            // RULE 2: SN special field validation
            if ("SN".equals(existingTimeOffType)) {
                if ("timeOff".equals(field)) {
                    // SN timeOffType: Only admin can edit
                    if (!isAdminUser) {
                        return ValidationResult.invalid("Only admin can edit SN timeoff type");
                    }
                } else if ("startTime".equals(field) || "endTime".equals(field)) {
                    // SN start/end times: Users CAN edit (forgot to register work)
                    debug(String.format("Allowing SN %s edit for %s (user can register forgotten work)", field, date));
                    // Continue with normal date range validation below
                }
            }

            // RULE 3: Admin users have more permissive rules
            if (isAdminUser) {
                // Admin can edit wider date ranges - implement if needed
                debug("Admin user - using standard date validation");
            }

            // RULE 4: Future dates - only within current month
            if (date.isAfter(today)) {
                return validateFutureDate(date, today);
            }

            // RULE 5: Past dates - current month + last 7 days of previous month
            if (date.isBefore(today)) {
                return validatePastDate(date, today);
            }

            // Should not reach here, but just in case
            return ValidationResult.valid();

        } catch (Exception e) {
            error("Error validating edit date: " + e.getMessage(), e);
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validate future date (only current month allowed)
     */
    private ValidationResult validateFutureDate(LocalDate date, LocalDate today) {
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth dateMonth = YearMonth.from(date);

        if (!dateMonth.equals(currentMonth)) {
            return ValidationResult.invalid(String.format(
                    "Can only edit future dates within current month (%s). Date %s is in %s",
                    currentMonth, date, dateMonth));
        }

        debug(String.format("Valid future date within current month: %s", date));
        return ValidationResult.valid();
    }

    /**
     * Validate past date (current month + last 7 days of previous month)
     */
    private ValidationResult validatePastDate(LocalDate date, LocalDate today) {
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth dateMonth = YearMonth.from(date);

        // Past dates in current month are always editable
        if (dateMonth.equals(currentMonth)) {
            debug(String.format("Valid past date in current month: %s", date));
            return ValidationResult.valid();
        }

        // Check if it's in the previous month
        YearMonth previousMonth = currentMonth.minusMonths(1);
        if (dateMonth.equals(previousMonth)) {
            // Only last 7 days of previous month are editable
            LocalDate lastDayOfPrevMonth = previousMonth.atEndOfMonth();
            LocalDate cutoffDate = lastDayOfPrevMonth.minusDays(6); // Last 7 days inclusive

            if (date.isBefore(cutoffDate)) {
                return ValidationResult.invalid(String.format(
                        "Can only edit last 7 days of previous month (%s to %s). Date %s is too old",
                        cutoffDate, lastDayOfPrevMonth, date));
            }

            debug(String.format("Valid past date within last 7 days of previous month: %s", date));
            return ValidationResult.valid();
        }

        // Date is in some other month - too old
        return ValidationResult.invalid(String.format(
                "Can only edit dates from current month (%s) or last 7 days of previous month (%s). Date %s is from %s",
                currentMonth, previousMonth, date, dateMonth));
    }

    /**
     * Validation result with success flag and reason
     */
    @Getter
    public static class ValidationResult {
        private final boolean canEdit;
        private final String reason;
        private final ValidationContext context;

        private ValidationResult(boolean canEdit, String reason) {
            this.canEdit = canEdit;
            this.reason = reason;
            this.context = new ValidationContext();
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }

        public boolean isValid() {
            return canEdit;
        }

        public boolean isInvalid() {
            return !canEdit;
        }
    }

    /**
     * Additional context for validation decisions
     */
    @Getter
    public static class ValidationContext {
        private boolean isCurrentDay = false;
        private boolean isFutureDate = false;
        private boolean isPastDate = false;
        private boolean isWeekend = false;
        private boolean isSNField = false;

        // Can be extended with more context as needed
    }
}