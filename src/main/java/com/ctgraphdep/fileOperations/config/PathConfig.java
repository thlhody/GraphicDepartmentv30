package com.ctgraphdep.fileOperations.config;

import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.events.NetworkStatusChangedEvent;
import com.ctgraphdep.fileOperations.util.FileOperationsUtil;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configuration for file paths used throughout the application.
 */
@Getter
@Setter
@Component
@Configuration
@ConfigurationProperties(prefix = "dbj")
public class PathConfig {

    // This flag will be controlled by NetworkStatusMonitor
    private final AtomicBoolean networkAvailable = new AtomicBoolean(false);
    private final AtomicBoolean localAvailable = new AtomicBoolean(false);
    // Cache for FilePath objects for commonly used paths
    private final Map<String, FilePath> filePathCache = new HashMap<>();

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    //App Title
    @Value("${app.title:CTTT}")
    private String appTitle;

    // Base paths
    @Value("${app.paths.network}")
    private String networkBasePath;
    private Path networkPath;
    @Value("${app.local}")
    private String localBasePath;
    private Path localPath;

    //Users Path and Format
    @Value("${dbj.login}")
    private String loginPath;
    @Value("${dbj.login.users}")
    private String usersPath;

    //Backups Path and Format
    @Value("${dbj.backup}")
    private String backupPath;
    @Value("${dbj.backup.level.low}")
    private String levelLow;
    @Value("${dbj.backup.level.medium}")
    private String levelMedium;
    @Value("${dbj.backup.level.high}")
    private String levelHigh;
    @Value("${dbj.backup.admin}")
    private String adminBackup;
    //Users
    @Value("${dbj.users.network.filename}")
    private String networkUsersFilename;
    @Value("${dbj.users.local.filename}")
    private String localUsersFilename;

    //Check Values
    @Value("${dbj.users.check.filename}")
    private String checkValuesFilename;

    //Status Path and Format
    @Value("${dbj.user.status}")
    private String userStatus;
    @Value("${dbj.dir.format.status}")
    private String localStatusFileFormat;
    @Value("${dbj.dir.format.status.flag}")
    private String statusFlagFormat;

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

            // Create backup directory structure
            initializeBackupDirectories();

            // We won't check network status here - NetworkStatusMonitor will handle this
            LoggerUtil.info(this.getClass(), String.format("Initialized paths - Network: %s, Local: %s, Local Available: %b",
                    networkPath, localPath, localAvailable.get()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during initialization: " + e.getMessage());
        }
    }

    /**
     * Initialize backup directory structure
     */
    private void initializeBackupDirectories() {
        try {
            // Ensure backup base directory exists
            Path backupBaseDir = localPath.resolve(backupPath);
            Files.createDirectories(backupBaseDir);

            // Create criticality level directories
            Path level1Dir = backupBaseDir.resolve(levelLow);
            Path level2Dir = backupBaseDir.resolve(levelMedium);
            Path level3Dir = backupBaseDir.resolve(levelHigh);
            Path adminBackupsDir = backupBaseDir.resolve(adminBackup);

            Files.createDirectories(level1Dir);
            Files.createDirectories(level2Dir);
            Files.createDirectories(level3Dir);
            Files.createDirectories(adminBackupsDir);

            LoggerUtil.info(this.getClass(), "Initialized backup directory structure at: " + backupBaseDir);
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Failed to initialize backup directories: " + e.getMessage());
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
                    loginPath, usersPath, userSession, userStatus,
                    userWorktime, userRegister, userTimeoff, adminWorktime,
                    adminRegister, adminBonus, networkLogsPath, backupPath
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

    //New Users/Holiday/CheckValues logic
    public Path getNetworkUsersPath(String username, Integer userId) {
        return  networkPath.resolve(usersPath).resolve(String.format(networkUsersFilename,username, userId));
    }
    public Path getLocalUsersPath(String username, Integer userId) {
        return  localPath.resolve(usersPath).resolve(String.format(localUsersFilename,username, userId));
    }
    public Path getNetworkCheckValuesPath(String username, Integer userId){
        return networkPath.resolve(usersPath).resolve(String.format(checkValuesFilename,username,userId));
    }
    public Path getLocalCheckValuesPath(String username, Integer userId){
        return localPath.resolve(usersPath).resolve(String.format(checkValuesFilename,username,userId));
    }

    // Network-only paths
    public Path getNetworkStatusFlagsDirectory() {
        return networkPath.resolve(userStatus);
    }
    public Path getNetworkLogDirectory() {
        return networkPath.resolve(networkLogsPath);
    }

    /**
     * Gets the network log path with version information
     * @param username The username
     * @param version The application version
     * @return Path to the user's log file with version
     */
    public Path getNetworkLogPath(String username, String version) {
        // Format: ctgraphdep-logger_username_vX.Y.Z.log
        String formattedLogFilename = String.format(logFileFormat, username, version);
        return getNetworkLogDirectory().resolve(formattedLogFilename);
    }

    /**
     * Extracts version from log filename
     * @param filename The log filename
     * @return The version string or "Unknown" if not found
     */
    public String extractVersionFromLogFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "Unknown";
        }

        // Pattern to match ctgraphdep-logger_username_vX.Y.Z.log
        Pattern pattern = Pattern.compile("ctgraphdep-logger_(.+)_v([\\d.]+)\\.log$");
        Matcher matcher = pattern.matcher(filename);

        if (matcher.find()) {
            return matcher.group(2); // Return the version group
        }

        // Backward compatibility for old log format (without version)
        return "Unknown";
    }

    // Local-only paths
    public Path getLocalBonusPath(int year, int month) {
        return localPath.resolve(adminBonus).resolve(String.format(adminBonusFormat, year, month));
    }
    public Path getTeamJsonPath(String teamLeadUsername, int year, int month) {
        return localPath.resolve(loginPath).resolve(String.format(teamFileFormat, teamLeadUsername, year, month));
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
        return localPath.resolve(userSession).resolve(String.format(sessionFormat, username, userId));
    }
    public Path getNetworkSessionPath(String username, Integer userId) {
        return networkPath.resolve(userSession).resolve(String.format(sessionFormat, username, userId));
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

    // Local/Network check
    public boolean isLocalAvailable() {
        return localAvailable.get();
    }
    public boolean isNetworkAvailable() {
        return networkAvailable.get();
    }
    public void setNetworkAvailable(boolean available) {
        boolean previous = networkAvailable.getAndSet(available);
        if (previous != available) {
            String reason = available ? "Network became available" : "Network became unavailable";
            LoggerUtil.info(this.getClass(), String.format("Network status updated to: %s", available ? "Available" : "Unavailable"));

            // Publish event for any interested services
            try {
                NetworkStatusChangedEvent event = new NetworkStatusChangedEvent(this, available, reason);
                eventPublisher.publishEvent(event);
                LoggerUtil.debug(this.getClass(), "Published network status event: " + event);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error publishing network status event: " + e.getMessage());
            }
        }
    }

    // Check & Create local directories
    public void revalidateLocalAccess() {
        try {
            initializeLocalDirectories();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to revalidate local access: " + e.getMessage());
        }
    }

    /**
     * Verifies and creates user directories if they don't exist
     * @return true if directories exist or were successfully created
     */
    public boolean verifyUserDirectories() {
        try {
            // Create parent directories for essential user paths if they don't exist
            Path sessionParent = getLocalSessionPath("user", 0).getParent();
            Path worktimeParent = getLocalWorktimePath("user", 0, 0).getParent();
            Path registerParent = getLocalRegisterPath("user", 0, 0, 0).getParent();
            Path checkRegisterParent = getLocalCheckRegisterPath("user", 0, 0, 0).getParent();
            Path timeOffParent = getLocalTimeOffTrackerPath("user", 0, 0).getParent();
            Path checkValuesParent = getLocalCheckValuesPath("user", 0).getParent();

            // Create each directory if needed
            createDirectoryIfNeeded(sessionParent);
            createDirectoryIfNeeded(worktimeParent);
            createDirectoryIfNeeded(registerParent);
            createDirectoryIfNeeded(checkRegisterParent);
            createDirectoryIfNeeded(timeOffParent);
            createDirectoryIfNeeded(checkValuesParent);

            LoggerUtil.info(this.getClass(), "User directories verified");
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to verify/create user directories: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifies and creates admin directories if they don't exist
     * @return true if directories exist or were successfully created
     */
    public boolean verifyAdminDirectories() {
        try {
            // Create parent directories for essential admin paths if they don't exist
            Path adminWorktimeParent = getLocalAdminWorktimePath(0, 0).getParent();
            Path adminRegisterParent = getLocalAdminRegisterPath("admin", 0, 0, 0).getParent();
            Path bonusParent = getLocalBonusPath(0, 0).getParent();
            Path leadCheckParent = getLocalCheckLeadRegisterPath("admin", 0, 0, 0).getParent();

            // Create each directory if needed
            createDirectoryIfNeeded(adminWorktimeParent);
            createDirectoryIfNeeded(adminRegisterParent);
            createDirectoryIfNeeded(bonusParent);
            createDirectoryIfNeeded(leadCheckParent);

            LoggerUtil.info(this.getClass(), "Admin directories verified");
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to verify/create admin directories: " + e.getMessage());
            return false;
        }
    }

    /**
     * Helper method to create a directory if it doesn't exist
     */
    private void createDirectoryIfNeeded(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            LoggerUtil.info(this.getClass(), "Created directory: " + directory);
        }
    }
}