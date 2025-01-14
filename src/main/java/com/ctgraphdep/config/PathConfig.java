package com.ctgraphdep.config;

import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

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
    private Path devPath;
    private Path activePath;
    private final List<String> missingDirectories;
    private final Map<String, String> user = new HashMap<>();
    private final Map<String, String> admin = new HashMap<>();
    private String login;

    private static final Set<String> NETWORK_PRIMARY_FILES = Set.of(
            "users.json",
            "paid_holiday_list.json"
    );

    private static final Set<String> LOCAL_ONLY_FILES = Set.of(
            "admin_bonus_%d_%02d.json"
    );

    // Network status tracking
    private final AtomicBoolean networkAvailable = new AtomicBoolean(false);
    private final Object networkStatusLock = new Object();


    @Autowired
    public PathConfig() {
        this.missingDirectories = new ArrayList<>();
        LoggerUtil.initialize(this.getClass(), "Initializing Path Configuration");
    }

    @PostConstruct
    public void init() {
        initializePaths();
        initializeDirectoryMappings();
        determineActivePath();
        if (verifyEnabled) {
            verifyDirectoryStructure();
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
        devPath = Paths.get(developmentBasePath);

        LoggerUtil.info(this.getClass(),
                String.format("Initialized paths - Network: %s, Installation: %s, Development: %s",
                        networkPath, installPath, devPath));
    }

    // Add new methods for file type checking
    public boolean isNetworkPrimaryFile(String filename) {
        return NETWORK_PRIMARY_FILES.contains(filename) ||
                NETWORK_PRIMARY_FILES.stream()
                        .anyMatch(pattern -> filename.matches(pattern.replace("%d", "\\d+")));
    }

    public boolean isLocalOnlyFile(String filename) {
        return LOCAL_ONLY_FILES.contains(filename) ||
                LOCAL_ONLY_FILES.stream()
                        .anyMatch(pattern -> filename.matches(pattern.replace("%d", "\\d+")));
    }

    public boolean requiresSync(String filename) {
        return !isLocalOnlyFile(filename) && !isNetworkPrimaryFile(filename);
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

    public Path resolvePathForRead(String filename) {
        // Special handling for network-primary files
        if (filename.equals(usersFilename) || filename.equals(holidayFilename)) {
            // Prefer network path for these files
            return networkAvailable.get() ?
                    networkPath.resolve(loginPath).resolve(filename) :
                    installPath.resolve(loginPath).resolve(filename);
        }

        // Determine the appropriate base path
        Path basePath = networkAvailable.get() ? networkPath : installPath;

        // Specific resolution for different file types
        if (filename.startsWith("session_")) {
            return installPath.resolve(userSession).resolve(filename);
        }
        if (filename.startsWith("worktime_")) {
            return installPath.resolve(userWorktime).resolve(filename);
        }
        if (filename.startsWith("registru_")) {
            return installPath.resolve(userRegister).resolve(filename);
        }
        if (filename.startsWith("general_worktime_")) {
            return installPath.resolve(adminWorktime).resolve(filename);
        }
        if (filename.startsWith("admin_registru_")) {
            return installPath.resolve(adminRegister).resolve(filename);
        }
        if (filename.startsWith("admin_bonus_")) {
            return installPath.resolve(adminBonus).resolve(filename);
        }

        // Fallback to local installation path
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

    private void determineActivePath() {
        // Try network path first
        if (isPathAccessible(networkPath)) {
            activePath = networkPath;
            LoggerUtil.info(this.getClass(), "Using network path: " + networkPath);
            return;
        }
        LoggerUtil.warn(this.getClass(), "Network path not accessible: " + networkPath);

        // Try installation path
        if (isPathAccessible(installPath)) {
            activePath = installPath;
            LoggerUtil.info(this.getClass(), "Using installation path: " + installPath);
            return;
        }
        LoggerUtil.warn(this.getClass(), "Installation path not accessible: " + installPath);

        // Try development path
        if (isPathAccessible(devPath)) {
            activePath = devPath;
            LoggerUtil.info(this.getClass(), "Using development path: " + devPath);
            return;
        }
        LoggerUtil.warn(this.getClass(), "Development path not accessible: " + devPath);

        // If no path is accessible, create structure in installation path
        createStructureInInstallPath();
    }

    private boolean isPathAccessible(Path path) {
        try {
            return Files.exists(path) && Files.isWritable(path);
        } catch (SecurityException e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Security error checking path %s: %s",
                            path, e.getMessage()));
            return false;
        }
    }

    private void createStructureInInstallPath() {
        try {
            Files.createDirectories(installPath);
            activePath = installPath;
            LoggerUtil.info(this.getClass(),
                    "Created and using installation path: " + installPath);
        } catch (Exception e) {
            String errorMsg = "Failed to create installation directory structure";
            LoggerUtil.error(this.getClass(), errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public Path getUsersJsonPath() {
        return activePath.resolve(loginPath).resolve(usersFilename);
    }

    public Path getHolidayListPath() {
        return activePath.resolve(login).resolve(holidayFilename);
    }

    public Path getSessionFilePath(String username, Integer userId) {
        return activePath.resolve(userSession).resolve(String.format(sessionFormat, username, userId));
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
        return isPathAccessible(networkPath);
    }

    public boolean isLocalAvailable() {
        return isPathAccessible(installPath);
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