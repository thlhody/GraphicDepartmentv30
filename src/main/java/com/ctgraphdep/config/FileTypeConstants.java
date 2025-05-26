package com.ctgraphdep.config;

import java.util.Map;

/**
 * Constants for file types and filename prefixes used throughout the application.
 * Provides mapping between logical file type names (used in UI/API) and actual filename prefixes.
 */
public final class FileTypeConstants {

    // ===== FILE EXTENSIONS =====
    public static final String JSON_EXTENSION = ".json";
    public static final String BACKUP_EXTENSION = ".bak";
    public static final String LOG_EXTENSION = ".log";
    public static final String FLAG_EXTENSION = ".flag";

    // ===== LOGICAL FILE TYPE IDENTIFIERS (used in UI, APIs, controllers) =====
    public static final String WORKTIME_TARGET = "worktime";
    public static final String REGISTER_TARGET = "register";
    public static final String SESSION_TARGET = "session";
    public static final String CHECK_REGISTER_TARGET = "check_register";
    public static final String TIMEOFF_TRACKER_TARGET = "timeoff_tracker";
    public static final String ADMIN_WORKTIME_TARGET = "admin_worktime";
    public static final String ADMIN_REGISTER_TARGET = "admin_register";
    public static final String ADMIN_BONUS_TARGET = "admin_bonus";
    public static final String LEAD_CHECK_REGISTER_TARGET = "lead_check_register";
    public static final String LEAD_CHECK_BONUS_TARGET = "lead_check_bonus";
    public static final String ADMIN_CHECK_BONUS_TARGET = "admin_check_bonus";

    // ===== ACTUAL FILENAME PREFIXES (what appears in the files) =====
    public static final String WORKTIME_PREFIX = "worktime";
    public static final String REGISTER_PREFIX = "registru";  // Romanian
    public static final String SESSION_PREFIX = "session";
    public static final String CHECK_REGISTER_PREFIX = "check_registru";  // Romanian
    public static final String TIMEOFF_TRACKER_PREFIX = "timeoff_tracker";
    public static final String ADMIN_WORKTIME_PREFIX = "general_worktime";
    public static final String ADMIN_REGISTER_PREFIX = "admin_registru";  // Romanian
    public static final String ADMIN_BONUS_PREFIX = "admin_bonus";
    public static final String LEAD_CHECK_REGISTER_PREFIX = "lead_check_registru";  // Romanian
    public static final String LEAD_CHECK_BONUS_PREFIX = "lead_check_bonus";
    public static final String ADMIN_CHECK_BONUS_PREFIX = "admin_check_bonus";

    // ===== MAPPING BETWEEN LOGICAL TYPES AND FILENAME PREFIXES =====
    private static final Map<String, String> TYPE_TO_PREFIX_MAP = Map.ofEntries(
            Map.entry(WORKTIME_TARGET, WORKTIME_PREFIX),
            Map.entry(REGISTER_TARGET, REGISTER_PREFIX),
            Map.entry(SESSION_TARGET, SESSION_PREFIX),
            Map.entry(CHECK_REGISTER_TARGET, CHECK_REGISTER_PREFIX),
            Map.entry(TIMEOFF_TRACKER_TARGET, TIMEOFF_TRACKER_PREFIX),
            Map.entry(ADMIN_WORKTIME_TARGET, ADMIN_WORKTIME_PREFIX),
            Map.entry(ADMIN_REGISTER_TARGET, ADMIN_REGISTER_PREFIX),
            Map.entry(ADMIN_BONUS_TARGET, ADMIN_BONUS_PREFIX),
            Map.entry(LEAD_CHECK_REGISTER_TARGET, LEAD_CHECK_REGISTER_PREFIX),
            Map.entry(LEAD_CHECK_BONUS_TARGET, LEAD_CHECK_BONUS_PREFIX),
            Map.entry(ADMIN_CHECK_BONUS_TARGET, ADMIN_CHECK_BONUS_PREFIX)
    );

    // ===== BACKUP CRITICALITY MAPPING =====
    private static final Map<String, String> TYPE_TO_CRITICALITY_MAP = Map.ofEntries(
            Map.entry(WORKTIME_TARGET, "LEVEL3_HIGH"),
            Map.entry(REGISTER_TARGET, "LEVEL3_HIGH"),
            Map.entry(SESSION_TARGET, "LEVEL2_MEDIUM"),
            Map.entry(CHECK_REGISTER_TARGET, "LEVEL3_HIGH"),
            Map.entry(TIMEOFF_TRACKER_TARGET, "LEVEL3_HIGH"),
            Map.entry(ADMIN_WORKTIME_TARGET, "LEVEL3_HIGH"),
            Map.entry(ADMIN_REGISTER_TARGET, "LEVEL3_HIGH"),
            Map.entry(ADMIN_BONUS_TARGET, "LEVEL3_HIGH"),
            Map.entry(LEAD_CHECK_REGISTER_TARGET, "LEVEL3_HIGH"),
            Map.entry(LEAD_CHECK_BONUS_TARGET, "LEVEL3_HIGH"),
            Map.entry(ADMIN_CHECK_BONUS_TARGET, "LEVEL3_HIGH")
    );

    /**
     * Gets the actual filename prefix for a logical file type.
     * @param fileType The logical file type (e.g., "register")
     * @return The actual filename prefix (e.g., "registru")
     */
    public static String getFilenamePrefix(String fileType) {
        return TYPE_TO_PREFIX_MAP.getOrDefault(fileType, fileType);
    }

    /**
     * Gets the criticality level for a file type.
     * @param fileType The logical file type
     * @return The criticality level string
     */
    public static String getCriticalityLevel(String fileType) {
        return TYPE_TO_CRITICALITY_MAP.getOrDefault(fileType, "LEVEL2_MEDIUM");
    }

    /**
     * Checks if a filename starts with any of the known prefixes.
     * @param filename The filename to check
     * @return true if it matches a known pattern
     */
    public static boolean isKnownFileType(String filename) {
        String lowerFilename = filename.toLowerCase();
        return TYPE_TO_PREFIX_MAP.values().stream()
                .anyMatch(prefix -> lowerFilename.startsWith(prefix.toLowerCase()));
    }

    /**
     * Checks if a file has a backup extension.
     * @param filename The filename to check
     * @return true if it's a backup file
     */
    public static boolean isBackupFile(String filename) {
        return filename.toLowerCase().endsWith(BACKUP_EXTENSION);
    }

    /**
     * Checks if a file has a JSON extension.
     * @param filename The filename to check
     * @return true if it's a JSON file
     */
    public static boolean isJsonFile(String filename) {
        return filename.toLowerCase().endsWith(JSON_EXTENSION);
    }

    // Prevent instantiation
    private FileTypeConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}