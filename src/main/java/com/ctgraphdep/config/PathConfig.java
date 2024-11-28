// PathConfig.java
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

@Getter
@Setter
@Component
@Configuration
@ConfigurationProperties(prefix = "dbj")
public class PathConfig {

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

    @Value("${dbj.dir.format.register:registru_%s_%d.json}")
    private String registerFormat;

    @Value("${dbj.dir.format.admin.worktime:general_worktime_%d_%02d.json}")
    private String adminWorktimeFormat;

    @Value("${dbj.dir.format.admin.register:general_registru_%d_%02d.json}")
    private String adminRegisterFormat;

    @Value("${dbj.dir.format.admin.bonus:general_bonus_%d_%02d.json}")
    private String adminBonusFormat;

    @Value("${dbj.dir.format.holiday:paid_holiday_list.json}")
    private String holidayListFormat;

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

    @Value("${dbj.admin.holiday}")
    private String adminHoliday;

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
        admin.put("holiday", adminHoliday);
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
            return Files.exists(path);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking path %s: %s", path, e.getMessage()));
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

    // Path resolution methods
    public Path getUsersJsonPath() {
        return activePath.resolve(login).resolve(usersFilename);
    }

    public Path getSessionFilePath(String username, Integer userId) {
        return activePath.resolve(user.get("session"))
                .resolve(String.format(sessionFormat, username, userId));
    }

    public Path getUserWorktimeFilePath(String username, int year, int month) {
        return activePath.resolve(user.get("worktime"))
                .resolve(String.format(worktimeFormat, username, year, month));
    }

    public Path getUserRegisterPath(String username, Integer userId,int year, int month) {
        return activePath.resolve(user.get("register"))
                .resolve(String.format(registerFormat, username, userId, year, month));
    }

    public Path getHolidayListPath() {
        return activePath.resolve(admin.get("holiday"))
                .resolve(holidayListFormat);
    }

    public Path getAdminWorktimePath(int year, int month) {
        return activePath.resolve(admin.get("worktime"))
                .resolve(String.format(adminWorktimeFormat, year, month));
    }

    public Path getAdminRegisterPath(int year, int month) {
        return activePath.resolve(admin.get("register"))
                .resolve(String.format(adminRegisterFormat, year, month));
    }

    public Path getAdminBonusPath(int year, int month) {
        return activePath.resolve(admin.get("bonus"))
                .resolve(String.format(adminBonusFormat, year, month));
    }

    // Status checks
    public boolean isNetworkAvailable() {
        return isPathAccessible(networkPath);
    }

    public boolean isLocalAvailable() {
        return isPathAccessible(installPath);
    }

    public List<String> getMissingDirectories() {
        return Collections.unmodifiableList(missingDirectories);
    }

    // Get Local Path (Installation Path)
    public Path getLocalPath() {
        return installPath;
    }

    public String getUserSession() {
        return user.get("session");
    }

    private void verifyDirectoryStructure() {
        if (!verifyEnabled) {
            LoggerUtil.info(this.getClass(), "Directory verification disabled");
            return;
        }

        missingDirectories.clear();

        List<String> pathsToVerify = new ArrayList<>();
        pathsToVerify.addAll(user.values());
        pathsToVerify.addAll(admin.values());
        pathsToVerify.add(login);

        for (String dirPath : pathsToVerify) {
            Path fullPath = activePath.resolve(dirPath);
            if (!Files.exists(fullPath)) {
                missingDirectories.add(dirPath);
                if (createMissing) {
                    try {
                        Files.createDirectories(fullPath);
                        if (dirPath.endsWith("login")) {
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
}