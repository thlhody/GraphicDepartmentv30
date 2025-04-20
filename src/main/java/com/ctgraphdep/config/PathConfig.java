package com.ctgraphdep.config;

import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private Path networkPath;
    private Path localPath;
    private final AtomicBoolean networkAvailable = new AtomicBoolean(false);
    private final AtomicBoolean localAvailable = new AtomicBoolean(false); // Add this
    private final Object networkStatusLock = new Object();

    @PostConstruct
    public void init() {
        LoggerUtil.info(this.getClass(), "Raw network path: " + networkBasePath);
        try {
            // Fix network path format - ensure proper UNC path format
            networkBasePath = normalizeNetworkPath(networkBasePath);
            LoggerUtil.info(this.getClass(), "Using normalized network path: " + networkBasePath);

            // Initialize paths
            networkPath = Paths.get(networkBasePath);
            localPath = Paths.get(localBasePath, appTitle);

            // Create local directories
            initializeLocalDirectories();

            // Check network availability (this will be updated by NetworkMonitorService)
            updateNetworkStatus();

            LoggerUtil.info(this.getClass(), String.format("Initialized paths - Network: %s, Local: %s, Network Available: %b, Local Available: %b",
                    networkPath, localPath, networkAvailable.get(), localAvailable.get()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during initialization: " + e.getMessage());
            networkAvailable.set(false);
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

    // Network status management
    public boolean isNetworkAvailable() {
        forceNetworkAvailable();
        synchronized (networkStatusLock) {
            return networkAvailable.get();
        }
    }
    public void forceNetworkAvailable() {
        synchronized (networkStatusLock) {
            if (!networkAvailable.get()) {
                networkAvailable.set(true);
                LoggerUtil.info(this.getClass(), "Manually forced network status to available");
            }
        }
    }
    public void updateNetworkStatus() {
        synchronized (networkStatusLock) {
            boolean previous = networkAvailable.get();
            boolean current = checkNetworkAccess();
            networkAvailable.set(current);
            if (previous != current) {
                LoggerUtil.info(this.getClass(), String.format("Network status changed from %b to %b", previous, current));
            }
        }
    }
    private boolean checkNetworkAccess() {
        try {
            // Validate network path
            if (networkPath == null) {
                return false;
            }

             //Skip UNC path check for development
             if (!networkPath.toString().startsWith("\\\\")) {
                LoggerUtil.warn(this.getClass(), "Invalid network path format");
                return false;
             }

            if (!Files.exists(networkPath) || !Files.isDirectory(networkPath)) {
                LoggerUtil.debug(this.getClass(), "Network path not available or not a directory");
                return false;
            }

            // Test write access with retry
            for (int i = 0; !(i > 2); i++) {
                try {
                    Path testFile = networkPath.resolve(".test_" + System.currentTimeMillis());
                    Files.createFile(testFile);
                    Files.delete(testFile);
                    return true;
                } catch (IOException e) {
                    if (i < 2) {
                        Thread.sleep(1000);
                        continue;
                    }
                    LoggerUtil.debug(this.getClass(), "Network write test failed: " + e.getMessage());
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), "Network check failed: " + e.getMessage());
            return false;
        }
    }
    @Scheduled(fixedDelay = 600000) // Check every 10 minutes - periodic checker
    public void scheduledNetworkCheck() {
        try {
            boolean previous = networkAvailable.get();
            boolean current = checkNetworkAccess();

            if (previous != current) {
                LoggerUtil.info(this.getClass(), String.format("Network status changed from %b to %b", previous, current));
                networkAvailable.set(current);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Scheduled network check failed: " + e.getMessage());
        }
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

    //Helper methods
    private String normalizeNetworkPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return path;
        }

        // Remove any quotes, brackets or parentheses
        path = path.replaceAll("[\"'()]", "");

        // Fix UNC path format - must start with \\
        if (path.startsWith("\\") && !path.startsWith("\\\\")) {
            path = "\\" + path;
        }

        // Normalize excessive backslashes
        if (path.matches("^\\\\\\\\+.*")) {
            path = "\\\\" + path.replaceAll("^\\\\+", "");
        }

        return path;
    }

}
