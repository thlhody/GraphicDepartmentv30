package com.ctgraphdep.service.result;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Specialized validation result for accumulating multiple validation errors.
 * Useful for validating forms or complex objects with multiple fields.
 * Usage:
 * ValidationServiceResult validation = ValidationServiceResult.create()
 *     .validate(() -> entry.getDate() != null, "Date is required", "missing_date")
 *     .validate(() -> entry.getOmsId() != null, "OMS ID is required", "missing_oms_id")
 *     .validate(() -> entry.getDesignerName() != null, "Designer name is required", "missing_designer_name");
 * if (validation.hasErrors()) {
 *     return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
 * }
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ValidationServiceResult {

    private final List<ValidationError> errors;

    @Getter
    @AllArgsConstructor
    public static class ValidationError {
        private final String message;
        private final String code;
    }

    /**
     * Creates a new ValidationServiceResult
     */
    public static ValidationServiceResult create() {
        return new ValidationServiceResult(new ArrayList<>());
    }

    /**
     * Validates a condition and adds error if condition is false
     */
    public ValidationServiceResult validate(boolean condition, String errorMessage, String errorCode) {
        if (!condition) {
            errors.add(new ValidationError(errorMessage, errorCode));
        }
        return this;
    }

    /**
     * Validates a condition using a supplier (for lazy evaluation)
     */
    public ValidationServiceResult validate(Supplier<Boolean> condition, String errorMessage, String errorCode) {
        try {
            if (!condition.get()) {
                errors.add(new ValidationError(errorMessage, errorCode));
            }
        } catch (Exception e) {
            errors.add(new ValidationError("Validation check failed: " + e.getMessage(), "validation_check_failed"));
        }
        return this;
    }

    /**
     * Validates that a field is not null
     */
    public ValidationServiceResult requireNotNull(Object field, String fieldName, String errorCode) {
        return validate(field != null, fieldName + " is required", errorCode);
    }

    /**
     * Validates that a string is not null or empty
     */
    public ValidationServiceResult requireNotEmpty(String field, String fieldName, String errorCode) {
        return validate(field != null && !field.trim().isEmpty(), fieldName + " is required", errorCode);
    }

    /**
     * Validates that a number is positive
     */
    public ValidationServiceResult requirePositive(Number field, String fieldName, String errorCode) {
        return validate(field != null && field.doubleValue() > 0, fieldName + " must be positive", errorCode);
    }

    /**
     * Returns true if there are validation errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Returns true if validation passed (no errors)
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Returns the first error message, or null if no errors
     */
    public String getFirstError() {
        return errors.isEmpty() ? null : errors.get(0).getMessage();
    }

    /**
     * Returns the first error code, or null if no errors
     */
    public String getFirstErrorCode() {
        return errors.isEmpty() ? null : errors.get(0).getCode();
    }

    /**
     * Returns all error messages as a single string
     */
    public String getAllErrorsAsString() {
        if (errors.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(errors.get(i).getMessage());
        }
        return sb.toString();
    }

    /**
     * Returns all error messages as a list
     */
    public List<String> getAllErrorMessages() {
        return errors.stream()
                .map(ValidationError::getMessage)
                .toList();
    }

    /**
     * Converts to ServiceResult
     */
    public ServiceResult<Void> toServiceResult() {
        if (isValid()) {
            return ServiceResult.success();
        } else {
            return ServiceResult.validationError(getFirstError(), getFirstErrorCode());
        }
    }

    /**
     * Converts to ServiceResult with specific data if valid
     */
    public <T> ServiceResult<T> toServiceResult(T data) {
        if (isValid()) {
            return ServiceResult.success(data);
        } else {
            return ServiceResult.validationError(getFirstError(), getFirstErrorCode());
        }
    }
}