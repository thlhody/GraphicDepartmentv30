package com.ctgraphdep.fileOperations.config;

import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.util.FileOperationsUtil;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Configuration for file paths used throughout the application.
 * Enhanced to support the new file operations system while maintaining backwards compatibility.
 */
@Getter
@Setter
@Component
@Configuration
@ConfigurationProperties(prefix = "dbj")
public class PathConfig {

    //App Title
    @Value("${app.title:CTTT}")
    private String appTitle;

    // Base paths
    @Value("${app.paths.network}")
    private String networkBasePath;
    @Value("${app.local}")
    private String localBasePath;

    //Login/Users Path and Format
    @Value("${dbj.login}")
    private String loginPath;
    @Value("${dbj.users.filename}")
    private String usersFilename;
    @Value("${dbj.users.local.filename}")
    private String localUsersFilename;
    @Value("${app.lock.users}")
    private String usersLockFile;

    //Holiday Path and Format
    @Value("${dbj.users.holiday}")
    private String holidayFilename;
    @Value("${app.cache.holiday}")
    private String holidayCacheFile;
    @Value("${app.lock.holiday}")
    private String holidayLockFile;

    //Status Path and Format
    @Value("${dbj.user.status}")
    private String userStatus;
    @Value("${dbj.dir.format.status}")
    private String localStatusFileFormat;
    @Value("${dbj.dir.format.status.flag}")
    private String statusFlagFormat;

    //Notification Path and Format
    @Value("${dbj.notification}")
    private String notificationPath;
    @Value("${app.lock.notification}")
    private String notificationLockFile;
    @Value("${app.local.notification.count}")
    private String notificationLockCountFile;

    //Session Path and Format
    @Value("${dbj.user.session}")
    private String userSession;
    @Value("${dbj.dir.format.session}")
    private String sessionFormat;

    //User Worktime Path and Format
    @Value("${dbj.user.worktime}")
    private String userWorktime;
    @Value("${dbj.dir.format.worktime}")
    private String worktimeFormat;
    //Admin Worktime Path and Format
    @Value("${dbj.dir.format.admin.worktime}")
    private String adminWorktimeFormat;
    @Value("${dbj.admin.worktime}")
    private String adminWorktime;

    //User Time Off Tracker Path and Format
    @Value("${dbj.user.timeoff}")
    private String userTimeoff;
    @Value("${dbj.dir.format.timeoff}")
    private String timeoffFormat;

    //User Register Path and Format
    @Value("${dbj.user.register}")
    private String userRegister;
    @Value("${dbj.dir.format.register}")
    private String registerFormat;
    //Admin Register Path and Format
    @Value("${dbj.admin.register}")
    private String adminRegister;
    @Value("${dbj.dir.format.admin.register}")
    private String adminRegisterFormat;
    @Value("${dbj.dir.format.admin.check.register}")
    private String leadCheckRegisterFormat;

    //User Check Register Path and Format
    @Value("${dbj.user.check.register}")
    private String checkRegister;
    @Value("${dbj.dir.format.check.register}")
    private String checkRegisterFormat;
    //Team Lead Check Register Path and Format
    @Value("${dbj.admin.check.register}")
    private String leadCheckRegister;
    @Value("${dbj.dir.format.lead.check.bonus}")
    private String leadCheckBonusFormat;

    //Admin Bonus Path and Format
    @Value("${dbj.admin.bonus}")
    private String adminBonus;
    @Value("${dbj.dir.format.admin.bonus}")
    private String adminBonusFormat;
    @Value("${dbj.dir.format.admin.check.bonus}")
    private String adminCheckBonusFormat;

    //Team Lead Statistics Format
    @Value("${dbj.dir.format.team}")
    private String teamFileFormat;

    //Logging Path and Format
    @Value("${logging.file.name}")
    private String localLogPath;
    @Value("${app.logs.network}")
    private String networkLogsPath;
    @Value("${app.logs.file.format}")
    private String logFileFormat;
    @Value("${app.logs.path.sync}")
    private String appLogPathFormat;

    // Return the base network path for use by other services
    @Getter
    private Path networkPath;
    private Path localPath;
    private final AtomicBoolean networkAvailable = new AtomicBoolean(false);
    private final AtomicBoolean localAvailable = new AtomicBoolean(false);

    // Cache for FilePath objects for commonly used paths
    private final Map<String, FilePath> filePathCache = new HashMap<>();

    @PostConstruct
    public void init() {
        LoggerUtil.info(this.getClass(), "Raw network path: " + networkBasePath);
        try {
            // Fix network path format - ensure proper UNC path format
            networkBasePath = FileOperationsUtil.normalizeNetworkPath(networkBasePath);
            LoggerUtil.info(this.getClass(), "Using normalized network path: " + networkBasePath);

            // Initialize paths
            networkPath = Paths.get(networkBasePath);
            localPath = Paths.get(localBasePath, appTitle);

            // Create local directories
            initializeLocalDirectories();
            // Verify initial network status
            checkInitialNetworkStatus();

            LoggerUtil.info(this.getClass(), String.format("Initialized paths - Network: %s, Local: %s, Local Available: %b", networkPath, localPath, localAvailable.get()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during initialization: " + e.getMessage());
        }
    }

    /**
     * Perform a quick network check at startup
     * This is not definitive - the NetworkMonitorService will do a more thorough check
     */
    private void checkInitialNetworkStatus() {
        try {
            Path testPath = networkPath;
            boolean exists = Files.exists(testPath) && Files.isDirectory(testPath);
            networkAvailable.set(exists);
            LoggerUtil.info(this.getClass(), "Initial network check: " + (exists ? "AVAILABLE" : "UNAVAILABLE"));
        } catch (Exception e) {
            networkAvailable.set(false);
            LoggerUtil.warn(this.getClass(), "Initial network check failed: " + e.getMessage());
        }
    }

    private void initializeLocalDirectories() {
        try {
            // Ensure local path exists
            if (!Files.exists(localPath)) {
                Files.createDirectories(localPath);
            }

            // Create all required local directories
            List<String> directories = Arrays.asList(
                    loginPath,
                    userSession,
                    userStatus,
                    userWorktime,
                    userRegister,
                    userTimeoff,
                    adminWorktime,
                    adminRegister,
                    adminBonus,
                    notificationPath
            );

            for (String dir : directories) {
                Path dirPath = localPath.resolve(dir);
                Files.createDirectories(dirPath);
            }

            localAvailable.set(true);
            LoggerUtil.info(this.getClass(), "Initialized local directories");
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Failed to initialize local directories: " + e.getMessage());
            localAvailable.set(false);
        }
    }

    // ENHANCED METHODS FOR THE NEW FILE OPERATIONS SYSTEM

    /**
     * Get a local FilePath for a file type and parameters
     * @param fileType The type of file (session, worktime, etc.)
     * @param username The username associated with the file (if applicable)
     * @param userId The user ID (if applicable)
     * @param params Additional parameters for path resolution
     * @return A FilePath object for the requested file
     */
    public FilePath getLocalFilePath(FileType fileType, String username, Integer userId, Map<String, Object> params) {
        Path path = resolveLocalPath(fileType, username, userId, params);
        return FilePath.local(path, username, userId);
    }

    /**
     * Get a network FilePath for a file type and parameters
     * @param fileType The type of file (session, worktime, etc.)
     * @param username The username associated with the file (if applicable)
     * @param userId The user ID (if applicable)
     * @param params Additional parameters for path resolution
     * @return A FilePath object for the requested file
     */
    public FilePath getNetworkFilePath(FileType fileType, String username, Integer userId, Map<String, Object> params) {
        Path path = resolveNetworkPath(fileType, username, userId, params);
        return FilePath.network(path, username, userId);
    }

    /**
     * Convert a local Path to a FilePath object
     * @param localPath The local Path to convert
     * @param username The username to associate (optional)
     * @param userId The user ID to associate (optional)
     * @return A FilePath object representing the local path
     */
    public FilePath toLocalFilePath(Path localPath, String username, Integer userId) {
        return FilePath.local(localPath, username, userId);
    }

    /**
     * Convert a network Path to a FilePath object
     * @param networkPath The network Path to convert
     * @param username The username to associate (optional)
     * @param userId The user ID to associate (optional)
     * @return A FilePath object representing the network path
     */
    public FilePath toNetworkFilePath(Path networkPath, String username, Integer userId) {
        return FilePath.network(networkPath, username, userId);
    }

    /**
     * Resolve a local path based on file type and parameters
     */
    private Path resolveLocalPath(FileType fileType, String username, Integer userId, Map<String, Object> params) {
        return switch (fileType) {
            case SESSION -> getLocalSessionPath(username, userId);
            case WORKTIME -> getLocalWorktimePath(username,
                    getParamOrDefault(params, "year", 0),
                    getParamOrDefault(params, "month", 0));
            case REGISTER -> getLocalRegisterPath(username, userId,
                    getParamOrDefault(params, "year", 0),
                    getParamOrDefault(params, "month", 0));
            case ADMIN_WORKTIME -> getLocalAdminWorktimePath(
                    getParamOrDefault(params, "year", 0),
                    getParamOrDefault(params, "month", 0));
            case ADMIN_REGISTER -> getLocalAdminRegisterPath(username, userId,
                    getParamOrDefault(params, "year", 0),
                    getParamOrDefault(params, "month", 0));
            case CHECK_REGISTER -> getLocalCheckRegisterPath(username, userId,
                    getParamOrDefault(params, "year", 0),
                    getParamOrDefault(params, "month", 0));
            case TIMEOFF_TRACKER -> getLocalTimeOffTrackerPath(username, userId,
                    getParamOrDefault(params, "year", 0));
            case USERS -> getLocalUsersPath();
            case HOLIDAY -> getNetworkHolidayCachePath(); // Local cached version of holiday data
            case STATUS -> getLocalStatusCachePath();
            case TEAM -> getTeamJsonPath(username,
                    getParamOrDefault(params, "year", 0),
                    getParamOrDefault(params, "month", 0));
            case LOG -> getLocalLogPath();
            default -> throw new IllegalArgumentException("Unsupported file type: " + fileType);
        };
    }

    /**
     * Resolve a network path based on file type and parameters
     */
    private Path resolveNetworkPath(FileType fileType, String username, Integer userId, Map<String, Object> params) {
        return switch (fileType) {
            case SESSION -> getNetworkSessionPath(username, userId);
            case WORKTIME -> getNetworkWorktimePath(username,
                    getParamOrDefault(params, "year", 0),
                    getParamOrDefault(params, "month", 0));
            case REGISTER -> getNetworkRegisterPath(username, userId,
                    getParamOrDefault(params, "year", 0),
                    getParamOrDefault(params, "month", 0));
            case ADMIN_WORKTIME -> getNetworkAdminWorktimePath(
                    getParamOrDefault(params, "year", 0),
                    getParamOrDefault(params, "month", 0));
            case ADMIN_REGISTER -> getNetworkAdminRegisterPath(username, userId,
                    getParamOrDefault(params, "year", 0),
                    getParamOrDefault(params, "month", 0));
            case CHECK_REGISTER -> getNetworkCheckRegisterPath(username, userId,
                    getParamOrDefault(params, "year", 0),
                    getParamOrDefault(params, "month", 0));
            case TIMEOFF_TRACKER -> getNetworkTimeOffTrackerPath(username, userId,
                    getParamOrDefault(params, "year", 0));
            case USERS -> getNetworkUsersPath();
            case HOLIDAY -> getNetworkHolidayPath();
            case STATUS -> getNetworkStatusFlagsDirectory();
            case LOG -> getNetworkLogDirectory();
            default -> throw new IllegalArgumentException("Unsupported file type: " + fileType);
        };
    }

    /**
     * Helper method to get a parameter from the map with a default value
     */
    private <T> T getParamOrDefault(Map<String, Object> params, String key, T defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        try {
            @SuppressWarnings("unchecked")
            T typedValue = (T) value;
            return typedValue;
        } catch (ClassCastException e) {
            LoggerUtil.warn(this.getClass(), "Invalid parameter type for key " + key);
            return defaultValue;
        }
    }

    /**
     * Create a parameter map for path resolution
     * @return A new parameter map
     */
    public Map<String, Object> createParams() {
        return new HashMap<>();
    }

    /**
     * Create a parameter map with year and month
     * @param year The year
     * @param month The month
     * @return A parameter map with year and month
     */
    public Map<String, Object> createYearMonthParams(int year, int month) {
        Map<String, Object> params = new HashMap<>();
        params.put("year", year);
        params.put("month", month);
        return params;
    }

    /**
     * Create a parameter map with just a year
     * @param year The year
     * @return A parameter map with year
     */
    public Map<String, Object> createYearParams(int year) {
        Map<String, Object> params = new HashMap<>();
        params.put("year", year);
        return params;
    }

    /**
     * File types used in the application
     */
    public enum FileType {
        SESSION,
        WORKTIME,
        REGISTER,
        ADMIN_WORKTIME,
        ADMIN_REGISTER,
        CHECK_REGISTER,
        LEAD_CHECK_REGISTER,
        TIMEOFF_TRACKER,
        USERS,
        HOLIDAY,
        STATUS,
        TEAM,
        LOG,
        NOTIFICATION
    }

    // LEGACY METHODS - MAINTAINED FOR BACKWARD COMPATIBILITY

    // Network-only paths
    public Path getNetworkUsersPath() {
        return networkPath.resolve(loginPath).resolve(usersFilename);
    }
    public Path getUsersLockPath() {
        return networkPath.resolve(loginPath).resolve(usersLockFile);
    }
    public Path getNetworkHolidayPath() {
        return networkPath.resolve(loginPath).resolve(holidayFilename);
    }
    public Path getNetworkHolidayCachePath() {
        return networkPath.resolve(loginPath).resolve(holidayCacheFile);
    }
    public Path getHolidayLockPath() {
        return networkPath.resolve(loginPath).resolve(holidayLockFile);
    }
    public Path getNetworkStatusFlagsDirectory() {
        return networkPath.resolve(userStatus);
    }
    public Path getNetworkLogDirectory() {
        return networkPath.resolve(networkLogsPath);
    }
    public Path getNetworkLogPath(String username) {
        String formattedLogFilename = String.format(logFileFormat, username);
        return getNetworkLogDirectory().resolve(formattedLogFilename);
    }

    // Local-only paths
    public Path getLocalUsersPath() {
        return localPath.resolve(loginPath).resolve(localUsersFilename);
    }
    public Path getLocalBonusPath(int year, int month) {
        return localPath.resolve(adminBonus).resolve(String.format(adminBonusFormat, year, month));
    }
    public Path getTeamJsonPath(String teamLeadUsername, int year, int month) {
        return localPath.resolve(loginPath).resolve(String.format(teamFileFormat, teamLeadUsername, year, month));
    }
    public Path getNotificationTrackingFilePath(String username, String notificationType) {
        return localPath.resolve(notificationPath).resolve(String.format(notificationLockFile, username, notificationType.toLowerCase()));
    }
    public Path getNotificationCountFilePath(String username, String notificationType) {
        return localPath.resolve(notificationPath).resolve(String.format(notificationLockCountFile, username, notificationType.toLowerCase()));
    }
    public Path getLocalStatusCachePath() {
        return localPath.resolve(userStatus).resolve(localStatusFileFormat);
    }
    public Path getLocalLogPath() {
        Path developmentLogPath = Paths.get(appLogPathFormat).toAbsolutePath();
        if (Files.exists(developmentLogPath)) {
            return developmentLogPath;
        }
        return Paths.get(localLogPath);
    }

    // Session paths - primarily local with network sync
    public Path getLocalSessionPath(String username, Integer userId) {
        return localPath.resolve(userSession)
                .resolve(String.format(sessionFormat, username, userId));
    }
    public Path getNetworkSessionPath(String username, Integer userId) {
        return networkPath.resolve(userSession)
                .resolve(String.format(sessionFormat, username, userId));
    }

    // Worktime paths - local and network for sync
    public Path getLocalWorktimePath(String username, int year, int month) {
        return localPath.resolve(userWorktime).resolve(String.format(worktimeFormat, username, year, month));
    }
    public Path getNetworkWorktimePath(String username, int year, int month) {
        return networkPath.resolve(userWorktime).resolve(String.format(worktimeFormat, username, year, month));
    }

    // Register paths - local and network for sync
    public Path getLocalRegisterPath(String username, Integer userId, int year, int month) {
        return localPath.resolve(userRegister).resolve(String.format(registerFormat, username, userId, year, month));
    }
    public Path getNetworkRegisterPath(String username, Integer userId, int year, int month) {
        return networkPath.resolve(userRegister).resolve(String.format(registerFormat, username, userId, year, month));
    }

    // Admin worktime paths - local and network for sync
    public Path getLocalAdminWorktimePath(int year, int month) {
        return localPath.resolve(adminWorktime).resolve(String.format(adminWorktimeFormat, year, month));
    }
    public Path getNetworkAdminWorktimePath(int year, int month) {
        return networkPath.resolve(adminWorktime).resolve(String.format(adminWorktimeFormat, year, month));
    }

    // Admin register paths - local and network for sync
    public Path getLocalAdminRegisterPath(String username, Integer userId, int year, int month) {
        return localPath.resolve(adminRegister).resolve(String.format(adminRegisterFormat, username, userId, year, month));
    }
    public Path getNetworkAdminRegisterPath(String username, Integer userId, int year, int month) {
        return networkPath.resolve(adminRegister).resolve(String.format(adminRegisterFormat, username, userId, year, month));
    }

    // Check Lead register path - local and network for sync
    public Path getLocalCheckLeadRegisterPath(String username, Integer userId, int year, int month) {
        return localPath.resolve(leadCheckRegister).resolve(String.format(leadCheckRegisterFormat, username, userId, year, month));
    }
    public Path getNetworkCheckLeadRegisterPath(String username, Integer userId, int year, int month) {
        return networkPath.resolve(leadCheckRegister).resolve(String.format(leadCheckRegisterFormat, username, userId, year, month));
    }

    //Check register path - local and network for sync
    public Path getLocalCheckRegisterPath(String username, Integer userId, int year, int month) {
        return localPath.resolve(checkRegister).resolve(String.format(checkRegisterFormat, username, userId, year, month));
    }
    public Path getNetworkCheckRegisterPath(String username, Integer userId, int year, int month) {
        return networkPath.resolve(checkRegister).resolve(String.format(checkRegisterFormat, username, userId, year, month));
    }

    //Check Bonus path - local and network for sync
    public Path getLocalCheckBonusPath(int year, int month) {
        return localPath.resolve(adminBonus).resolve(String.format(adminCheckBonusFormat, year, month));
    }
    public Path getNetworkCheckBonusPath(int year, int month) {
        return networkPath.resolve(adminBonus).resolve(String.format(adminCheckBonusFormat, year, month));
    }

    //Time Off Tracker path - local and network for sync
    public Path getLocalTimeOffTrackerPath(String username, Integer userId, int year) {
        return localPath.resolve(userTimeoff).resolve(String.format(timeoffFormat, username, userId, year));
    }
    public Path getNetworkTimeOffTrackerPath(String username, Integer userId, int year) {
        return networkPath.resolve(userTimeoff).resolve(String.format(timeoffFormat, username, userId, year));
    }

    // Network
    /**
     * Simple getter for network availability
     */
    public boolean isNetworkAvailable() {
        return networkAvailable.get();
    }

    /**
     * Simple setter for network availability - to be called by NetworkMonitorService only
     */
    public void setNetworkAvailable(boolean available) {
        boolean previous = networkAvailable.getAndSet(available);
        if (previous != available) {
            LoggerUtil.info(this.getClass(),
                    String.format("Network status updated to: %s", available ? "Available" : "Unavailable"));
            // Broadcast status change to any listeners
            notifyNetworkStatusChange(available);
        }
    }
    /**
     * Notify any interested components about network status changes
     */
    private void notifyNetworkStatusChange(boolean available) {
        // This could be expanded to use Spring events if needed
        LoggerUtil.info(this.getClass(),
                "==== NETWORK STATUS CHANGED: " + (available ? "AVAILABLE" : "UNAVAILABLE") + " ====");
    }

    // Local check
    public boolean isLocalAvailable() {
        return localAvailable.get();
    }
    public void revalidateLocalAccess() {
        try {
            initializeLocalDirectories();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to revalidate local access: " + e.getMessage());
        }
    }
}