package com.ctgraphdep.controller.utility;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.events.BackupEventListener;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.session.service.SessionMidnightHandler;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.monitoring.MonitoringStateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for diagnostics and troubleshooting utilities.
 * Handles backup event diagnostics and comprehensive system summaries.
 */
@RestController
@RequestMapping("/utility/diagnostics")
public class DiagnosticsUtilityController extends BaseController {

    private final BackupEventListener backupEventListener;
    private final PathConfig pathConfig;
    private final SchedulerHealthMonitor schedulerHealthMonitor;
    private final AllUsersCacheService allUsersCacheService;
    private final MainDefaultUserContextService mainDefaultUserContextService;
    private final SessionMidnightHandler sessionMidnightHandler;
    private final MonitoringStateService monitoringStateService;

    public DiagnosticsUtilityController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            BackupEventListener backupEventListener,
            PathConfig pathConfig,
            SchedulerHealthMonitor schedulerHealthMonitor,
            AllUsersCacheService allUsersCacheService,
            MainDefaultUserContextService mainDefaultUserContextService,
            SessionMidnightHandler sessionMidnightHandler,
            MonitoringStateService monitoringStateService) {

        super(userService, folderStatus, timeValidationService);
        this.backupEventListener = backupEventListener;
        this.pathConfig = pathConfig;
        this.schedulerHealthMonitor = schedulerHealthMonitor;
        this.allUsersCacheService = allUsersCacheService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        this.sessionMidnightHandler = sessionMidnightHandler;
        this.monitoringStateService = monitoringStateService;
    }

    // ========================================================================
    // DIAGNOSTICS & TROUBLESHOOTING ENDPOINTS
    // ========================================================================

    /**
     * Get backup event diagnostics
     */
    @GetMapping("/backup-events")
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
     * Get comprehensive system summary
     */
    @GetMapping("/system-summary")
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
}
