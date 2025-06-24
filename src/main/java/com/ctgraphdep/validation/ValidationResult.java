package com.ctgraphdep.validation;

import lombok.Getter;

/**
 * Standardized validation result for TimeValidationService operations.
 * Provides consistent success/failure handling across all validation scenarios.
 */
@Getter
public class ValidationResult {

    private final boolean valid;
    private final String errorMessage;
    private final String errorCode;

    private ValidationResult(boolean valid, String errorMessage, String errorCode) {
        this.valid = valid;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Create a successful validation result
     */
    public static ValidationResult valid() {
        return new ValidationResult(true, null, null);
    }

    /**
     * Create a failed validation result with error message
     */
    public static ValidationResult invalid(String errorMessage) {
        return new ValidationResult(false, errorMessage, null);
    }

    /**
     * Create a failed validation result with error message and code
     */
    public static ValidationResult invalid(String errorMessage, String errorCode) {
        return new ValidationResult(false, errorMessage, errorCode);
    }

    // ========================================================================
    // CONVENIENCE METHODS
    // ========================================================================

    /**
     * Check if validation failed
     */
    public boolean isInvalid() {
        return !valid;
    }

    /**
     * Get error message or default if none
     */
    public String getErrorMessageOrDefault(String defaultMessage) {
        return errorMessage != null ? errorMessage : defaultMessage;
    }

    /**
     * Check if validation failed with specific error code
     */
    public boolean hasErrorCode(String code) {
        return errorCode != null && errorCode.equals(code);
    }

    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult[VALID]";
        } else {
            return String.format("ValidationResult[INVALID: %s%s]",
                    errorMessage,
                    errorCode != null ? " (" + errorCode + ")" : "");
        }
    }
}