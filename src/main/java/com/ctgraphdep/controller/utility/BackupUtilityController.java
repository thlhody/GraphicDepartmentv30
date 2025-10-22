package com.ctgraphdep.controller.utility;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.events.BackupEventListener;
import com.ctgraphdep.fileOperations.service.BackupService;
import com.ctgraphdep.fileOperations.service.BackupUtilityService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * REST controller for backup management utilities.
 * Handles backup listing, restoration, creation, and diagnostics.
 */
@RestController
@RequestMapping("/utility/backups")
public class BackupUtilityController extends BaseController {

    private final BackupUtilityService backupUtilityService;
    private final BackupService backupService;
    private final BackupEventListener backupEventListener;
    private final PathConfig pathConfig;

    public BackupUtilityController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            BackupUtilityService backupUtilityService,
            BackupService backupService,
            BackupEventListener backupEventListener,
            PathConfig pathConfig) {

        super(userService, folderStatus, timeValidationService);
        this.backupUtilityService = backupUtilityService;
        this.backupService = backupService;
        this.backupEventListener = backupEventListener;
        this.pathConfig = pathConfig;
    }

    // ========================================================================
    // BACKUP MANAGEMENT ENDPOINTS
    // ========================================================================

    /**
     * List available backups for the current user
     */
    @GetMapping("/list")
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
    @PostMapping("/restore")
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
     * Restore latest backup for a file type
     */
    @PostMapping("/restore-latest")
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
    @PostMapping("/create")
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
     * Delete simple backup for cleanup
     */
    @DeleteMapping("/cleanup")
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
     * Get backup diagnostics
     */
    @GetMapping("/diagnostics")
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
     * Create memory backup for troubleshooting
     */
    @PostMapping("/memory-backup")
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
