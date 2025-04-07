package com.ctgraphdep.utils;

import com.ctgraphdep.model.WorkUsersSessionsStates;

import java.time.LocalDateTime;

/**
 * Utility class for common validation logic used across commands.
 */
public final class ValidationUtil {

    private ValidationUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Validates that a session is not null.
     *
     * @param session The session to validate
     * @param paramName The parameter name for error messages
     * @throws IllegalArgumentException if session is null
     */
    public static void validateSessionNotNull(WorkUsersSessionsStates session, String paramName) {
        if (session == null) {
            throw new IllegalArgumentException(paramName + " cannot be null");
        }
    }

    /**
     * Validates that a date/time is not null.
     *
     * @param dateTime The date/time to validate
     * @param paramName The parameter name for error messages
     * @throws IllegalArgumentException if date/time is null
     */
    public static void validateDateTimeNotNull(LocalDateTime dateTime, String paramName) {
        if (dateTime == null) {
            throw new IllegalArgumentException(paramName + " cannot be null");
        }
    }

    /**
     * Validates that a number is positive.
     *
     * @param value The value to validate
     * @param paramName The parameter name for error messages
     * @throws IllegalArgumentException if value is not positive
     */
    public static void validatePositive(int value, String paramName) {
        if (value <= 0) {
            throw new IllegalArgumentException(paramName + " must be positive");
        }
    }

    /**
     * Validates that a string is not null or empty.
     *
     * @param value The string to validate
     * @param paramName The parameter name for error messages
     * @throws IllegalArgumentException if string is null or empty
     */
    public static void validateNotEmpty(String value, String paramName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(paramName + " cannot be null or empty");
        }
    }
}