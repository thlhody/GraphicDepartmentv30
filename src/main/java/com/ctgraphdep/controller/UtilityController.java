package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.service.BackupService;
import com.ctgraphdep.fileOperations.service.BackupUtilityService;
import com.ctgraphdep.fileOperations.events.BackupEventListener;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.session.service.SessionMidnightHandler;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.monitoring.MonitoringStateService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.merge.login.interfaces.LoginMergeService;
import com.ctgraphdep.merge.login.LoginMergeStrategy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * COMPLETELY REFACTORED: Comprehensive user utility controller for self-service diagnostics and recovery.
 * Since each PC runs one app instance with one user, this provides complete user self-service capabilities:
 * - Backup management and recovery
 * - Cache diagnostics and emergency reset
 * - Session management and troubleshooting
 * - System health monitoring
 * - File operation diagnostics
 */
@Controller
@RequestMapping("/utility")
public class UtilityController extends BaseController {

    // EXPANDED DEPENDENCIES - All services needed for complete user utilities
    private final BackupUtilityService backupUtilityService;
    private final BackupService backupService;
    private final AllUsersCacheService allUsersCacheService;
    private final SessionMidnightHandler sessionMidnightHandler;
    private final SchedulerHealthMonitor schedulerHealthMonitor;
    private final MonitoringStateService monitoringStateService;
    private final BackupEventListener backupEventListener;
    private final MainDefaultUserContextService mainDefaultUserContextService;
    private final PathConfig pathConfig;
    private final LoginMergeService loginMergeService;
    private final LoginMergeStrategy loginMergeStrategy;

    public UtilityController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            BackupUtilityService backupUtilityService,
            BackupService backupService,
            AllUsersCacheService allUsersCacheService,
            SessionMidnightHandler sessionMidnightHandler,
            SchedulerHealthMonitor schedulerHealthMonitor,
            MonitoringStateService monitoringStateService,
            BackupEventListener backupEventListener,
            MainDefaultUserContextService mainDefaultUserContextService,
            PathConfig pathConfig,
            LoginMergeService loginMergeService,
            LoginMergeStrategy loginMergeStrategy) {

        super(userService, folderStatus, timeValidationService);
        this.backupUtilityService = backupUtilityService;
        this.backupService = backupService;
        this.allUsersCacheService = allUsersCacheService;
        this.sessionMidnightHandler = sessionMidnightHandler;
        this.schedulerHealthMonitor = schedulerHealthMonitor;
        this.monitoringStateService = monitoringStateService;
        this.backupEventListener = backupEventListener;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        this.pathConfig = pathConfig;
        this.loginMergeService = loginMergeService;
        this.loginMergeStrategy = loginMergeStrategy;
    }

    /**
     * Main utility page with card-based layout
     */
    @GetMapping
    public String utilityPage(Authentication authentication, Model model) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            model.addAttribute("currentTime", getStandardCurrentDateTime());
            model.addAttribute("userId", currentUser.getUserId());
            LoggerUtil.info(this.getClass(), "User " + currentUser.getUsername() + " accessed utilities page");

            return "utility";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading utility page: " + e.getMessage(), e);
            model.addAttribute("error", "Failed to load utilities page");
            return "error";
        }
    }

    // ========================================================================
    // BACKUP MANAGEMENT SECTION (Enhanced)
    // ========================================================================

    /**
     * List available backups for the current user
     */
    @GetMapping("/backups/list")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listUserBackups(
            Authentication authentication,
            @RequestParam String fileType) {

        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, LocalDateTime> backups = backupUtilityService.listAvailableBackups(currentUser.getUsername(), fileType);

            List<Map<String, Object>> backupList = new ArrayList<>();
            for (Map.Entry<String, LocalDateTime> entry : backups.entrySet()) {
                Map<String, Object> backupInfo = new HashMap<>();
                backupInfo.put("path", entry.getKey());
                backupInfo.put("displayPath", formatDisplayPath(entry.getKey()));
                backupInfo.put("timestamp", entry.getValue());
                backupInfo.put("formattedDate", entry.getValue().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

                Map<String, Object> metadata = backupUtilityService.getBackupMetadata(entry.getKey());
                backupInfo.putAll(metadata);

                backupList.add(backupInfo);
            }

            backupList.sort((a, b) -> {
                LocalDateTime timeA = (LocalDateTime) a.get("timestamp");
                LocalDateTime timeB = (LocalDateTime) b.get("timestamp");
                return timeB.compareTo(timeA);
            });

            response.put("success", true);
            response.put("backups", backupList);
            response.put("fileType", fileType);
            response.put("totalFound", backupList.size());

            LoggerUtil.info(this.getClass(), String.format("Listed %d backups for user %s, type %s",
                    backupList.size(), currentUser.getUsername(), fileType));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error listing backups: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error listing backups: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Restore a backup file
     */
    @PostMapping("/backups/restore")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restoreBackup(
            Authentication authentication,
            @RequestParam String backupPath,
            @RequestParam String fileType,
            @RequestParam int year,
            @RequestParam int month) {

        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            String targetPath = determineTargetPath(currentUser, fileType, year, month);
            var result = backupUtilityService.restoreFromBackup(backupPath, targetPath);

            if (result.isSuccess()) {
                response.put("success", true);
                response.put("message", "Backup restored successfully");
                response.put("restoredFile", targetPath);

                LoggerUtil.info(this.getClass(), String.format("User %s restored backup from %s to %s",
                        currentUser.getUsername(), backupPath, targetPath));
            } else {
                response.put("success", false);
                response.put("message", result.getErrorMessage().orElse("Restore operation failed"));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error restoring backup: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error restoring backup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Restore latest backup for a file type
     */
    @PostMapping("/backups/restore-latest")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restoreLatestBackup(
            Authentication authentication,
            @RequestParam String fileType,
            @RequestParam int year,
            @RequestParam int month) {

        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Determine original file path and create FilePath
            String targetPath = determineTargetPath(currentUser, fileType, year, month);
            FilePath originalPath = FilePath.local(Path.of(targetPath));

            // Determine criticality level using BackupService logic
            var criticalityLevel = FileTypeConstants.getCriticalityLevelForFilename(
                    originalPath.getPath().getFileName().toString());

            // Use BackupService to restore from latest backup
            var result = backupService.restoreFromLatestBackup(originalPath, criticalityLevel);

            if (result.isSuccess()) {
                response.put("success", true);
                response.put("message", "Latest backup restored successfully");
                response.put("restoredFile", targetPath);

                LoggerUtil.info(this.getClass(), String.format("User %s restored latest backup for %s (%d/%d)",
                        currentUser.getUsername(), fileType, year, month));
            } else {
                response.put("success", false);
                response.put("message", result.getErrorMessage().orElse("Latest backup restore failed"));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error restoring latest backup: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error restoring latest backup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Create a manual backup
     */
    @PostMapping("/backups/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createBackup(
            Authentication authentication,
            @RequestParam String fileType,
            @RequestParam int year,
            @RequestParam int month) {

        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            var result = backupUtilityService.createAdminBackup(
                    currentUser.getUsername(),
                    currentUser.getUserId(),
                    fileType,
                    year,
                    month
            );

            if (result.isSuccess()) {
                response.put("success", true);
                response.put("message", "Backup created successfully");
                response.put("backupPath", result.getFilePath().toString());

                LoggerUtil.info(this.getClass(), String.format("User %s created backup for %s (%d/%d)",
                        currentUser.getUsername(), fileType, year, month));
            } else {
                response.put("success", false);
                response.put("message", result.getErrorMessage().orElse("Backup creation failed"));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating backup: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error creating backup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Delete simple backup for cleanup
     */
    @DeleteMapping("/backups/cleanup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cleanupSimpleBackup(
            Authentication authentication,
            @RequestParam String fileType,
            @RequestParam int year,
            @RequestParam int month) {

        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            String targetPath = determineTargetPath(currentUser, fileType, year, month);
            FilePath originalPath = FilePath.local(Path.of(targetPath));

            // Delete simple backup using BackupService
            backupService.deleteSimpleBackup(originalPath);

            response.put("success", true);
            response.put("message", "Simple backup cleaned up successfully");

            LoggerUtil.info(this.getClass(), String.format("User %s cleaned up simple backup for %s (%d/%d)",
                    currentUser.getUsername(), fileType, year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error cleaning up backup: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error cleaning up backup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Get backup diagnostics
     */
    @GetMapping("/backups/diagnostics")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getBackupDiagnostics(
            Authentication authentication,
            @RequestParam String fileType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Get backup utility diagnostics
            String utilityDiagnostics = backupUtilityService.getBackupUtilityDiagnostics(fileType);

            // If specific file requested, get file-specific diagnostics
            String fileDiagnostics = null;
            if (year != null && month != null) {
                String targetPath = determineTargetPath(currentUser, fileType, year, month);
                FilePath originalPath = FilePath.local(Path.of(targetPath));
                fileDiagnostics = backupService.getBackupDiagnostics(originalPath);
            }

            // Get backup event diagnostics
            String eventDiagnostics = null;
            if (year != null && month != null) {
                String targetPath = determineTargetPath(currentUser, fileType, year, month);
                FilePath originalPath = FilePath.local(Path.of(targetPath));
                eventDiagnostics = backupEventListener.getBackupDiagnostics(originalPath);
            }

            response.put("success", true);
            response.put("utilityDiagnostics", utilityDiagnostics);
            response.put("fileDiagnostics", fileDiagnostics);
            response.put("eventDiagnostics", eventDiagnostics);
            response.put("fileType", fileType);

            LoggerUtil.info(this.getClass(), String.format("Generated backup diagnostics for user %s, type %s",
                    currentUser.getUsername(), fileType));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting backup diagnostics: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting backup diagnostics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Create memory backup for troubleshooting
     */
    @PostMapping("/backups/memory-backup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createMemoryBackup(
            Authentication authentication,
            @RequestParam String fileType,
            @RequestParam int year,
            @RequestParam int month) {

        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            String targetPath = determineTargetPath(currentUser, fileType, year, month);
            FilePath originalPath = FilePath.local(Path.of(targetPath));

            Optional<byte[]> memoryBackup = backupService.createMemoryBackup(originalPath);

            if (memoryBackup.isPresent()) {
                response.put("success", true);
                response.put("message", "Memory backup created successfully");
                response.put("backupSize", memoryBackup.get().length);
                response.put("timestamp", getStandardCurrentDateTime());

                LoggerUtil.info(this.getClass(), String.format("User %s created memory backup for %s (%d/%d), size: %d bytes",
                        currentUser.getUsername(), fileType, year, month, memoryBackup.get().length));
            } else {
                response.put("success", false);
                response.put("message", "Failed to create memory backup - file may not exist");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating memory backup: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error creating memory backup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // CACHE MANAGEMENT SECTION (Enhanced)
    // ========================================================================

    /**
     * Get status cache health information
     */
    @GetMapping("/cache/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            String cacheStatus = allUsersCacheService.getCacheStatus();

            response.put("success", true);
            response.put("cacheStatus", cacheStatus);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting cache status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting cache status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Perform manual cache validation
     */
    @PostMapping("/cache/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateCache() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Get current cache status for validation
            String cacheStatus = allUsersCacheService.getCacheStatus();
            boolean hasUserData = allUsersCacheService.hasUserData();
            int cachedUserCount = allUsersCacheService.getCachedUserCount();

            response.put("success", true);
            response.put("message", "Cache validation completed");
            response.put("hasUserData", hasUserData);
            response.put("cachedUserCount", cachedUserCount);
            response.put("cacheStatus", cacheStatus);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Manual cache validation performed - Users: " + cachedUserCount);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error validating cache: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error validating cache: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Check if cache has user data
     */
    @GetMapping("/cache/user-data-check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkUserData() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean hasUserData = allUsersCacheService.hasUserData();
            int cachedUserCount = allUsersCacheService.getCachedUserCount();

            response.put("success", true);
            response.put("hasUserData", hasUserData);
            response.put("cachedUserCount", cachedUserCount);
            response.put("message", hasUserData ? "Cache contains user data" : "Cache is empty");
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking user data: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error checking user data: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Get cached user count
     */
    @GetMapping("/cache/user-count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCachedUserCount() {
        Map<String, Object> response = new HashMap<>();

        try {
            int cachedUserCount = allUsersCacheService.getCachedUserCount();

            response.put("success", true);
            response.put("cachedUserCount", cachedUserCount);
            response.put("message", "Found " + cachedUserCount + " cached users");
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting cached user count: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting cached user count: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Refresh cache from UserService data
     */
    @PostMapping("/cache/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshCache() {
        Map<String, Object> response = new HashMap<>();

        try {
            int beforeCount = allUsersCacheService.getCachedUserCount();

            // Refresh all users from UserDataService
            allUsersCacheService.refreshAllUsersFromUserDataServiceWithCompleteData();
            allUsersCacheService.writeToFile();

            int afterCount = allUsersCacheService.getCachedUserCount();

            response.put("success", true);
            response.put("message", "Cache refreshed successfully");
            response.put("beforeCount", beforeCount);
            response.put("afterCount", afterCount);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), String.format("Cache refreshed: %d → %d users", beforeCount, afterCount));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error refreshing cache: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error refreshing cache: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Emergency cache reset
     */
    @PostMapping("/cache/emergency-reset")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> emergencyCacheReset(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Perform emergency cache reset using SessionMidnightHandler
            sessionMidnightHandler.performEmergencyCacheReset();

            response.put("success", true);
            response.put("message", "Emergency cache reset completed successfully");
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.warn(this.getClass(), "Emergency cache reset performed by user: " + currentUser.getUsername());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error performing emergency cache reset: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error performing emergency cache reset: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // SESSION MANAGEMENT SECTION (New)
    // ========================================================================

    /**
     * NEW: Perform manual session reset
     */
    @PostMapping("/session/manual-reset")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> performManualReset(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Perform manual reset using SessionMidnightHandler
            sessionMidnightHandler.performManualReset(currentUser.getUsername());

            response.put("success", true);
            response.put("message", "Manual session reset completed successfully");
            response.put("username", currentUser.getUsername());
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Manual session reset performed for user: " + currentUser.getUsername());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error performing manual reset: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error performing manual reset: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Get midnight reset status
     */
    @GetMapping("/session/reset-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getResetStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            String resetStatus = sessionMidnightHandler.getMidnightResetStatus();

            response.put("success", true);
            response.put("resetStatus", resetStatus);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting reset status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting reset status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Get user context status
     */
    @GetMapping("/session/context-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserContextStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            User currentUser = mainDefaultUserContextService.getOriginalUser();
            String currentUsername = mainDefaultUserContextService.getCurrentUsername();
            boolean isHealthy = mainDefaultUserContextService.isCacheHealthy();
            boolean hasRealUser = mainDefaultUserContextService.hasRealUser();
            boolean isInitialized = mainDefaultUserContextService.isCacheInitialized();

            response.put("success", true);
            response.put("currentUsername", currentUsername);
            response.put("currentUserId", currentUser != null ? currentUser.getUserId() : null);
            response.put("isHealthy", isHealthy);
            response.put("hasRealUser", hasRealUser);
            response.put("isInitialized", isInitialized);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting user context status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting user context status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // SYSTEM HEALTH MONITORING SECTION (New)
    // ========================================================================

    /**
     * NEW: Get overall system health
     */
    @GetMapping("/health/overall")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getOverallHealth() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Boolean> healthStatus = schedulerHealthMonitor.getHealthStatus();

            response.put("success", true);
            response.put("healthStatus", healthStatus);
            response.put("overallHealthy", healthStatus.values().stream().allMatch(Boolean::booleanValue));
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting overall health: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting overall health: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Get detailed task health information
     */
    @GetMapping("/health/tasks")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTaskHealth() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Boolean> healthStatus = schedulerHealthMonitor.getHealthStatus();
            Map<String, Object> taskDetails = new HashMap<>();

            // Get detailed status for each task
            for (String taskId : healthStatus.keySet()) {
                boolean isHealthy = schedulerHealthMonitor.isTaskHealthy(taskId);
                SchedulerHealthMonitor.TaskStatus taskStatus = schedulerHealthMonitor.getTaskStatus(taskId);

                Map<String, Object> taskInfo = new HashMap<>();
                taskInfo.put("healthy", isHealthy);
                if (taskStatus != null) {
                    taskInfo.put("lastExecution", taskStatus.getLastExecutionTime());
                    taskInfo.put("consecutiveFailures", taskStatus.getConsecutiveFailures());
                    taskInfo.put("minutesSinceLastExecution", taskStatus.getMinutesSinceLastExecution());
                    taskInfo.put("lastError", taskStatus.getLastError());
                }

                taskDetails.put(taskId, taskInfo);
            }

            response.put("success", true);
            response.put("healthStatus", healthStatus);
            response.put("taskDetails", taskDetails);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting task health: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting task health: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Get monitoring state for current user
     */
    @GetMapping("/health/monitoring-state")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMonitoringState(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            String username = currentUser.getUsername();
            String monitoringMode = monitoringStateService.getMonitoringMode(username);
            boolean scheduleMonitoring = monitoringStateService.isInScheduleMonitoring(username);
            boolean hourlyMonitoring = monitoringStateService.isInHourlyMonitoring(username);
            boolean tempStopMonitoring = monitoringStateService.isInTempStopMonitoring(username);
            boolean continuedAfterSchedule = monitoringStateService.hasContinuedAfterSchedule(username);

            response.put("success", true);
            response.put("username", username);
            response.put("monitoringMode", monitoringMode);
            response.put("scheduleMonitoring", scheduleMonitoring);
            response.put("hourlyMonitoring", hourlyMonitoring);
            response.put("tempStopMonitoring", tempStopMonitoring);
            response.put("continuedAfterSchedule", continuedAfterSchedule);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting monitoring state: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting monitoring state: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // DIAGNOSTICS & TROUBLESHOOTING SECTION (New)
    // ========================================================================

    /**
     * NEW: Get backup event diagnostics
     */
    @GetMapping("/diagnostics/backup-events")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getBackupEventDiagnostics(
            Authentication authentication,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            String diagnostics = "Backup Event Diagnostics:\n";

            if (fileType != null && year != null && month != null) {
                String targetPath = determineTargetPath(currentUser, fileType, year, month);
                FilePath originalPath = FilePath.local(Path.of(targetPath));
                diagnostics = backupEventListener.getBackupDiagnostics(originalPath);
            } else {
                diagnostics += "General backup event system status\n";
                diagnostics += "Request specific file for detailed diagnostics\n";
            }

            response.put("success", true);
            response.put("diagnostics", diagnostics);
            response.put("fileType", fileType);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting backup event diagnostics: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting backup event diagnostics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Get comprehensive system summary
     */
    @GetMapping("/diagnostics/system-summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSystemSummary(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> summary = new HashMap<>();

            // User Context Information
            summary.put("currentUser", currentUser.getUsername());
            summary.put("userId", currentUser.getUserId());
            summary.put("userRole", currentUser.getRole());

            // Cache Information
            summary.put("cacheHealthy", mainDefaultUserContextService.isCacheHealthy());
            summary.put("cachedUserCount", allUsersCacheService.getCachedUserCount());
            summary.put("hasUserData", allUsersCacheService.hasUserData());

            // System Health
            Map<String, Boolean> healthStatus = schedulerHealthMonitor.getHealthStatus();
            summary.put("systemHealthy", healthStatus.values().stream().allMatch(Boolean::booleanValue));
            summary.put("healthyTasks", healthStatus.size());

            // Monitoring State
            String monitoringMode = monitoringStateService.getMonitoringMode(currentUser.getUsername());
            summary.put("monitoringMode", monitoringMode);

            // Session Information
            String resetStatus = sessionMidnightHandler.getMidnightResetStatus();
            summary.put("sessionResetStatus", resetStatus);

            response.put("success", true);
            response.put("summary", summary);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting system summary: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting system summary: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // MERGE MANAGEMENT SECTION (New)
    // ========================================================================

    /**
     * NEW: Get pending merge status for current user
     * Checks if there are pending merge operations stuck in the queue
     */
    @GetMapping("/merge/pending-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPendingMergeStatus(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            String username = currentUser.getUsername();
            boolean hasPendingMerges = loginMergeService.hasPendingMerges(username);
            int pendingCount = loginMergeService.getPendingMergeCount();

            response.put("success", true);
            response.put("username", username);
            response.put("hasPendingMerges", hasPendingMerges);
            response.put("pendingCount", pendingCount);
            response.put("timestamp", getStandardCurrentDateTime());

            // Warning message if user has pending merges
            if (hasPendingMerges) {
                response.put("warning", "You have pending merge operations that may need clearing if stuck");
                response.put("action", "Consider using the Clear Pending Merges utility if these operations are not completing");
            } else {
                response.put("message", "No pending merge operations");
            }

            LoggerUtil.info(this.getClass(), String.format("Pending merge status checked for user %s: %d pending",
                    username, pendingCount));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting pending merge status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting pending merge status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Get total count of pending merge operations
     * Useful for dashboard or status indicators
     */
    @GetMapping("/merge/pending-count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPendingMergeCount() {
        Map<String, Object> response = new HashMap<>();

        try {
            int pendingCount = loginMergeService.getPendingMergeCount();

            response.put("success", true);
            response.put("pendingCount", pendingCount);
            response.put("message", pendingCount > 0
                ? String.format("Found %d pending merge operation(s)", pendingCount)
                : "No pending merge operations");
            response.put("timestamp", getStandardCurrentDateTime());

            // Add severity level based on count
            if (pendingCount > 10) {
                response.put("severity", "high");
                response.put("recommendation", "High number of pending merges - consider clearing queue");
            } else if (pendingCount > 5) {
                response.put("severity", "medium");
                response.put("recommendation", "Moderate pending merges - monitor if they complete");
            } else if (pendingCount > 0) {
                response.put("severity", "low");
                response.put("recommendation", "Low pending merges - should complete automatically");
            } else {
                response.put("severity", "none");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting pending merge count: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting pending merge count: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Clear all pending merge operations
     * Use this when merge operations are stuck and not completing
     * WARNING: Only use if merges are confirmed stuck/failed
     */
    @PostMapping("/merge/clear-pending")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearPendingMerges(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            int countBeforeClear = loginMergeService.getPendingMergeCount();

            // Clear all pending merges
            loginMergeService.clearPendingMerges();

            response.put("success", true);
            response.put("message", "All pending merge operations have been cleared");
            response.put("clearedCount", countBeforeClear);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.warn(this.getClass(), String.format("User %s cleared %d pending merge operations",
                    currentUser.getUsername(), countBeforeClear));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error clearing pending merges: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error clearing pending merges: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Get login merge strategy status
     * Shows current login count and next merge strategy
     */
    @GetMapping("/merge/strategy-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMergeStrategyStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            int loginCount = loginMergeStrategy.getCurrentLoginCount();
            boolean shouldPerformFullMerge = loginMergeStrategy.shouldPerformFullMerge();
            boolean shouldPerformFastRefresh = loginMergeStrategy.shouldPerformFastCacheRefresh();
            boolean isFirstLogin = loginMergeStrategy.isFirstLoginOfDay();
            String statusDescription = loginMergeStrategy.getStatus();
            String performanceBenefit = loginMergeStrategy.getPerformanceBenefit();

            response.put("success", true);
            response.put("loginCount", loginCount);
            response.put("shouldPerformFullMerge", shouldPerformFullMerge);
            response.put("shouldPerformFastRefresh", shouldPerformFastRefresh);
            response.put("isFirstLogin", isFirstLogin);
            response.put("statusDescription", statusDescription);
            response.put("performanceBenefit", performanceBenefit);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Merge strategy status retrieved: " + statusDescription);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting merge strategy status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting merge strategy status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Force full merge on next login
     * Resets login count to 0, causing next login to perform full merge
     */
    @PostMapping("/merge/force-full-merge")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> forceFullMergeOnNextLogin(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            int beforeCount = loginMergeStrategy.getCurrentLoginCount();

            // Force full merge on next login
            loginMergeStrategy.forceFullMergeOnNextLogin();

            response.put("success", true);
            response.put("message", "Next login will perform full merge (data refresh)");
            response.put("previousLoginCount", beforeCount);
            response.put("newLoginCount", loginMergeStrategy.getCurrentLoginCount());
            response.put("nextStrategy", "Full Merge (~7 seconds)");
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.warn(this.getClass(), String.format("User %s forced full merge on next login (reset from count %d to 0)",
                    currentUser.getUsername(), beforeCount));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error forcing full merge: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error forcing full merge: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * NEW: Trigger full merge immediately
     * Sets login count to 1, simulating first login
     * WARNING: This sets the counter to 1, not 0!
     */
    @PostMapping("/merge/trigger-merge-now")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> triggerFullMergeNow(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Trigger full merge now
            loginMergeStrategy.triggerFullMergeNow();

            response.put("success", true);
            response.put("message", "Merge strategy set to trigger full merge");
            response.put("currentLoginCount", loginMergeStrategy.getCurrentLoginCount());
            response.put("willPerformFullMerge", loginMergeStrategy.shouldPerformFullMerge());
            response.put("status", loginMergeStrategy.getStatus());
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "User " + currentUser.getUsername() + " triggered full merge strategy");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error triggering full merge: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error triggering full merge: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Determine target file path based on file type and user
     */
    private String determineTargetPath(User user, String fileType, int year, int month) {
        return switch (fileType.toLowerCase()) {
            case FileTypeConstants.WORKTIME_PREFIX -> pathConfig.getLocalWorktimePath(user.getUsername(), year, month).toString();
            case FileTypeConstants.REGISTER_PREFIX ->
                    pathConfig.getLocalRegisterPath(user.getUsername(), user.getUserId(), year, month).toString();
            case FileTypeConstants.SESSION_PREFIX -> pathConfig.getLocalSessionPath(user.getUsername(), user.getUserId()).toString();
            case FileTypeConstants.CHECK_REGISTER_PREFIX ->
                    pathConfig.getLocalCheckRegisterPath(user.getUsername(), user.getUserId(), year, month).toString();
            case FileTypeConstants.TIMEOFF_TRACKER_PREFIX ->
                    pathConfig.getLocalTimeOffTrackerPath(user.getUsername(), user.getUserId(), year).toString();
            default -> throw new IllegalArgumentException("Unknown file type: " + fileType);
        };
    }

    /**
     * Formats a full backup path to show only the relative path from the backup directory
     */
    private String formatDisplayPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return fullPath;
        }

        String normalizedPath = fullPath.replace('/', '\\');
        String backupMarker = "\\backup\\";
        int backupIndex = normalizedPath.toLowerCase().indexOf(backupMarker);

        if (backupIndex >= 0) {
            return "backup" + normalizedPath.substring(backupIndex + backupMarker.length() - 1);
        }

        int lastSeparator = Math.max(normalizedPath.lastIndexOf('\\'), normalizedPath.lastIndexOf('/'));
        if (lastSeparator >= 0) {
            return normalizedPath.substring(lastSeparator + 1);
        }

        return fullPath;
    }
}