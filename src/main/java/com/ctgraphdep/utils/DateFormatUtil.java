package com.ctgraphdep.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for centralized date and time formatting.
 * Provides consistent date/time format patterns across the application.
 *
 * This eliminates duplicate DateTimeFormatter instances and ensures
 * consistent formatting throughout the codebase.
 *
 * Usage:
 * - Use the static formatters for direct formatting
 * - Use the format methods for convenience
 */
public class DateFormatUtil {

    // ========================================================================
    // STANDARD FORMATTERS
    // ========================================================================

    /**
     * Standard date format for frontend display: "yyyy-M-d"
     * Example: "2025-1-15"
     */
    public static final DateTimeFormatter FRONTEND_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-M-d");

    /**
     * Standard date format with leading zeros: "yyyy-MM-dd"
     * Example: "2025-01-15"
     */
    public static final DateTimeFormatter ISO_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Display date format with slashes: "dd/MM/yyyy"
     * Example: "15/01/2025"
     */
    public static final DateTimeFormatter DISPLAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Full date format with month name: "MMM dd, yyyy"
     * Example: "Jan 15, 2025"
     */
    public static final DateTimeFormatter FULL_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy");

    // ========================================================================
    // TIME FORMATTERS
    // ========================================================================

    /**
     * Standard time format: "HH:mm"
     * Example: "14:30"
     */
    public static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Time format with seconds: "HH:mm:ss"
     * Example: "14:30:45"
     */
    public static final DateTimeFormatter TIME_WITH_SECONDS_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // ========================================================================
    // DATE-TIME FORMATTERS
    // ========================================================================

    /**
     * Standard date-time format: "yyyy-MM-dd HH:mm:ss"
     * Example: "2025-01-15 14:30:45"
     */
    public static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Display date-time format: "dd/MM/yyyy HH:mm"
     * Example: "15/01/2025 14:30"
     */
    public static final DateTimeFormatter DISPLAY_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Full date-time format: "MMM dd, yyyy HH:mm"
     * Example: "Jan 15, 2025 14:30"
     */
    public static final DateTimeFormatter FULL_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    /**
     * ISO 8601 date-time format
     */
    public static final DateTimeFormatter ISO_DATETIME_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ========================================================================
    // CONVENIENCE METHODS - LocalDate
    // ========================================================================

    /**
     * Format date for frontend: "yyyy-M-d"
     */
    public static String formatForFrontend(LocalDate date) {
        return date != null ? date.format(FRONTEND_DATE_FORMAT) : null;
    }

    /**
     * Format date as ISO: "yyyy-MM-dd"
     */
    public static String formatAsISO(LocalDate date) {
        return date != null ? date.format(ISO_DATE_FORMAT) : null;
    }

    /**
     * Format date for display: "dd/MM/yyyy"
     */
    public static String formatForDisplay(LocalDate date) {
        return date != null ? date.format(DISPLAY_DATE_FORMAT) : null;
    }

    /**
     * Format date with full month name: "MMM dd, yyyy"
     */
    public static String formatFull(LocalDate date) {
        return date != null ? date.format(FULL_DATE_FORMAT) : null;
    }

    // ========================================================================
    // CONVENIENCE METHODS - LocalDateTime
    // ========================================================================

    /**
     * Format time: "HH:mm"
     */
    public static String formatTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(TIME_FORMAT) : null;
    }

    /**
     * Format time with seconds: "HH:mm:ss"
     */
    public static String formatTimeWithSeconds(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(TIME_WITH_SECONDS_FORMAT) : null;
    }

    /**
     * Format date-time: "yyyy-MM-dd HH:mm:ss"
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMAT) : null;
    }

    /**
     * Format date-time for display: "dd/MM/yyyy HH:mm"
     */
    public static String formatDateTimeForDisplay(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DISPLAY_DATETIME_FORMAT) : null;
    }

    /**
     * Format date-time with full month: "MMM dd, yyyy HH:mm"
     */
    public static String formatDateTimeFull(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(FULL_DATETIME_FORMAT) : null;
    }

    /**
     * Format date-time as ISO 8601
     */
    public static String formatDateTimeAsISO(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(ISO_DATETIME_FORMAT) : null;
    }

    // ========================================================================
    // PARSING METHODS
    // ========================================================================

    /**
     * Parse date from frontend format: "yyyy-M-d"
     */
    public static LocalDate parseFrontendDate(String dateString) {
        return dateString != null && !dateString.trim().isEmpty() ?
                LocalDate.parse(dateString, FRONTEND_DATE_FORMAT) : null;
    }

    /**
     * Parse date from ISO format: "yyyy-MM-dd"
     */
    public static LocalDate parseISODate(String dateString) {
        return dateString != null && !dateString.trim().isEmpty() ?
                LocalDate.parse(dateString, ISO_DATE_FORMAT) : null;
    }

    /**
     * Parse date from display format: "dd/MM/yyyy"
     */
    public static LocalDate parseDisplayDate(String dateString) {
        return dateString != null && !dateString.trim().isEmpty() ?
                LocalDate.parse(dateString, DISPLAY_DATE_FORMAT) : null;
    }

    /**
     * Parse date-time from standard format: "yyyy-MM-dd HH:mm:ss"
     */
    public static LocalDateTime parseDateTime(String dateTimeString) {
        return dateTimeString != null && !dateTimeString.trim().isEmpty() ?
                LocalDateTime.parse(dateTimeString, DATETIME_FORMAT) : null;
    }

    /**
     * Parse date-time from display format: "dd/MM/yyyy HH:mm"
     */
    public static LocalDateTime parseDisplayDateTime(String dateTimeString) {
        return dateTimeString != null && !dateTimeString.trim().isEmpty() ?
                LocalDateTime.parse(dateTimeString, DISPLAY_DATETIME_FORMAT) : null;
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Get current date formatted for frontend
     */
    public static String getCurrentDateForFrontend() {
        return formatForFrontend(LocalDate.now());
    }

    /**
     * Get current date formatted for display
     */
    public static String getCurrentDateForDisplay() {
        return formatForDisplay(LocalDate.now());
    }

    /**
     * Get current date-time formatted
     */
    public static String getCurrentDateTime() {
        return formatDateTime(LocalDateTime.now());
    }

    /**
     * Get current date-time formatted for display
     */
    public static String getCurrentDateTimeForDisplay() {
        return formatDateTimeForDisplay(LocalDateTime.now());
    }

    // Private constructor to prevent instantiation
    private DateFormatUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
