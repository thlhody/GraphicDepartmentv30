package com.ctgraphdep.config;

/**
 * Configuration for validation-related defaults and policies
 */
public class ValidationConfig {

    /**
     * Default value to return when IsWeekdayCommand fails
     * false is the secure default (disabling access on error)
     */
    public static final boolean DEFAULT_WEEKDAY_ON_ERROR = false;
    /**
     * Default value to return when HasActiveSessionTodayCommand fails
     * false is the secure default (disabling access on error)
     */
    public static final boolean DEFAULT_ACTIVE_SESSION_ON_ERROR = false;
    /**
     * Default value to return when IsWorkingHoursCommand fails
     * false is the secure default (disabling access on error)
     */
    public static final boolean DEFAULT_WORKING_HOURS_ON_ERROR = false;

    // Private constructor to prevent instantiation
    private ValidationConfig() {}
}