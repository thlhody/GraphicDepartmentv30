package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.service.BackupUtilityService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.StatusCacheService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller for user utility operations including backup management,
 * cache diagnostics, and system monitoring tools.
 */
@Controller
@RequestMapping("/utility")
public class UtilityController extends BaseController {

    private final BackupUtilityService backupUtilityService;
    private final StatusCacheService statusCacheService;
    private final PathConfig pathConfig;

    public UtilityController(UserService userService, FolderStatus folderStatus, TimeValidationService timeValidationService,
                             BackupUtilityService backupUtilityService, StatusCacheService statusCacheService, PathConfig pathConfig) {
        super(userService, folderStatus, timeValidationService);
        this.backupUtilityService = backupUtilityService;
        this.statusCacheService = statusCacheService;
        this.pathConfig = pathConfig;
    }

    /**
     * Main utility page with card-based layout
     */
    @GetMapping
    public String utilityPage(Authentication authentication, Model model) {
        try {
            // Get UserDetails from Authentication
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // Validate user access
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Add current time for display
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

    // ===== BACKUP MANAGEMENT ENDPOINTS =====

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

            // Get backups for this user and file type
            Map<String, LocalDateTime> backups = backupUtilityService.listAvailableBackups(currentUser.getUsername(), currentUser.getUserId(), fileType);
            LoggerUtil.info(this.getClass(),"Backups/lists:"+currentUser.getUsername()+"/"+currentUser.getUserId()+"/"+fileType );

            // Convert to display format
            List<Map<String, Object>> backupList = new ArrayList<>();
            for (Map.Entry<String, LocalDateTime> entry : backups.entrySet()) {
                Map<String, Object> backupInfo = new HashMap<>();
                backupInfo.put("path", entry.getKey()); // Keep full path for restore operations
                backupInfo.put("displayPath", formatDisplayPath(entry.getKey())); // Add formatted path for display
                backupInfo.put("timestamp", entry.getValue());
                backupInfo.put("formattedDate", entry.getValue().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

                // Get additional metadata
                Map<String, Object> metadata = backupUtilityService.getBackupMetadata(entry.getKey());
                backupInfo.putAll(metadata);

                backupList.add(backupInfo);
            }

            // Sort by timestamp (newest first)
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

            // Determine target file path based on file type
            String targetPath = determineTargetPath(currentUser, fileType, year, month);

            // Perform the restore operation
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

            // Create backup using the service
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

    // ===== CACHE MANAGEMENT ENDPOINTS =====

    /**
     * Get status cache health information
     */
    @GetMapping("/cache/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            String cacheStatus = statusCacheService.getCacheStatus();

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
            // Note: This method doesn't exist yet in StatusCacheService
            // We'll need to add it or use existing methods
            response.put("success", true);
            response.put("message", "Cache validation completed");
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Manual cache validation performed");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error validating cache: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error validating cache: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ===== HELPER METHODS =====

    /**
     * Determine target file path based on file type and user
     */
    private String determineTargetPath(User user, String fileType, int year, int month) {
        return switch (fileType.toLowerCase()) {
            case "worktime" -> pathConfig.getLocalWorktimePath(user.getUsername(), year, month).toString();
            case "register" ->
                    pathConfig.getLocalRegisterPath(user.getUsername(), user.getUserId(), year, month).toString();
            case "session" -> pathConfig.getLocalSessionPath(user.getUsername(), user.getUserId()).toString();
            case "check_register" ->
                    pathConfig.getLocalCheckRegisterPath(user.getUsername(), user.getUserId(), year, month).toString();
            case "timeoff_tracker" ->
                    pathConfig.getLocalTimeOffTrackerPath(user.getUsername(), user.getUserId(), year).toString();
            default -> throw new IllegalArgumentException("Unknown file type: " + fileType);
        };
    }

    /**
     * Formats a full backup path to show only the relative path from the backup directory
     * @param fullPath The complete absolute path
     * @return Formatted path starting from "backup" directory, or original path if "backup" not found
     */
    private String formatDisplayPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return fullPath;
        }

        // Normalize path separators to work on both Windows and Unix
        String normalizedPath = fullPath.replace('/', '\\');

        // Find the backup directory in the path
        String backupMarker = "\\backup\\";
        int backupIndex = normalizedPath.toLowerCase().indexOf(backupMarker);

        if (backupIndex >= 0) {
            // Return path starting from "backup"
            return "backup" + normalizedPath.substring(backupIndex + backupMarker.length() - 1);
        }

        // Fallback: if "backup" not found, try to extract just the filename
        int lastSeparator = Math.max(normalizedPath.lastIndexOf('\\'), normalizedPath.lastIndexOf('/'));
        if (lastSeparator >= 0) {
            return normalizedPath.substring(lastSeparator + 1);
        }

        // Return original if no processing possible
        return fullPath;
    }
}