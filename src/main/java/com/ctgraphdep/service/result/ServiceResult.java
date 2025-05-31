package com.ctgraphdep.service.result;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Generic wrapper for service operation results.
 * Provides type-safe error handling without exceptions.
 * Usage Examples:
 * - ServiceResult.success(data)
 * - ServiceResult.failure("Error message", "error_code")
 * - ServiceResult.validationError("Field required", "missing_field")
 * - ServiceResult.systemError("Database unavailable")
 */
@Getter
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ServiceResult<T> {

    private final boolean success;
    private final T data;
    private final String errorMessage;
    private final String errorCode;
    private final ResultType resultType;
    private final List<String> warnings;

    /**
     * Result type enumeration for better error categorization
     */
    public enum ResultType {
        SUCCESS,
        VALIDATION_ERROR,
        BUSINESS_ERROR,
        SYSTEM_ERROR,
        NOT_FOUND,
        UNAUTHORIZED
    }

    // ========================================================================
    // SUCCESS FACTORY METHODS
    // ========================================================================

    /**
     * Creates a successful result with data
     */
    public static <T> ServiceResult<T> success(T data) {
        return new ServiceResult<>(true, data, null, null, ResultType.SUCCESS, new ArrayList<>());
    }

    /**
     * Creates a successful result with data and warnings
     */
    public static <T> ServiceResult<T> successWithWarnings(T data, List<String> warnings) {
        return new ServiceResult<>(true, data, null, null, ResultType.SUCCESS, new ArrayList<>(warnings));
    }

    /**
     * Creates a successful result with data and a single warning
     */
    public static <T> ServiceResult<T> successWithWarning(T data, String warning) {
        List<String> warnings = new ArrayList<>();
        warnings.add(warning);
        return new ServiceResult<>(true, data, null, null, ResultType.SUCCESS, warnings);
    }

    /**
     * Creates a successful result without data (for void operations)
     */
    public static ServiceResult<Void> success() {
        return success(null);
    }

    // ========================================================================
    // ERROR FACTORY METHODS
    // ========================================================================

    /**
     * Creates a validation error result
     */
    public static <T> ServiceResult<T> validationError(String message, String errorCode) {
        return new ServiceResult<>(false, null, message, errorCode, ResultType.VALIDATION_ERROR, new ArrayList<>());
    }

    /**
     * Creates a validation error result without error code
     */
    public static <T> ServiceResult<T> validationError(String message) {
        return validationError(message, "validation_error");
    }

    /**
     * Creates a business logic error result
     */
    public static <T> ServiceResult<T> businessError(String message, String errorCode) {
        return new ServiceResult<>(false, null, message, errorCode, ResultType.BUSINESS_ERROR, new ArrayList<>());
    }

    /**
     * Creates a business logic error result without error code
     */
    public static <T> ServiceResult<T> businessError(String message) {
        return businessError(message, "business_error");
    }

    /**
     * Creates a system error result (for technical failures)
     */
    public static <T> ServiceResult<T> systemError(String message, String errorCode) {
        return new ServiceResult<>(false, null, message, errorCode, ResultType.SYSTEM_ERROR, new ArrayList<>());
    }

    /**
     * Creates a system error result without error code
     */
    public static <T> ServiceResult<T> systemError(String message) {
        return systemError(message, "system_error");
    }

    /**
     * Creates a not found error result
     */
    public static <T> ServiceResult<T> notFound(String message, String errorCode) {
        return new ServiceResult<>(false, null, message, errorCode, ResultType.NOT_FOUND, new ArrayList<>());
    }

    /**
     * Creates a not found error result without error code
     */
    public static <T> ServiceResult<T> notFound(String message) {
        return notFound(message, "not_found");
    }

    /**
     * Creates an unauthorized error result
     */
    public static <T> ServiceResult<T> unauthorized(String message, String errorCode) {
        return new ServiceResult<>(false, null, message, errorCode, ResultType.UNAUTHORIZED, new ArrayList<>());
    }

    /**
     * Creates an unauthorized error result without error code
     */
    public static <T> ServiceResult<T> unauthorized(String message) {
        return unauthorized(message, "unauthorized");
    }

    /**
     * Creates a generic failure result
     */
    public static <T> ServiceResult<T> failure(String message, String errorCode) {
        return businessError(message, errorCode);
    }

    /**
     * Creates a generic failure result without error code
     */
    public static <T> ServiceResult<T> failure(String message) {
        return businessError(message);
    }

    // ========================================================================
    // CONVENIENCE METHODS
    // ========================================================================

    /**
     * Returns data as Optional (empty if error or null data)
     */
    public Optional<T> getDataOptional() {
        return success ? Optional.ofNullable(data) : Optional.empty();
    }

    /**
     * Returns data or default value if error/null
     */
    public T getDataOrDefault(T defaultValue) {
        return success && data != null ? data : defaultValue;
    }

    /**
     * Returns true if operation failed
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * Returns true if result has warnings (regardless of success/failure)
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    /**
     * Returns true if this is a validation error
     */
    public boolean isValidationError() {
        return resultType == ResultType.VALIDATION_ERROR;
    }

    /**
     * Returns true if this is a system error
     */
    public boolean isSystemError() {
        return resultType == ResultType.SYSTEM_ERROR;
    }

    /**
     * Returns true if this is a business error
     */
    public boolean isBusinessError() {
        return resultType == ResultType.BUSINESS_ERROR;
    }

    /**
     * Returns true if this is a not found error
     */
    public boolean isNotFound() {
        return resultType == ResultType.NOT_FOUND;
    }

    /**
     * Returns true if this is an unauthorized error
     */
    public boolean isUnauthorized() {
        return resultType == ResultType.UNAUTHORIZED;
    }

    // ========================================================================
    // FUNCTIONAL METHODS
    // ========================================================================

    /**
     * Execute action if successful
     */
    public ServiceResult<T> ifSuccess(Consumer<T> action) {
        if (success && action != null) {
            action.accept(data);
        }
        return this;
    }

    /**
     * Execute action if failed
     */
    public ServiceResult<T> ifFailure(Consumer<String> action) {
        if (!success && action != null) {
            action.accept(errorMessage);
        }
        return this;
    }

    /**
     * Transform data if successful
     */
    public <U> ServiceResult<U> map(Function<T, U> mapper) {
        if (!success) {
            return new ServiceResult<>(false, null, errorMessage, errorCode, resultType, warnings);
        }

        try {
            U mappedData = mapper.apply(data);
            return new ServiceResult<>(true, mappedData, null, null, ResultType.SUCCESS, warnings);
        } catch (Exception e) {
            return ServiceResult.systemError("Error transforming result: " + e.getMessage());
        }
    }

    /**
     * Chain another service result operation
     */
    public <U> ServiceResult<U> flatMap(Function<T, ServiceResult<U>> mapper) {
        if (!success) {
            return new ServiceResult<>(false, null, errorMessage, errorCode, resultType, warnings);
        }

        try {
            ServiceResult<U> result = mapper.apply(data);
            // Preserve warnings from both results
            if (hasWarnings() && result.hasWarnings()) {
                List<String> combinedWarnings = new ArrayList<>(warnings);
                combinedWarnings.addAll(result.getWarnings());
                return new ServiceResult<>(result.success, result.data, result.errorMessage,
                        result.errorCode, result.resultType, combinedWarnings);
            } else if (hasWarnings()) {
                return new ServiceResult<>(result.success, result.data, result.errorMessage,
                        result.errorCode, result.resultType, warnings);
            }
            return result;
        } catch (Exception e) {
            return ServiceResult.systemError("Error chaining operation: " + e.getMessage());
        }
    }

    // ========================================================================
    // UTILITY METHODS FOR MULTIPLE RESULTS
    // ========================================================================

    /**
     * Combines multiple results into one. If any fail, returns the first failure.
     * If all succeed, returns success with list of all data.
     */
    @SafeVarargs
    public static <T> ServiceResult<List<T>> combine(ServiceResult<T>... results) {
        List<T> allData = new ArrayList<>();
        List<String> allWarnings = new ArrayList<>();

        for (ServiceResult<T> result : results) {
            if (result.isFailure()) {
                return new ServiceResult<>(false, null, result.errorMessage, result.errorCode,
                        result.resultType, result.warnings);
            }

            if (result.data != null) {
                allData.add(result.data);
            }

            if (result.hasWarnings()) {
                allWarnings.addAll(result.warnings);
            }
        }

        return new ServiceResult<>(true, allData, null, null, ResultType.SUCCESS, allWarnings);
    }

    /**
     * Combines multiple results, collecting all errors and successes
     * Returns success with list of successful data, plus warnings about failures
     */
    @SafeVarargs
    public static <T> ServiceResult<List<T>> combinePartial(ServiceResult<T>... results) {
        List<T> successfulData = new ArrayList<>();
        List<String> allWarnings = new ArrayList<>();
        int failureCount = 0;

        for (ServiceResult<T> result : results) {
            if (result.isSuccess()) {
                if (result.data != null) {
                    successfulData.add(result.data);
                }
            } else {
                failureCount++;
                allWarnings.add("Operation failed: " + result.errorMessage);
            }

            if (result.hasWarnings()) {
                allWarnings.addAll(result.warnings);
            }
        }

        if (failureCount > 0) {
            allWarnings.add(0, String.format("%d of %d operations failed", failureCount, results.length));
        }

        return new ServiceResult<>(true, successfulData, null, null, ResultType.SUCCESS, allWarnings);
    }
}