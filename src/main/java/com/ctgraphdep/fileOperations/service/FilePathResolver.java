package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service for resolving file paths between local and network storage.
 */
@Service
public class FilePathResolver {
    private final PathConfig pathConfig;
    private final Map<String, ReentrantReadWriteLock> pathLocks = new ConcurrentHashMap<>();

    public FilePathResolver(PathConfig pathConfig) {
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Creates a FilePath object for a local file
     * @param username The username associated with the file
     * @param userId The user ID associated with the file
     * @param fileType The type of file (e.g., session, worktime, register)
     * @param parameters Additional parameters for the file path (e.g., year, month)
     * @return The local file path object
     */
    public FilePath getLocalPath(String username, Integer userId, FileType fileType, Map<String, Object> parameters) {
        Path path = resolvePath(true, username, userId, fileType, parameters);
        return FilePath.local(path, username, userId);
    }

    /**
     * Creates a FilePath object for a network file
     * @param username The username associated with the file
     * @param userId The user ID associated with the file
     * @param fileType The type of file (e.g., session, worktime, register)
     * @param parameters Additional parameters for the file path (e.g., year, month)
     * @return The network file path object
     */
    public FilePath getNetworkPath(String username, Integer userId, FileType fileType, Map<String, Object> parameters) {
        Path path = resolvePath(false, username, userId, fileType, parameters);
        return FilePath.network(path, username, userId);
    }

    /**
     * Resolves a path string back to a FilePath object
     * @param pathString The path string to resolve
     * @return The FilePath object
     */
    public FilePath resolve(String pathString) {
        Path path = Path.of(pathString);

        // Determine if this is a local or network path
        boolean isLocal = pathString.startsWith(pathConfig.getLocalPath().toString());
        boolean isNetwork = pathString.startsWith(pathConfig.getNetworkPath().toString());

        if (!isLocal && !isNetwork) {
            throw new IllegalArgumentException("Path is neither local nor network: " + pathString);
        }

        if (isLocal) {
            return FilePath.local(path);
        } else {
            return FilePath.network(path);
        }
    }

    /**
     * Converts a local path to its network equivalent
     * @param localPath The local file path
     * @return The equivalent network file path
     */
    public FilePath toNetworkPath(FilePath localPath) {
        if (!localPath.isLocal()) {
            throw new IllegalArgumentException("Not a local path: " + localPath.getPath());
        }

        Path relativePath = pathConfig.getLocalPath().relativize(localPath.getPath());
        Path networkPath = pathConfig.getNetworkPath().resolve(relativePath);

        return FilePath.network(networkPath, localPath.getUsername().orElse(null), localPath.getUserId().orElse(null));
    }

    /**
     * Converts a network path to its local equivalent
     * @param networkPath The network file path
     * @return The equivalent local file path
     */
    public FilePath toLocalPath(FilePath networkPath) {
        if (!networkPath.isNetwork()) {
            throw new IllegalArgumentException("Not a network path: " + networkPath.getPath());
        }

        Path relativePath = pathConfig.getNetworkPath().relativize(networkPath.getPath());
        Path localPath = pathConfig.getLocalPath().resolve(relativePath);

        return FilePath.local(localPath, networkPath.getUsername().orElse(null), networkPath.getUserId().orElse(null));
    }

    /**
     * Gets a lock for a specific file path
     * @param path The file path to lock
     * @return The lock for the path
     */
    public ReentrantReadWriteLock getLock(FilePath path) {
        String key = path.getPath().toString();
        return pathLocks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
    }

    /**
     * Resolves a path based on the given parameters
     */
    private Path resolvePath(boolean isLocal, String username, Integer userId, FileType fileType, Map<String, Object> parameters) {

        // Declare variables at the beginning of the method, outside the switch statement
        int year = (int) parameters.getOrDefault("year", LocalDate.now().getYear());
        int month = (int) parameters.getOrDefault("month", LocalDate.now().getMonthValue());
        String version = (String) parameters.getOrDefault("version", "Unknown");

        return switch (fileType) {
            //users
            case SESSION -> isLocal ?
                    pathConfig.getLocalSessionPath(username, userId) :
                    pathConfig.getNetworkSessionPath(username, userId);
            case WORKTIME -> isLocal ?
                    pathConfig.getLocalWorktimePath(username, year, month) :
                    pathConfig.getNetworkWorktimePath(username, year, month);
            case TIMEOFF_TRACKER -> isLocal ?
                    pathConfig.getLocalTimeOffTrackerPath(username, userId, year) :
                    pathConfig.getNetworkTimeOffTrackerPath(username, userId, year);
            case TEAM ->
                    pathConfig.getTeamJsonPath(username, year, month);
            case REGISTER -> isLocal ?
                    pathConfig.getLocalRegisterPath(username, userId, year, month) :
                    pathConfig.getNetworkRegisterPath(username, userId, year, month);
            case CHECK_REGISTER -> isLocal ?
                    pathConfig.getLocalCheckRegisterPath(username, userId, year, month) :
                    pathConfig.getNetworkCheckRegisterPath(username, userId, year, month);
            case LEAD_CHECK_REGISTER -> isLocal?
                    pathConfig.getLocalCheckLeadRegisterPath(username,userId,year,month) :
                    pathConfig.getNetworkCheckLeadRegisterPath(username,userId,year,month);
            //admin
            case ADMIN_BONUS ->
                    pathConfig.getLocalBonusPath(year, month);
            case ADMIN_WORKTIME -> isLocal ?
                    pathConfig.getLocalAdminWorktimePath(year, month) :
                    pathConfig.getNetworkAdminWorktimePath(year, month);
            case ADMIN_REGISTER -> isLocal ?
                    pathConfig.getLocalAdminRegisterPath(username, userId, year, month) :
                    pathConfig.getNetworkAdminRegisterPath(username, userId, year, month);
            case CHECK_VALUES -> isLocal ?
                    pathConfig.getLocalCheckValuesPath(username,userId) :
                    pathConfig.getNetworkCheckValuesPath(username,userId);
            //login
            case USERS -> isLocal ?
                    pathConfig.getLocalUsersPath(username,userId) :
                    pathConfig.getNetworkUsersPath(username,userId);
            //status
            case STATUS -> isLocal ?
                    pathConfig.getLocalStatusCachePath() :
                    pathConfig.getNetworkStatusFlagsDirectory();

            //logs - Update to include version parameter
            case LOG -> pathConfig.getNetworkLogPath(username, version);
        };
    }

    /**
     * Creates a parameter map with version information
     * @param version The version string
     * @return A parameter map with version
     */
    public static Map<String, Object> createVersionParams(String version) {
        Map<String, Object> params = new HashMap<>();
        params.put("version", version);
        return params;
    }

    /**
     * Enum representing different types of files in the system
     */
    public enum FileType {
        //user
        SESSION,
        WORKTIME,
        REGISTER,
        TIMEOFF_TRACKER,
        TEAM,
        CHECK_REGISTER,
        LEAD_CHECK_REGISTER,
        //admin
        ADMIN_WORKTIME,
        ADMIN_REGISTER,
        ADMIN_BONUS,
        CHECK_VALUES,
        //login
        USERS,
        //status
        STATUS,
        //logs
        LOG
    }

    /**
     * Creates a parameter map for path resolution
     * @return A mutable parameter maps
     */
    public static Map<String, Object> createParams() {
        return new HashMap<>();
    }

    /**
     * Creates a parameter map with year and month
     * @param year The year
     * @param month The month
     * @return A parameter map with year and month
     */
    public static Map<String, Object> createYearMonthParams(int year, int month) {
        Map<String, Object> params = new HashMap<>();
        params.put("year", year);
        params.put("month", month);
        return params;
    }

    /**
     * Creates a parameter map with just a year
     * @param year The year
     * @return A parameter map with year
     */
    public static Map<String, Object> createYearParams(int year) {
        Map<String, Object> params = new HashMap<>();
        params.put("year", year);
        return params;
    }
}
