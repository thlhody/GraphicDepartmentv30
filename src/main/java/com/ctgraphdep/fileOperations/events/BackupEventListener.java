package com.ctgraphdep.fileOperations.events;

import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.BackupService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Event listener that handles file operation events and creates backups accordingly.
 * This component decouples backup creation from file writing operations.
 */
@Component
public class BackupEventListener {

    private final BackupService backupService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public BackupEventListener(BackupService backupService, ApplicationEventPublisher eventPublisher) {
        this.backupService = backupService;
        this.eventPublisher = eventPublisher;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Handles successful file write events and creates backups if requested.
     * This method runs asynchronously to avoid blocking the main file write operation.
     */
    @EventListener
    @Async
    public void handleFileWriteSuccess(FileWriteSuccessEvent event) {
        // Check if backup should be created
        if (!event.isShouldCreateBackup()) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Skipping backup for %s - backup disabled for this operation (Event ID: %s)",
                    event.getFilePath().getPath().getFileName(), event.getEventId()));
            return;
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Processing backup request for %s (Event ID: %s)",
                event.getFilePath().getPath().getFileName(), event.getEventId()));

        long backupStartTime = System.currentTimeMillis();
        String backupPath = null;
        String errorMessage = null;
        boolean backupSuccess = false;

        try {
            // Determine criticality level based on file path and type
            BackupService.CriticalityLevel criticalityLevel = determineCriticalityLevel(event.getFilePath());

            LoggerUtil.info(this.getClass(), String.format(
                    "Creating %s backup for %s (user: %s, Event ID: %s)",
                    criticalityLevel,
                    event.getFilePath().getPath().getFileName(),
                    event.getUsername(),
                    event.getEventId()));

            // Create the backup
            FileOperationResult backupResult = backupService.createBackup(event.getFilePath(), criticalityLevel);

            if (backupResult.isSuccess()) {
                backupSuccess = true;
                backupPath = backupResult.getFilePath().toString();

                LoggerUtil.info(this.getClass(), String.format(
                        "Event-driven backup created successfully: %s (level: %s, user: %s, Event ID: %s)",
                        backupPath, criticalityLevel, event.getUsername(), event.getEventId()));

                // For high criticality files, also sync backups to network
                if (criticalityLevel == BackupService.CriticalityLevel.LEVEL3_HIGH && event.getUsername() != null) {
                    try {
                        backupService.syncBackupsToNetwork(event.getUsername(), criticalityLevel);
                        LoggerUtil.info(this.getClass(), String.format(
                                "Synced high criticality backups to network for user %s (Event ID: %s)",
                                event.getUsername(), event.getEventId()));
                    } catch (Exception syncException) {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Failed to sync high criticality backups to network: %s (Event ID: %s)",
                                syncException.getMessage(), event.getEventId()));
                        // Don't fail the backup operation if sync fails
                    }
                }
            } else {
                errorMessage = backupResult.getErrorMessage().orElse("Unknown backup error");
                LoggerUtil.error(this.getClass(), String.format(
                        "Event-driven backup failed for %s: %s (Event ID: %s)",
                        event.getFilePath().getPath().getFileName(), errorMessage, event.getEventId()));
            }

        } catch (Exception e) {
            errorMessage = e.getMessage();
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
     * Determines the criticality level of a file based on its path and type.
     * This logic mirrors the logic in FileWriterService but is centralized here for the event system.
     */
    private BackupService.CriticalityLevel determineCriticalityLevel(com.ctgraphdep.fileOperations.core.FilePath filePath) {
        Path path = filePath.getPath();
        String pathStr = path.toString().toLowerCase();
        String fileName = path.getFileName().toString().toLowerCase();

        LoggerUtil.debug(this.getClass(), "Determining criticality for: " + pathStr);

        // LEVEL1_LOW - Status files, temporary files
        if (pathStr.contains("status") || fileName.startsWith("status_") ||
                pathStr.contains("temp") || pathStr.contains("cache")) {
            return BackupService.CriticalityLevel.LEVEL1_LOW;
        }

        // LEVEL3_HIGH - Critical user and business data
        if (pathStr.contains("worktime") || pathStr.contains("registru") ||
                pathStr.contains("register") || pathStr.contains("timeoff") ||
                (pathStr.contains("user") && !pathStr.contains("session")) ||
                pathStr.contains("check") || pathStr.contains("bonus") ||
                fileName.contains("worktime") || fileName.contains("registru") ||
                fileName.contains("register") || fileName.contains("timeoff")) {
            LoggerUtil.debug(this.getClass(), "High criticality file detected: " + pathStr);
            return BackupService.CriticalityLevel.LEVEL3_HIGH;
        }

        // LEVEL2_MEDIUM - Session files and everything else
        if (pathStr.contains("session") || fileName.contains("session") ||
                pathStr.contains("team")) {
            return BackupService.CriticalityLevel.LEVEL2_MEDIUM;
        }

        // Default to medium criticality for unknown files
        return BackupService.CriticalityLevel.LEVEL2_MEDIUM;
    }
}