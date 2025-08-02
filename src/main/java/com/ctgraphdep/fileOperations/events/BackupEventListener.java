package com.ctgraphdep.fileOperations.events;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.config.FileTypeConstants.CriticalityLevel;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.service.BackupService;
import com.ctgraphdep.monitoring.BackupEventMonitor;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REFACTORED: Event listener that handles file operation events and creates backups accordingly.
 * This component decouples backup creation from file writing operations.
 * Key Changes:
 * - Now uses FileTypeConstants.CriticalityLevel enum instead of BackupService.CriticalityLevel
 * - Leverages FileTypeConstants.getCriticalityLevelForFilename() for centralized logic
 * - Simplified criticality determination - one line instead of complex hardcoded rules
 * - Enhanced logging with file type diagnostics
 * - Better integration with centralized file type classification system
 */
@Component
public class BackupEventListener {

    private final BackupService backupService;
    private final ApplicationEventPublisher eventPublisher;
    private final BackupEventMonitor backupEventMonitor;
    private final ConcurrentHashMap<String, Boolean> userBackupSyncedThisSession = new ConcurrentHashMap<>();

    @Autowired
    public BackupEventListener(BackupService backupService,
                               ApplicationEventPublisher eventPublisher,
                               BackupEventMonitor backupEventMonitor) {
        this.backupService = backupService;
        this.eventPublisher = eventPublisher;
        this.backupEventMonitor = backupEventMonitor;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * REFACTORED: Handles successful file write events and creates backups if requested.
     * This method runs asynchronously to avoid blocking the main file write operation.
     * Now uses FileTypeConstants for all criticality decisions.
     */
    @EventListener
    @Async("backupTaskExecutor")
    public void handleFileWriteSuccess(FileWriteSuccessEvent event) {
        // Check if backup should be created
        if (!event.isShouldCreateBackup()) {
            LoggerUtil.debug(this.getClass(), String.format("Skipping backup for %s - backup disabled for this operation (Event ID: %s)",
                    event.getFilePath().getPath().getFileName(), event.getEventId()));
            return;
        }

        LoggerUtil.info(this.getClass(), String.format("Processing backup request for %s (Event ID: %s)",
                event.getFilePath().getPath().getFileName(), event.getEventId()));

        long backupStartTime = System.currentTimeMillis();
        String backupPath = null;
        String errorMessage = null;
        boolean backupSuccess = false;

        try {
            // Determine criticality level using FileTypeConstants
            CriticalityLevel criticalityLevel = determineCriticalityLevel(event.getFilePath());

            // Enhanced logging with file type information
            String fileName = event.getFilePath().getPath().getFileName().toString();
            String fileType = FileTypeConstants.extractFileTypeFromFilename(fileName);

            LoggerUtil.info(this.getClass(), String.format("Creating %s backup for %s (file type: %s, user: %s, Event ID: %s)",
                    criticalityLevel,
                    fileName,
                    fileType != null ? fileType : "unknown",
                    event.getUsername(),
                    event.getEventId()));

            // Create the backup using the determined criticality level
            FileOperationResult backupResult = backupService.createBackup(event.getFilePath(), criticalityLevel);

            if (backupResult.isSuccess()) {
                backupSuccess = true;
                backupPath = backupResult.getFilePath().toString();

                // Record successful backup in monitor
                backupEventMonitor.recordBackupCreated();

                LoggerUtil.info(this.getClass(), String.format(
                        "Event-driven backup created successfully: %s (level: %s, max backups: %d, user: %s, Event ID: %s)",
                        backupPath, criticalityLevel, FileTypeConstants.getMaxBackups(criticalityLevel),
                        event.getUsername(), event.getEventId()));

                // For high criticality files, also sync backups to network
                if (criticalityLevel == CriticalityLevel.LEVEL3_HIGH && event.getUsername() != null) {
                    String username = event.getUsername();

                    // Check if we've already synced backups for this user in this session
                    boolean alreadySynced = userBackupSyncedThisSession.getOrDefault(username, false);

                    if (!alreadySynced) {
                        try {
                            LoggerUtil.info(this.getClass(), String.format(
                                    "First Level 3 backup for user %s this session - syncing to network (Event ID: %s)",
                                    username, event.getEventId()));

                            // Mark this user as having synced backups this session
                            userBackupSyncedThisSession.put(username, true);

                            // Add a small delay to ensure backup files are completely written
                            CompletableFuture.runAsync(() -> {
                                try {
                                    Thread.sleep(1000); // 1-second delay

                                    // Pass the file type to sync only the relevant subdirectory
                                    backupService.syncBackupsToNetworkByType(event.getUsername(), criticalityLevel, fileType);
                                    LoggerUtil.info(this.getClass(), String.format(
                                            "Synced %s backups to network for user %s (Event ID: %s)",
                                            fileType, event.getUsername(), event.getEventId()));
                                } catch (Exception syncException) {
                                    LoggerUtil.warn(this.getClass(), String.format(
                                            "Failed to sync %s backups to network: %s (Event ID: %s)",
                                            fileType, syncException.getMessage(), event.getEventId()));
                                }
                            });
                        } catch (Exception syncException) {
                            LoggerUtil.warn(this.getClass(), String.format(
                                    "Failed to schedule backup sync: %s (Event ID: %s)",
                                    syncException.getMessage(), event.getEventId()));
                        }
                    } else {
                        LoggerUtil.debug(this.getClass(), String.format(
                                "Skipping backup sync for user %s - already synced this session (Event ID: %s)",
                                username, event.getEventId()));
                    }
                }
            } else {
                errorMessage = backupResult.getErrorMessage().orElse("Unknown backup error");

                // Record backup failure in monitor
                backupEventMonitor.recordBackupFailure();

                LoggerUtil.error(this.getClass(), String.format(
                        "Event-driven backup failed for %s: %s (Event ID: %s)",
                        fileName, errorMessage, event.getEventId()));
            }

        } catch (Exception e) {
            errorMessage = e.getMessage();

            // Record backup failure in monitor
            backupEventMonitor.recordBackupFailure();

            LoggerUtil.error(this.getClass(), String.format(
                    "Exception during event-driven backup creation for %s: %s (Event ID: %s)",
                    event.getFilePath().getPath().getFileName(), e.getMessage(), event.getEventId()), e);
        } finally {
            // Always publish backup operation event for monitoring
            long backupDuration = System.currentTimeMillis() - backupStartTime;
            BackupOperationEvent backupEvent = new BackupOperationEvent(
                    this,
                    event.getFilePath(),
                    event.getUsername(),
                    event.getUserId(),
                    backupSuccess,
                    backupPath,
                    backupSuccess ? determineCriticalityLevel(event.getFilePath()).toString() : "UNKNOWN",
                    backupDuration,
                    errorMessage
            );
            eventPublisher.publishEvent(backupEvent);
        }
    }

    /**
     * Handles file write failures for logging and potential recovery operations.
     */
    @EventListener
    @Async("backupTaskExecutor")
    public void handleFileWriteFailure(FileWriteFailureEvent event) {
        LoggerUtil.warn(this.getClass(), String.format(
                "File write failed for %s by user %s: %s (Event ID: %s)",
                event.getFilePath().getPath().getFileName(),
                event.getUsername(),
                event.getException() != null ? event.getException().getMessage() : "Unknown error",
                event.getEventId()));

        // Could implement recovery logic here if needed
        // For example, attempt to restore from backup if a critical file write fails
    }

    /**
     * Handles backup operation events for monitoring and statistics.
     */
    @EventListener
    @Async("backupTaskExecutor")
    public void handleBackupOperation(BackupOperationEvent event) {
        if (event.isBackupSuccess()) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Backup operation completed: %s (Event ID: %s)",
                    event.getDescription(), event.getEventId()));
        } else {
            LoggerUtil.error(this.getClass(), String.format(
                    "Backup operation failed: %s (Event ID: %s)",
                    event.getDescription(), event.getEventId()));
        }

        // Here you could implement backup statistics collection, alerting, etc.
        // For example, track backup success rates, identify problematic files, etc.
    }

    /**
     * Handles file sync events for monitoring.
     */
    @EventListener
    @Async("backupTaskExecutor")
    public void handleFileSync(FileSyncEvent event) {
        if (event.isSyncSuccess()) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "File sync completed: %s (Event ID: %s)",
                    event.getDescription(), event.getEventId()));
        } else {
            LoggerUtil.warn(this.getClass(), String.format(
                    "File sync failed: %s (Event ID: %s)",
                    event.getDescription(), event.getEventId()));
        }
    }

    /**
     * REFACTORED: Determines the criticality level using FileTypeConstants.
     * Replaces complex hardcoded string matching with centralized logic.
     *
     * @param filePath The file path to analyze
     * @return The criticality level from FileTypeConstants
     */
    private CriticalityLevel determineCriticalityLevel(FilePath filePath) {
        String fileName = filePath.getPath().getFileName().toString();

        // Use FileTypeConstants for centralized criticality determination
        CriticalityLevel level = FileTypeConstants.getCriticalityLevelForFilename(fileName);

        // Enhanced debugging with file type information
        String fileType = FileTypeConstants.extractFileTypeFromFilename(fileName);
        String description = FileTypeConstants.getCriticalityDescription(level);

        LoggerUtil.debug(this.getClass(), String.format(
                "Criticality determination for %s: detected type=%s, level=%s (%s)",
                fileName,
                fileType != null ? fileType : "unknown",
                level,
                description));

        return level;
    }

    /**
     * NEW: Diagnostic method for troubleshooting backup issues.
     * Uses FileTypeConstants comprehensive diagnostics.
     *
     * @param filePath The file path to diagnose
     * @return Diagnostic information string
     */
    public String getBackupDiagnostics(FilePath filePath) {
        String fileName = filePath.getPath().getFileName().toString();

        StringBuilder diag = new StringBuilder();
        diag.append("=== BACKUP EVENT LISTENER DIAGNOSTICS ===\n");

        // Use FileTypeConstants comprehensive diagnostics
        diag.append(FileTypeConstants.getFileTypeDiagnostics(fileName));

        // Add event-specific information
        CriticalityLevel level = determineCriticalityLevel(filePath);
        diag.append("\nEvent Processing:\n");
        diag.append("Will sync to network: ").append(level == CriticalityLevel.LEVEL3_HIGH ? "Yes" : "No").append("\n");
        diag.append("Async executor: backupTaskExecutor\n");

        return diag.toString();
    }

    // Add this method to BackupEventListener class to clear the session cache on logout
    public void clearUserBackupSyncCache(String username) {
        userBackupSyncedThisSession.remove(username);
        LoggerUtil.debug(this.getClass(), String.format("Cleared backup sync cache for user: %s", username));
    }

    // Add this method to clear all session caches (for app restart/midnight reset)
    public void clearAllBackupSyncCaches() {
        int count = userBackupSyncedThisSession.size();
        userBackupSyncedThisSession.clear();
        LoggerUtil.info(this.getClass(), String.format("Cleared backup sync cache for %d users", count));
    }
}