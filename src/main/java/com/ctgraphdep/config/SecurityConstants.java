package com.ctgraphdep.config;

/**
 * Central location for role and permission constants
 * to ensure consistency across the application
 */
public final class SecurityConstants {

    // Standard role names without ROLE_ prefix (used in configuration)
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_TEAM_LEADER = "TEAM_LEADER";
    public static final String ROLE_TL_CHECKING = "TL_CHECKING";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_CHECKING = "CHECKING";
    public static final String ROLE_USER_CHECKING = "USER_CHECKING";

    // Full role names with ROLE_ prefix (as stored in Spring Security)
    public static final String SPRING_ROLE_ADMIN = "ROLE_ADMIN";
    public static final String SPRING_ROLE_TEAM_LEADER = "ROLE_TEAM_LEADER";
    public static final String SPRING_ROLE_TL_CHECKING = "ROLE_TL_CHECKING";
    public static final String SPRING_ROLE_USER = "ROLE_USER";
    public static final String SPRING_ROLE_CHECKING = "ROLE_CHECKING";
    public static final String SPRING_ROLE_USER_CHECKING = "ROLE_USER_CHECKING";

    private SecurityConstants() {
        // Prevent instantiation
    }
}
