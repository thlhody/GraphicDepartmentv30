package com.ctgraphdep.config;

import java.nio.file.Path;
import java.util.Map;

/**
 * REFACTORED: Centralized constants for file types, filename prefixes, and backup criticality levels.
 * This class serves as the single source of truth for:
 * - Mapping between logical file type names (used in UI/API) and actual filename prefixes
 * - Backup criticality levels for different file types
 * - File type detection and validation utilities
 * Key Improvements:
 * - CriticalityLevel enum moved here from BackupService (better logical grouping)
 * - Direct enum usage eliminates string conversion overhead
 * - All file type logic centralized in one place
 * - Type-safe criticality level determination
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

    // ===== CRITICALITY LEVEL ENUM (moved from BackupService) =====
    /**
     * Criticality levels for different types of files, determining backup strategy.
     */
    public enum CriticalityLevel {
        /**
         * Low criticality - Status files, temporary files, cache
         * Backup Strategy: Simple .bak file next to original, no historical rotation
         * Max Backups: 1
         */
        LEVEL1_LOW,

        /**
         * Medium criticality - Session files, configuration files
         * Backup Strategy: Timestamped backups + simple .bak, limited rotation
         * Max Backups: 5
         */
        LEVEL2_MEDIUM,

        /**
         * High criticality - Business data (worktime, registers, timeoff, admin data)
         * Backup Strategy: Comprehensive timestamped backups + simple .bak, full rotation
         * Max Backups: 10
         */
        LEVEL3_HIGH
    }

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

    // ===== CRITICALITY LEVEL MAPPING (now using enum directly) =====
    private static final Map<String, CriticalityLevel> TYPE_TO_CRITICALITY_MAP = Map.ofEntries(
            // Business Critical Data - LEVEL3_HIGH (10 backups)
            Map.entry(WORKTIME_TARGET, CriticalityLevel.LEVEL3_HIGH),
            Map.entry(REGISTER_TARGET, CriticalityLevel.LEVEL3_HIGH),
            Map.entry(CHECK_REGISTER_TARGET, CriticalityLevel.LEVEL3_HIGH),
            Map.entry(TIMEOFF_TRACKER_TARGET, CriticalityLevel.LEVEL1_LOW),
            Map.entry(ADMIN_WORKTIME_TARGET, CriticalityLevel.LEVEL1_LOW),
            Map.entry(ADMIN_REGISTER_TARGET, CriticalityLevel.LEVEL1_LOW),
            Map.entry(ADMIN_BONUS_TARGET, CriticalityLevel.LEVEL1_LOW),


            // Session Data - LEVEL2_MEDIUM (5 backups)
            Map.entry(LEAD_CHECK_REGISTER_TARGET, CriticalityLevel.LEVEL2_MEDIUM),
            Map.entry(LEAD_CHECK_BONUS_TARGET, CriticalityLevel.LEVEL2_MEDIUM),
            Map.entry(ADMIN_CHECK_BONUS_TARGET, CriticalityLevel.LEVEL2_MEDIUM),
            Map.entry(SESSION_TARGET, CriticalityLevel.LEVEL2_MEDIUM)

            // Note: LEVEL1_LOW files (status, temp, cache) are detected by path/filename patterns
    );

    // ===== PREFIX TO LOGICAL TYPE REVERSE MAPPING (for efficient lookup) =====
    private static final Map<String, String> PREFIX_TO_TYPE_MAP = Map.ofEntries(
            Map.entry(WORKTIME_PREFIX, WORKTIME_TARGET),
            Map.entry(REGISTER_PREFIX, REGISTER_TARGET),
            Map.entry(SESSION_PREFIX, SESSION_TARGET),
            Map.entry(CHECK_REGISTER_PREFIX, CHECK_REGISTER_TARGET),
            Map.entry(TIMEOFF_TRACKER_PREFIX, TIMEOFF_TRACKER_TARGET),
            Map.entry(ADMIN_WORKTIME_PREFIX, ADMIN_WORKTIME_TARGET),
            Map.entry(ADMIN_REGISTER_PREFIX, ADMIN_REGISTER_TARGET),
            Map.entry(ADMIN_BONUS_PREFIX, ADMIN_BONUS_TARGET),
            Map.entry(LEAD_CHECK_REGISTER_PREFIX, LEAD_CHECK_REGISTER_TARGET),
            Map.entry(LEAD_CHECK_BONUS_PREFIX, LEAD_CHECK_BONUS_TARGET),
            Map.entry(ADMIN_CHECK_BONUS_PREFIX, ADMIN_CHECK_BONUS_TARGET)
    );

    // ===== PRIMARY API METHODS =====

    /**
     * Gets the actual filename prefix for a logical file type.
     * @param fileType The logical file type (e.g., "register")
     * @return The actual filename prefix (e.g., "registru")
     */
    public static String getFilenamePrefix(String fileType) {
        return TYPE_TO_PREFIX_MAP.getOrDefault(fileType, fileType);
    }

    /**
     * Gets the criticality level for a logical file type.
     * @param fileType The logical file type (e.g., "worktime")
     * @return The criticality level enum
     */
    public static CriticalityLevel getCriticalityLevel(String fileType) {
        return TYPE_TO_CRITICALITY_MAP.getOrDefault(fileType, CriticalityLevel.LEVEL2_MEDIUM);
    }

    /**
     * PRIMARY METHOD: Gets criticality level directly from filename.
     * This is the main method used by backup services to determine backup strategy.
     *
     * @param filename The filename to analyze (e.g., "registru_user_5_2025_01.json")
     * @return The criticality level enum for backup strategy
     */
    public static CriticalityLevel getCriticalityLevelForFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return CriticalityLevel.LEVEL2_MEDIUM;
        }

        // First try extracting logical file type from filename
        String fileType = extractFileTypeFromFilename(filename);
        if (fileType != null) {
            return getCriticalityLevel(fileType);
        }

        // Fallback: Check for special patterns that indicate low criticality
        String lowerFilename = filename.toLowerCase();
        if (isLowCriticalityFile(lowerFilename)) {
            return CriticalityLevel.LEVEL1_LOW;
        }

        // Default to medium criticality for unknown files
        return CriticalityLevel.LEVEL2_MEDIUM;
    }

    /**
     * Enhanced method that analyzes full path for criticality determination.
     * Useful when filename analysis isn't sufficient.
     *
     * @param filePath The complete file path
     * @return The criticality level enum
     */
    public static CriticalityLevel getCriticalityLevelForPath(Path filePath) {
        if (filePath == null) {
            return CriticalityLevel.LEVEL2_MEDIUM;
        }

        String fileName = filePath.getFileName().toString();

        // First try filename-based detection
        CriticalityLevel level = getCriticalityLevelForFilename(fileName);

        // If we got a specific result (not default), return it
        if (level != CriticalityLevel.LEVEL2_MEDIUM) {
            return level;
        }

        // For medium results, check if path indicates low criticality
        String pathStr = filePath.toString().toLowerCase();
        if (isLowCriticalityPath(pathStr)) {
            return CriticalityLevel.LEVEL1_LOW;
        }

        return level;
    }

    // ===== FILE TYPE DETECTION METHODS =====

    /**
     * Extracts the logical file type from a filename.
     * @param filename The filename (e.g., "registru_user_5_2025_01.json")
     * @return The logical file type (e.g., "register") or null if unknown
     */
    public static String extractFileTypeFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }

        String lowerFilename = filename.toLowerCase();

        // Check each known prefix to find a match
        for (Map.Entry<String, String> entry : PREFIX_TO_TYPE_MAP.entrySet()) {
            String prefix = entry.getKey().toLowerCase();
            String logicalType = entry.getValue();

            // Check if filename starts with this prefix followed by underscore
            if (lowerFilename.startsWith(prefix + "_")) {
                return logicalType;
            }
        }

        return null; // Unknown file type
    }

    /**
     * Checks if a filename corresponds to any known file type.
     * @param filename The filename to check
     * @return true if it matches a known pattern
     */
    public static boolean isKnownFileType(String filename) {
        return extractFileTypeFromFilename(filename) != null;
    }

    /**
     * Checks if a filename indicates a low criticality file.
     * @param lowerFilename The filename in lowercase
     * @return true if it's a low criticality file
     */
    private static boolean isLowCriticalityFile(String lowerFilename) {
        return lowerFilename.contains("status") ||
                lowerFilename.startsWith("status_") ||
                lowerFilename.contains("temp") ||
                lowerFilename.contains("cache") ||
                lowerFilename.contains("log") ||
                lowerFilename.endsWith(".tmp");
    }

    /**
     * Checks if a file path indicates low criticality based on directory structure.
     * @param lowerPath The path in lowercase
     * @return true if it's in a low criticality location
     */
    private static boolean isLowCriticalityPath(String lowerPath) {
        return lowerPath.contains("/status/") ||
                lowerPath.contains("\\status\\") ||
                lowerPath.contains("/temp/") ||
                lowerPath.contains("\\temp\\") ||
                lowerPath.contains("/cache/") ||
                lowerPath.contains("\\cache\\") ||
                lowerPath.contains("/logs/") ||
                lowerPath.contains("\\logs\\");
    }

    // ===== VALIDATION METHODS =====

    /**
     * Checks if a file has a backup extension.
     * @param filename The filename to check
     * @return true if it's a backup file
     */
    public static boolean isBackupFile(String filename) {
        return filename != null && filename.toLowerCase().endsWith(BACKUP_EXTENSION);
    }

    /**
     * Checks if a file has a JSON extension.
     * @param filename The filename to check
     * @return true if it's a JSON file
     */
    public static boolean isJsonFile(String filename) {
        return filename != null && filename.toLowerCase().endsWith(JSON_EXTENSION);
    }

    /**
     * Gets a human-readable description of a criticality level.
     * @param level The criticality level
     * @return Description of backup strategy
     */
    public static String getCriticalityDescription(CriticalityLevel level) {
        return switch (level) {
            case LEVEL1_LOW -> "Low priority - Simple backup only (1 file)";
            case LEVEL2_MEDIUM -> "Medium priority - Limited historical backups (5 files)";
            case LEVEL3_HIGH -> "High priority - Comprehensive backups (10 files)";
        };
    }

    /**
     * Gets the maximum number of backups for a criticality level.
     * @param level The criticality level
     * @return Maximum backup count
     */
    public static int getMaxBackups(CriticalityLevel level) {
        return switch (level) {
            case LEVEL1_LOW -> 1;
            case LEVEL2_MEDIUM -> 5;
            case LEVEL3_HIGH -> 10;
        };
    }

    // ===== DEBUGGING AND DIAGNOSTICS =====

    /**
     * Gets diagnostic information about file type detection.
     * Useful for troubleshooting backup issues.
     *
     * @param filename The filename to analyze
     * @return Diagnostic string with detection details
     */
    public static String getFileTypeDiagnostics(String filename) {
        if (filename == null) {
            return "Filename is null";
        }

        StringBuilder diag = new StringBuilder();
        diag.append("File: ").append(filename).append("\n");

        String fileType = extractFileTypeFromFilename(filename);
        diag.append("Detected Type: ").append(fileType != null ? fileType : "UNKNOWN").append("\n");

        CriticalityLevel level = getCriticalityLevelForFilename(filename);
        diag.append("Criticality: ").append(level).append("\n");
        diag.append("Max Backups: ").append(getMaxBackups(level)).append("\n");
        diag.append("Strategy: ").append(getCriticalityDescription(level)).append("\n");

        if (fileType != null) {
            diag.append("Prefix: ").append(getFilenamePrefix(fileType)).append("\n");
        }

        return diag.toString();
    }

    // Prevent instantiation
    private FileTypeConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}