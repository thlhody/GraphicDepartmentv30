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

    // Base paths
    @Value("${app.paths.network}")
    private String networkBasePath;

    @Value("${app.local}")
    private String localBasePath;

    @Value("${app.title:CTTT}")
    private String appTitle;

    //Status db paths
    @Value("${dbj.user.status:dbj/user/usersession/status_db}")
    private String userStatus;
    @Value("${dbj.dir.format.status:status_%s_%d.json}")
    private String statusFormat;
    @Value("${dbj.dir.format.resolution:resolution_%s_%d.json}")
    private String resolutionFormat;


    // Directory paths
    @Value("${dbj.user.session}")
    private String userSession;
    @Value("${dbj.user.worktime}")
    private String userWorktime;
    @Value("${dbj.user.register}")
    private String userRegister;
    @Value("${dbj.admin.worktime}")
    private String adminWorktime;
    @Value("${dbj.admin.register}")
    private String adminRegister;
    @Value("${dbj.admin.bonus}")
    private String adminBonus;
    @Value("${dbj.login}")
    private String loginPath;

    // File formats
    @Value("${dbj.dir.format.session}")
    private String sessionFormat;
    @Value("${dbj.dir.format.worktime}")
    private String worktimeFormat;
    @Value("${dbj.dir.format.register}")
    private String registerFormat;
    @Value("${dbj.dir.format.admin.worktime}")
    private String adminWorktimeFormat;
    @Value("${dbj.dir.format.admin.register}")
    private String adminRegisterFormat;
    @Value("${dbj.dir.format.admin.bonus}")
    private String adminBonusFormat;

    // File names
    @Value("${dbj.users.filename}")
    private String usersFilename;
    @Value("${dbj.users.local.filename}")
    private String localUsersFilename;
    @Value("${dbj.dir.format.team}")
    private String teamFileFormat;
    @Value("${dbj.users.holiday}")
    private String holidayFilename;

    @Value("${app.cache.holiday}")
    private String holidayCacheFile;  // holiday_cache.json

    @Value("${app.lock.holiday}")
    private String holidayLockFile;   // holiday.lock

    @Value("${app.lock.users}")
    private String usersLockFile;   // users.lock

    // Return the base network path for use by other services
    @Getter
    private Path networkPath;
    private Path localPath;
    private final AtomicBoolean networkAvailable = new AtomicBoolean(false);
    private final AtomicBoolean localAvailable = new AtomicBoolean(false); // Add this
    private final Object networkStatusLock = new Object();

    @PostConstruct
    public void init() {
        // Add at beginning of init() in PathConfig
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

            LoggerUtil.info(this.getClass(), String.format(
                    "Initialized paths - Network: %s, Local: %s, Network Available: %b, Local Available: %b",
                    networkPath, localPath, networkAvailable.get(), localAvailable.get()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during initialization: " + e.getMessage());
            networkAvailable.set(false);
        }
    }

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

    // Network-only paths
    public Path getNetworkUsersPath() {
        return networkPath.resolve(loginPath).resolve(usersFilename);
    }

    public Path getNetworkHolidayPath() {
        return networkPath.resolve(loginPath).resolve(holidayFilename);
    }

    // Local-only paths
    public Path getLocalUsersPath() {
        return localPath.resolve(loginPath).resolve(localUsersFilename);
    }

    public Path getLocalBonusPath(int year, int month) {
        return localPath.resolve(adminBonus).resolve(String.format(adminBonusFormat, year, month));
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

    public Path getLocalStatusDbDirectory() {
        return localPath.resolve(userStatus);
    }

    public Path getNetworkStatusDbDirectory() {
        return networkPath.resolve(userStatus);
    }

    public Path getLocalStatusFilePath(String username, Integer userId) {
        return getLocalStatusDbDirectory().resolve(String.format(statusFormat, username, userId));
    }

    public Path getNetworkStatusFilePath(String username, Integer userId) {
        return getNetworkStatusDbDirectory().resolve(String.format(statusFormat, username, userId));
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

    public Path getTeamJsonPath(String teamLeadUsername, int year, int month) {
        return localPath.resolve(loginPath).resolve(String.format(teamFileFormat, teamLeadUsername, year, month));
    }

    public Path getHolidayCachePath() {
        return networkPath.resolve(loginPath).resolve(holidayCacheFile);
    }

    public Path getHolidayLockPath() {
        return networkPath.resolve(loginPath).resolve(holidayLockFile);
    }
    public Path getUsersLockPath() {
        return networkPath.resolve(loginPath).resolve(usersLockFile);
    }

    // Resolution file path - local only
    public Path getLocalResolutionPath(String username, Integer userId) {
        return localPath.resolve(userSession)
                .resolve(String.format(resolutionFormat, username, userId));
    }

    // Network status management
    public boolean isNetworkAvailable() {
        synchronized (networkStatusLock) {
            return networkAvailable.get();
        }
    }

    public void updateNetworkStatus() {
        synchronized (networkStatusLock) {
            boolean previous = networkAvailable.get();
            boolean current = checkNetworkAccess();
            networkAvailable.set(current);

            if (previous != current) {
                LoggerUtil.info(this.getClass(),
                        String.format("Network status changed from %b to %b", previous, current));
            }
        }
    }

    private boolean checkNetworkAccess() {
        try {
            // Validate network path
            // For development, allow local paths
            if (networkPath == null) {
                return false;
            }

            // Skip UNC path check for development
            // if (!networkPath.toString().startsWith("\\\\")) {
            //    LoggerUtil.warn(this.getClass(), "Invalid network path format");
            //    return false;
            // }

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

    // Add periodic network status checker
    @Scheduled(fixedDelay = 600000) // Check every minute
    public void scheduledNetworkCheck() {
        try {
            boolean previous = networkAvailable.get();
            boolean current = checkNetworkAccess();

            if (previous != current) {
                LoggerUtil.info(this.getClass(),
                        String.format("Network status changed from %b to %b", previous, current));
                networkAvailable.set(current);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Scheduled network check failed: " + e.getMessage());
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
                    adminWorktime,
                    adminRegister,
                    adminBonus
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