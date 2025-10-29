package com.ctgraphdep.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CENTRALIZED REGISTRY FOR ALL TIME-OFF TYPE LOGIC
 *
 * This class is the SINGLE SOURCE OF TRUTH for time-off type definitions.
 * When adding a new time-off type, ONLY update this class - all other code
 * references this registry for validation, patterns, and business logic.
 *
 * Key Features:
 * - Defines which types support work hours (e.g., SN:5, CO:6)
 * - Defines which types are plain time-off only (e.g., D, CN, CR)
 * - Provides regex patterns for validation
 * - Provides formatted strings for error messages
 *
 * Usage:
 * - AdminUpdateCommand uses this for validation patterns
 * - TimeValidationService uses this for input validation
 * - WorktimeEntityBuilder uses this to determine special day processing
 * - Frontend JavaScript uses this via REST endpoint (future enhancement)
 */
public final class TimeOffTypeRegistry {

    // ========================================================================
    // SPECIAL DAY TYPES (Support work hours: TYPE:X format, e.g., SN:5, CE:6)
    // All work on these days becomes holiday overtime
    // ========================================================================

    /**
     * Time-off types that support work hours (TYPE:HOURS format).
     * These are "special days" where all work becomes holiday overtime.
     *
     * TO ADD A NEW SPECIAL DAY TYPE:
     * 1. Add the WorkCode constant to this array
     * 2. That's it! All validation patterns update automatically.
     */
    private static final String[] SPECIAL_DAY_TYPES_WITH_WORK = {
        WorkCode.NATIONAL_HOLIDAY_CODE,    // SN - National Holiday
        WorkCode.TIME_OFF_CODE,            // CO - Vacation
        WorkCode.MEDICAL_LEAVE_CODE,       // CM - Medical Leave
        WorkCode.WEEKEND_CODE,             // W - Weekend
        WorkCode.SPECIAL_EVENT_CODE        // CE - Special Event (marriage/birth/death)
    };

    // ========================================================================
    // PLAIN TIME-OFF TYPES (No work hours allowed, only plain type, e.g., D, CN, CR)
    // These are full-day absences or special work arrangements
    // ========================================================================

    /**
     * Time-off types that DO NOT support work hours (plain type only).
     * These represent full-day absences or special work arrangements.
     *
     * TO ADD A NEW PLAIN TIME-OFF TYPE:
     * 1. Add the WorkCode constant to this array
     * 2. That's it! All validation patterns update automatically.
     */
    private static final String[] PLAIN_TIME_OFF_ONLY = {
        WorkCode.DELEGATION_CODE,          // D - Delegation/Business Trip
        WorkCode.UNPAID_LEAVE_CODE,        // CN - Unpaid Leave
        WorkCode.RECOVERY_LEAVE_CODE       // CR - Recovery Leave (paid from overtime)
    };

    // ========================================================================
    // IMMUTABLE SETS (Thread-safe, optimized for lookups)
    // ========================================================================

    private static final Set<String> SPECIAL_DAY_SET = Collections.unmodifiableSet(
        Arrays.stream(SPECIAL_DAY_TYPES_WITH_WORK).collect(Collectors.toSet())
    );

    private static final Set<String> PLAIN_TIME_OFF_SET = Collections.unmodifiableSet(
        Arrays.stream(PLAIN_TIME_OFF_ONLY).collect(Collectors.toSet())
    );

    // Combined set of ALL time-off types (special days + plain)
    private static final Set<String> ALL_TIME_OFF_TYPES;
    static {
        Set<String> combined = Arrays.stream(SPECIAL_DAY_TYPES_WITH_WORK)
            .collect(Collectors.toSet());
        combined.addAll(PLAIN_TIME_OFF_SET);
        ALL_TIME_OFF_TYPES = Collections.unmodifiableSet(combined);
    }

    // ========================================================================
    // REGEX PATTERNS (Auto-generated from arrays above)
    // ========================================================================

    /**
     * Regex pattern for special day work format: (SN|CO|CM|W|CE):\d+
     * Auto-generated from SPECIAL_DAY_TYPES_WITH_WORK array.
     */
    private static final String SPECIAL_DAY_WORK_PATTERN =
        "^(" + String.join("|", SPECIAL_DAY_TYPES_WITH_WORK) + "):\\d+(\\.\\d+)?$";

    /**
     * Regex pattern for special day types only: (SN|CO|CM|W|CE)
     * Auto-generated from SPECIAL_DAY_TYPES_WITH_WORK array.
     */
    private static final String SPECIAL_DAY_TYPES_PATTERN =
        "^(" + String.join("|", SPECIAL_DAY_TYPES_WITH_WORK) + ")$";

    /**
     * Regex pattern for plain time-off types only: (D|CN|CR)
     * Auto-generated from PLAIN_TIME_OFF_ONLY array.
     */
    private static final String PLAIN_TIME_OFF_PATTERN =
        "^(" + String.join("|", PLAIN_TIME_OFF_ONLY) + ")$";

    /**
     * Regex pattern for ALL time-off types: (SN|CO|CM|W|CE|D|CN|CR)
     * Auto-generated from both arrays combined.
     */
    private static final String ALL_TIME_OFF_PATTERN =
        "^(" + String.join("|", ALL_TIME_OFF_TYPES) + ")$";

    // ========================================================================
    // PUBLIC API - Validation Methods
    // ========================================================================

    /**
     * Check if value matches special day work format (TYPE:HOURS).
     * Examples: SN:5, CO:6, CE:8
     */
    public static boolean isSpecialDayWorkFormat(String value) {
        return value != null && value.matches(SPECIAL_DAY_WORK_PATTERN);
    }

    /**
     * Check if type is a special day type (supports work hours).
     * Examples: SN, CO, CM, W, CE
     */
    public static boolean isSpecialDayType(String type) {
        return type != null && SPECIAL_DAY_SET.contains(type.toUpperCase());
    }

    /**
     * Check if type is a plain time-off type (no work hours allowed).
     * Examples: D, CN, CR
     */
    public static boolean isPlainTimeOffType(String type) {
        return type != null && PLAIN_TIME_OFF_SET.contains(type.toUpperCase());
    }

    /**
     * Check if type is ANY valid time-off type.
     * Examples: SN, CO, CM, W, CE, D, CN, CR
     */
    public static boolean isValidTimeOffType(String type) {
        return type != null && ALL_TIME_OFF_TYPES.contains(type.toUpperCase());
    }

    // ========================================================================
    // PUBLIC API - Pattern Access
    // ========================================================================

    /**
     * Get regex pattern for special day work format validation.
     * Use this for .matches() calls: value.matches(getSpecialDayWorkPattern())
     */
    public static String getSpecialDayWorkPattern() {
        return SPECIAL_DAY_WORK_PATTERN;
    }

    /**
     * Get regex pattern for special day types validation.
     * Use this for .matches() calls: type.matches(getSpecialDayTypesPattern())
     */
    public static String getSpecialDayTypesPattern() {
        return SPECIAL_DAY_TYPES_PATTERN;
    }

    /**
     * Get regex pattern for plain time-off types validation.
     * Use this for .matches() calls: type.matches(getPlainTimeOffPattern())
     */
    public static String getPlainTimeOffPattern() {
        return PLAIN_TIME_OFF_PATTERN;
    }

    /**
     * Get regex pattern for ALL time-off types validation.
     * Use this for .matches() calls: type.matches(getAllTimeOffPattern())
     */
    public static String getAllTimeOffPattern() {
        return ALL_TIME_OFF_PATTERN;
    }

    // ========================================================================
    // PUBLIC API - Display Strings (for error messages)
    // ========================================================================

    /**
     * Get comma-separated list of special day types for display.
     * Example: "SN, CO, CM, W, CE"
     */
    public static String getSpecialDayTypesDisplay() {
        return String.join(", ", SPECIAL_DAY_TYPES_WITH_WORK);
    }

    /**
     * Get comma-separated list of plain time-off types for display.
     * Example: "D, CN, CR"
     */
    public static String getPlainTimeOffTypesDisplay() {
        return String.join(", ", PLAIN_TIME_OFF_ONLY);
    }

    /**
     * Get comma-separated list of ALL time-off types for display.
     * Example: "SN, CO, CM, W, CE, D, CN, CR"
     */
    public static String getAllTimeOffTypesDisplay() {
        return String.join(", ", ALL_TIME_OFF_TYPES);
    }

    /**
     * Get example format strings for special day work.
     * Example: "SN:5, CO:6, CM:4, W:8, CE:5"
     */
    public static String getSpecialDayWorkExamples() {
        return Arrays.stream(SPECIAL_DAY_TYPES_WITH_WORK)
            .map(type -> type + ":5")
            .collect(Collectors.joining(", "));
    }

    // ========================================================================
    // PUBLIC API - Collections
    // ========================================================================

    /**
     * Get immutable list of special day types.
     */
    public static List<String> getSpecialDayTypes() {
        return Arrays.asList(SPECIAL_DAY_TYPES_WITH_WORK);
    }

    /**
     * Get immutable list of plain time-off types.
     */
    public static List<String> getPlainTimeOffTypes() {
        return Arrays.asList(PLAIN_TIME_OFF_ONLY);
    }

    /**
     * Get immutable set of ALL time-off types.
     */
    public static Set<String> getAllTimeOffTypes() {
        return ALL_TIME_OFF_TYPES;
    }

    // ========================================================================
    // Prevent Instantiation
    // ========================================================================

    private TimeOffTypeRegistry() {
        throw new UnsupportedOperationException("TimeOffTypeRegistry is a utility class and cannot be instantiated");
    }
}
