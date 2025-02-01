package com.ctgraphdep.config;

import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
@Component
@Configuration
@ConfigurationProperties(prefix = "dbj")
public class PathConfig {

    //main paths
    @Value("${app.paths.network}")
    private String networkBasePath;

    @Value("${app.paths.development}")
    private String developmentBasePath;

    @Value("${app.local}")
    private String installationPath;

    // File format configurations
    @Value("${dbj.dir.format.session:session_%s_%d.json}")
    private String sessionFormat;

    @Value("${dbj.dir.format.worktime:worktime_%s_%d_%02d.json}")
    private String worktimeFormat;

    @Value("${dbj.dir.format.register:registru_%s_%d_%d_%02d.json}")
    private String registerFormat;

    @Value("${dbj.dir.format.admin.worktime:general_worktime_%d_%02d.json}")
    private String adminWorktimeFormat;

    @Value("${dbj.dir.format.admin.register:admin_registru_%s_%d_%d_%02d.json}")
    private String adminRegisterFormat;

    @Value("${dbj.dir.format.admin.bonus:admin_bonus_%d_%02d.json}")
    private String adminBonusFormat;

    @Value("${dbj.users.holiday:paid_holiday_list.json}")
    private String holidayFilename;

    @Getter
    @Value("${dbj.users.local.filename:local_users.json}")
    private String localUsersFilename;

    @Value("${dbj.users.filename:users.json}")
    private String usersFilename;

    @Value("${app.path.verify.enabled:true}")
    private boolean verifyEnabled;

    @Value("${app.path.create.missing:true}")
    private boolean createMissing;

    @Value("${app.title:CTTT}")
    private String appTitle;

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

    private Path networkPath;
    private Path installPath;
    private Path activePath;
    private final List<String> missingDirectories;
    private final Map<String, String> user = new HashMap<>();
    private final Map<String, String> admin = new HashMap<>();
    private String login;

    // Update the pattern sets to ensure they match exactly
    private static final Set<String> USER_SYNCABLE_PATTERNS = Set.of(
            "session_.*_\\d+\\.json",
            "worktime_.*_\\d{4}_\\d{2}\\.json",
            "registru_.*_\\d+_\\d{4}_\\d{2}\\.json"
    );

    private static final Set<String> ADMIN_SYNCABLE_PATTERNS = Set.of(
            "general_worktime_\\d{4}_\\d{2}\\.json",
            "admin_registru_.*_\\d+_\\d{4}_\\d{2}\\.json"
    );

    private static final Set<String> NETWORK_PRIMARY_FILES = Set.of(
            "users.json",
            "paid_holiday_list.json"
    );

    private static final Set<String> LOCAL_ONLY_FILES = Set.of(
            "admin_bonus_%d_%02d.json",
            "local_users.json"
    );

    // Network status tracking
    private final AtomicBoolean networkAvailable = new AtomicBoolean(false);
    private final Object networkStatusLock = new Object();


    @Autowired
    public PathConfig() {
        this.missingDirectories = new ArrayList<>();
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        try {
            initializePaths();
            initializeDirectoryMappings();
            determineActivePath();

            if (verifyEnabled) {
                verifyDirectoryStructure();
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during initialization", e);
            // Instead of throwing an exception, fall back to local path
            activePath = installPath;
            networkAvailable.set(false);
        }
    }

    private void initializeDirectoryMappings() {
        // Initialize user mappings
        user.put("session", userSession);
        user.put("worktime", userWorktime);
        user.put("register", userRegister);

        // Initialize admin mappings
        admin.put("worktime", adminWorktime);
        admin.put("register", adminRegister);
        admin.put("bonus", adminBonus);

        // Set login path
        this.login = loginPath;

        LoggerUtil.info(this.getClass(), "Initialized directory mappings");
    }

    private void initializePaths() {
        // Initialize all possible paths
        networkPath = Paths.get(networkBasePath);
        installPath = Paths.get(installationPath, appTitle);

        LoggerUtil.info(this.getClass(),
                String.format("Initialized paths - Network: %s, Installation: %s",
                        networkPath, installPath));
    }

    // Add new methods for file type checking
    public boolean isNetworkPrimaryFile(String filename) {
        return NETWORK_PRIMARY_FILES.contains(filename) ||
                NETWORK_PRIMARY_FILES.stream()
                        .anyMatch(pattern -> filename.matches(pattern.replace("%d", "\\d+")));
    }

    private boolean matchesPattern(String filename, Set<String> patterns) {
        for (String pattern : patterns) {
            if (filename.matches(pattern)) {
                LoggerUtil.debug(this.getClass(),
                        String.format("File %s matches pattern %s", filename, pattern));
                return true;
            }
        }
        return false;
    }

    public boolean shouldSync(String filename, boolean isAdmin, String username) {
        LoggerUtil.info(this.getClass(),
                String.format("Checking sync for file: %s, isAdmin: %b, username: %s",
                        filename, isAdmin, username));

        // First check if it's excluded from sync
        if (isLocalOnlyFile(filename)) {
            LoggerUtil.info(this.getClass(), "File is local-only, skipping sync");
            return false;
        }

        if (isNetworkPrimaryFile(filename)) {
            LoggerUtil.info(this.getClass(), "File is network-primary, skipping sync");
            return false;
        }

        boolean shouldSync = false;
        if (isAdmin) {
            shouldSync = matchesPattern(filename, ADMIN_SYNCABLE_PATTERNS);
        } else {
            shouldSync = matchesPattern(filename, USER_SYNCABLE_PATTERNS) &&
                    filename.contains(username);
        }

        LoggerUtil.info(this.getClass(),
                String.format("Final sync decision for %s: %b", filename, shouldSync));
        return shouldSync;
    }

    public boolean isLocalOnlyFile(String filename) {
        return LOCAL_ONLY_FILES.contains(filename);
    }

    // Enhance path resolution for different file types
    public Path resolvePathForWrite(String filename) {
        // Specific handling for different file types
        if (filename.startsWith("session_")) {
            // Always write session files to local path
            return installPath.resolve(userSession).resolve(filename);
        }
        if (filename.startsWith("worktime_")) {
            // Write worktime files to local path first
            return installPath.resolve(userWorktime).resolve(filename);
        }
        if (filename.startsWith("registru_")) {
            // Write register files to local path first
            return installPath.resolve(userRegister).resolve(filename);
        }
        if (filename.startsWith("general_worktime_")) {
            // Write admin worktime files to local path first
            return installPath.resolve(adminWorktime).resolve(filename);
        }
        if (filename.startsWith("admin_registru_")) {
            // Write admin register files to local path first
            return installPath.resolve(adminRegister).resolve(filename);
        }
        if (filename.startsWith("admin_bonus_")) {
            // Write admin bonus files to local path first
            return installPath.resolve(adminBonus).resolve(filename);
        }
        if (filename.equals(localUsersFilename)) {
            return installPath.resolve(loginPath).resolve(filename);
        }
        if (filename.equals(usersFilename)) {
            // Write users file to local login path
            return installPath.resolve(loginPath).resolve(filename);
        }
        if (filename.equals(holidayFilename)) {
            // Write holiday file to local login path
            return installPath.resolve(login).resolve(filename);
        }

        // Fallback to local installation path
        return installPath.resolve(filename);
    }

    @SneakyThrows
    public Path resolvePathForRead(String filename) {
        // For network primary files
        if (filename.equals(usersFilename) || filename.equals(holidayFilename)) {
            if (isNetworkAvailable()) {
                Path networkFilePath = networkPath.resolve(loginPath).resolve(filename);
                if (Files.exists(networkFilePath) && Files.size(networkFilePath) > 0) {
                    return networkFilePath;
                }
            }
            return installPath.resolve(loginPath).resolve(filename);
        }

        // For local-only files
        if (filename.equals(localUsersFilename)) {
            return installPath.resolve(loginPath).resolve(filename);
        }
        if (filename.startsWith("admin_bonus_")) {
            return installPath.resolve(adminBonus).resolve(filename);
        }

        // For all other files
        if (isNetworkAvailable()) {
            Path networkFilePath = null;

            // Determine correct network path with proper subdirectories
            if (filename.startsWith("session_")) {
                networkFilePath = networkPath.resolve(userSession).resolve(filename); // dbj/user/usersession
            } else if (filename.startsWith("worktime_")) {
                networkFilePath = networkPath.resolve(userWorktime).resolve(filename); // dbj/user/userworktime
            } else if (filename.startsWith("registru_")) {
                networkFilePath = networkPath.resolve(userRegister).resolve(filename); // dbj/user/userregister
            } else if (filename.startsWith("general_worktime_")) {
                networkFilePath = networkPath.resolve(adminWorktime).resolve(filename); // dbj/admin/adminworktime
            } else if (filename.startsWith("admin_registru_")) {
                networkFilePath = networkPath.resolve(adminRegister).resolve(filename); // dbj/admin/adminregister
            }

            // If network path exists, use it
            if (networkFilePath != null && Files.exists(networkFilePath)) {
                return networkFilePath;
            }
        }

        // Fallback to local with correct subdirectories
        if (filename.startsWith("session_")) {
            return installPath.resolve(userSession).resolve(filename);
        } else if (filename.startsWith("worktime_")) {
            return installPath.resolve(userWorktime).resolve(filename);
        } else if (filename.startsWith("registru_")) {
            return installPath.resolve(userRegister).resolve(filename);
        } else if (filename.startsWith("general_worktime_")) {
            return installPath.resolve(adminWorktime).resolve(filename);
        } else if (filename.startsWith("admin_registru_")) {
            return installPath.resolve(adminRegister).resolve(filename);
        }

        return installPath.resolve(filename);
    }
    // Enhance network status checking
    public void updateNetworkStatus() {
        synchronized (networkStatusLock) {
            boolean previous = networkAvailable.get();
            boolean current = isPathAccessible(networkPath);
            networkAvailable.set(current);

            if (previous != current) {
                LoggerUtil.info(this.getClass(),
                        String.format("Network status changed from %s to %s",
                                previous, current));
            }
        }
    }

    private boolean isPathAccessible(Path path) {
        try {
            if (path == null) {
                LoggerUtil.error(this.getClass(), "Path is null");
                return false;
            }

            // More comprehensive path checks
            boolean exists = Files.exists(path);
            boolean isDirectory = Files.isDirectory(path);
            boolean isReadable = Files.isReadable(path);
            boolean isWritable = Files.isWritable(path);

            LoggerUtil.info(this.getClass(),
                    String.format("Path Check: %s - Exists: %b, IsDirectory: %b, Readable: %b, Writable: %b",
                            path, exists, isDirectory, isReadable, isWritable));

            return exists && isDirectory && isReadable && isWritable;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking path %s: %s",
                            path, e.getMessage()), e);
            return false;
        }
    }

    private void determineActivePath() {
        LoggerUtil.info(this.getClass(), "Determining active path");

        try {
            // Normalize paths with explicit creation
            networkPath = createAndValidatePath(networkBasePath);
            installPath = createAndValidatePath(installationPath + File.separator + appTitle);

            // Always ensure installation path exists
            Files.createDirectories(installPath);

            // Set initial state to local mode
            activePath = installPath;
            networkAvailable.set(false);

            // Try to enable network mode if possible
            if (checkNetworkPathAccessibility(networkPath)) {
                activePath = networkPath;
                networkAvailable.set(true);
                LoggerUtil.info(this.getClass(), "Using network path: " + networkPath);
            } else {
                LoggerUtil.info(this.getClass(), "Using local installation path: " + installPath);
            }

            // Create required directories in active path
            createRequiredDirectories();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error determining active path", e);
            // Ensure we fallback to installation path
            activePath = installPath;
            networkAvailable.set(false);

            // Try to create local directories as last resort
            try {
                Files.createDirectories(installPath);
                createRequiredDirectories();
            } catch (IOException ioEx) {
                LoggerUtil.error(this.getClass(), "Critical failure: Cannot create installation directory", ioEx);
            }
        }
    }

    private Path createAndValidatePath(String pathString) {
        Path path = Paths.get(pathString).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
            if (!Files.isWritable(path)) {
                throw new IOException("Path is not writable: " + path);
            }
        } catch (IOException e) {
            LoggerUtil.warn(this.getClass(), "Could not create/validate path: " + path + " - " + e.getMessage());
        }
        return path;
    }

    private void createRequiredDirectories() {
        List<String> requiredDirs = Arrays.asList(
                login,
                userSession,
                userWorktime,
                userRegister,
                adminWorktime,
                adminRegister,
                adminBonus
        );

        for (String dir : requiredDirs) {
            try {
                Path dirPath = activePath.resolve(dir);
                Files.createDirectories(dirPath);

                // Initialize users.json in login directory if needed
                if (dir.equals(login)) {
                    Path usersFile = dirPath.resolve(usersFilename);
                    if (!Files.exists(usersFile)) {
                        Files.write(usersFile, "[]".getBytes());
                    }
                }
            } catch (IOException e) {
                LoggerUtil.warn(this.getClass(), "Could not create directory: " + dir + " - " + e.getMessage());
            }
        }
    }

    private boolean checkNetworkPathAccessibility(Path networkPath) {
        try {
            // First just check basic connectivity
            if (!Files.exists(networkPath)) {
                LoggerUtil.debug(this.getClass(), "Network path does not exist");
                return false;
            }

            if (!Files.isDirectory(networkPath)) {
                LoggerUtil.debug(this.getClass(), "Network path is not a directory");
                return false;
            }

            // Add retry logic for write check
            int maxRetries = 3;
            int retryDelay = 1000; // 1 second

            for (int i = 0; i < maxRetries; i++) {
                try {
                    // Create a test file to verify write access
                    Path testFile = networkPath.resolve(".test_write_" + System.currentTimeMillis());
                    Files.createFile(testFile);
                    Files.delete(testFile);

                    // If we get here, write access is confirmed
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Network path check successful - Path: %s, Exists: true, IsDir: true",
                            networkPath));
                    return true;
                } catch (Exception e) {
                    if (i < maxRetries - 1) {
                        Thread.sleep(retryDelay);
                        continue;
                    }
                    LoggerUtil.warn(this.getClass(),
                            "Write check failed after retries: " + e.getMessage());
                }
            }

            return false;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error checking network path: " + e.getMessage());
            return false;
        }
    }

    public Path getUsersJsonPath() {
        return activePath.resolve(loginPath).resolve(usersFilename);
    }
    public Path getLocalUsersJsonPath() {
        return installPath.resolve(loginPath).resolve(localUsersFilename);
    }
    public Path getHolidayListPath() {
        return activePath.resolve(login).resolve(holidayFilename);
    }

    public Path getSessionFilePath(String username, Integer userId) {
        return activePath.resolve(userSession).resolve(String.format(sessionFormat, username, userId));
    }
    public Path getLocalSessionFilePath(String username, Integer userId) {
        // This method specifically returns the local session file path
        return installPath.resolve(userSession).resolve(String.format(sessionFormat, username, userId));
    }
    public Path getUserWorktimeFilePath(String username, int year, int month) {
        return activePath.resolve(userWorktime).resolve(String.format(worktimeFormat, username, year, month));
    }
    public Path getUserRegisterPath(String username, Integer userId, int year, int month) {
        return activePath.resolve(userRegister).resolve(String.format(registerFormat, username, userId, year, month));
    }

    public Path getAdminWorktimePath(int year, int month) {
        return activePath.resolve(adminWorktime).resolve(String.format(adminWorktimeFormat, year, month));
    }
    public Path getAdminRegisterPath(String username, Integer userId, int year, int month) {
        return activePath.resolve(adminRegister).resolve(String.format(adminRegisterFormat, username, userId, year, month));
    }
    public Path getAdminBonusPath(int year, int month) {
        return activePath.resolve(adminBonus).resolve(String.format(adminBonusFormat, year, month));
    }

    // Status checks
    public boolean isNetworkAvailable() {
        synchronized (networkStatusLock) {
            // Do a fresh check if needed
            if (!networkAvailable.get()) {
                boolean available = checkNetworkPathAccessibility(networkPath);
                networkAvailable.set(available);
                LoggerUtil.debug(this.getClass(),
                        String.format("Refreshed network status: %b", available));
            }
            return networkAvailable.get();
        }
    }

    // Get Local Path (Installation Path)
    public Path getLocalPath() {
        return installPath;
    }

    private void verifyDirectoryStructure() {
        if (!verifyEnabled) {
            LoggerUtil.info(this.getClass(), "Directory verification disabled");
            return;
        }

        missingDirectories.clear();

        // Use the configured paths from properties
        List<String> pathsToVerify = Arrays.asList(
                loginPath,           // dbj/login
                userSession,         // dbj/user/usersession
                userWorktime,        // dbj/user/userworktime
                userRegister,        // dbj/user/userregister
                adminWorktime,       // dbj/admin/adminworktime
                adminRegister,       // dbj/admin/adminregister
                adminBonus          // dbj/admin/bonus
        );

        for (String dirPath : pathsToVerify) {
            Path fullPath = activePath.resolve(dirPath);
            if (!Files.exists(fullPath)) {
                missingDirectories.add(dirPath);
                if (createMissing) {
                    try {
                        Files.createDirectories(fullPath);
                        if (dirPath.equals(loginPath)) {
                            Files.write(fullPath.resolve(usersFilename), "[]".getBytes());
                        }
                        LoggerUtil.info(this.getClass(),
                                "Created directory and initialized: " + fullPath);
                    } catch (Exception e) {
                        LoggerUtil.error(this.getClass(),
                                "Failed to create directory: " + fullPath, e);
                    }
                }
            }
        }
    }

    // Base directory path getters
    public Path getUserSessionDir() {
        return activePath.resolve(userSession);
    }

    public Path getUserWorktimeDir() {
        return activePath.resolve(userWorktime);
    }
    public Path getUserRegisterDir() {
        return activePath.resolve(userRegister);
    }

    public Path getAdminWorktimeDir() {
        return activePath.resolve(adminWorktime);
    }
    public Path getAdminRegisterDir() {
        return activePath.resolve(adminRegister);
    }
    public Path getAdminBonusDir() {
        return activePath.resolve(adminBonus);
    }

    public Path getLoginDir() {
        return activePath.resolve(loginPath);
    }

}