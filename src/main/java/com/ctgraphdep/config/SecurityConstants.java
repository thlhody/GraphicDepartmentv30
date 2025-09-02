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
    public static final String ROLE_ROLE = "ROLE_";

    // Full role names with ROLE_ prefix (as stored in Spring Security)
    public static final String SPRING_ROLE_ADMIN = "ROLE_ADMIN";
    public static final String SPRING_ROLE_TEAM_LEADER = "ROLE_TEAM_LEADER";
    public static final String SPRING_ROLE_TL_CHECKING = "ROLE_TL_CHECKING";
    public static final String SPRING_ROLE_USER = "ROLE_USER";
    public static final String SPRING_ROLE_CHECKING = "ROLE_CHECKING";
    public static final String SPRING_ROLE_USER_CHECKING = "ROLE_USER_CHECKING";

    public static final String ADMIN_SIMPLE = "admin";

    //dashboard url's
    public static final String ADMIN_URL = "/admin";
    public static final String TEAM_LEAD_URL = "/team-lead";
    public static final String TL_CHECKING_URL = "/team-checking";
    public static final String USER_CHECKING_URL= "/user-checking";
    public static final String CHECKING_URL = "/checking";
    public static final String USER_URL = "/user";

    private SecurityConstants() {
        throw new UnsupportedOperationException("Security class cannot be instantiated");
    }
}
