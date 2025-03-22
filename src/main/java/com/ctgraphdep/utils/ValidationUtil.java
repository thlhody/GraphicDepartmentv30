package com.ctgraphdep.utils;

import com.ctgraphdep.model.WorkUsersSessionsStates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

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
     * Validates that a date is not null.
     *
     * @param date The date to validate
     * @param paramName The parameter name for error messages
     * @throws IllegalArgumentException if date is null
     */
    public static void validateDateNotNull(LocalDate date, String paramName) {
        if (date == null) {
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
     * Validates that a collection is not null or empty.
     *
     * @param collection The collection to validate
     * @param paramName The parameter name for error messages
     * @throws IllegalArgumentException if collection is null or empty
     */
    public static void validateNotEmpty(Collection<?> collection, String paramName) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(paramName + " cannot be null or empty");
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
    /**
     * Validates that a map is not null or empty.
     *
     * @param map The map to validate
     * @param paramName The parameter name for error messages
     * @throws IllegalArgumentException if map is null or empty
     */
    public static void validateNotEmptyNotificationTime(Map<?, ?> map, String paramName) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(paramName + " cannot be null or empty");
        }
    }
    /**
     * Validates that an object is not null.
     *
     * @param object The object to validate
     * @param paramName The parameter name for error messages
     * @throws IllegalArgumentException if object is null
     */
    public static void validateNotNull(Object object, String paramName) {
        if (object == null) {
            throw new IllegalArgumentException(paramName + " cannot be null");
        }
    }
}