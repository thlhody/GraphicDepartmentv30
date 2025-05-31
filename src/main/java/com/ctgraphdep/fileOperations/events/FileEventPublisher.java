package com.ctgraphdep.fileOperations.events;

import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.monitoring.BackupEventMonitor;  // CHANGED: Import from monitoring package
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Service for publishing file operation events.
 * Provides a clean interface for other services to trigger backup and monitoring events.
 * UPDATED: Now uses the dedicated BackupEventMonitor service from monitoring package.
 */
@Service
public class FileEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Getter
    private final BackupEventMonitor eventMonitor;  // CHANGED: Use the service instead of inner class

    @Autowired
    public FileEventPublisher(ApplicationEventPublisher eventPublisher,
                              BackupEventMonitor eventMonitor) {  // CHANGED: Parameter type
        this.eventPublisher = eventPublisher;
        this.eventMonitor = eventMonitor;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Publishes a file write start event.
     * Call this before beginning a file write operation.
     */
    public void publishFileWriteStart(FilePath filePath, String username, Integer userId,
                                      boolean shouldCreateBackup, Object dataToWrite) {
        try {
            FileWriteEvent event = new FileWriteEvent(this, filePath, username, userId, shouldCreateBackup, dataToWrite);
            eventPublisher.publishEvent(event);
            eventMonitor.recordEventProcessed();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Published FileWriteEvent: %s (Event ID: %s)",
                    event.getDescription(), event.getEventId()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Failed to publish FileWriteEvent for %s: %s",
                    filePath.getPath().getFileName(), e.getMessage()), e);
        }
    }

    /**
     * Publishes a file write success event.
     * Call this after a successful file write operation.
     * This is the primary trigger for backup creation.
     */
    public void publishFileWriteSuccess(FilePath filePath, String username, Integer userId,
                                        boolean shouldCreateBackup, FileOperationResult result,
                                        long operationDurationMs) {
        try {
            FileWriteSuccessEvent event = new FileWriteSuccessEvent(
                    this, filePath, username, userId, shouldCreateBackup, result, operationDurationMs);
            eventPublisher.publishEvent(event);
            eventMonitor.recordEventProcessed();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Published FileWriteSuccessEvent: %s (Event ID: %s)",
                    event.getDescription(), event.getEventId()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Failed to publish FileWriteSuccessEvent for %s: %s",
                    filePath.getPath().getFileName(), e.getMessage()), e);
        }
    }

    /**
     * Publishes a file write failure event.
     * Call this when a file write operation fails.
     */
    public void publishFileWriteFailure(FilePath filePath, String username, Integer userId,
                                        boolean shouldCreateBackup, FileOperationResult result,
                                        Exception exception, long operationDurationMs) {
        try {
            FileWriteFailureEvent event = new FileWriteFailureEvent(
                    this, filePath, username, userId, shouldCreateBackup, result, exception, operationDurationMs);
            eventPublisher.publishEvent(event);
            eventMonitor.recordEventProcessed();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Published FileWriteFailureEvent: %s (Event ID: %s)",
                    event.getDescription(), event.getEventId()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Failed to publish FileWriteFailureEvent for %s: %s",
                    filePath.getPath().getFileName(), e.getMessage()), e);
        }
    }

    /**
     * Publishes a file sync event.
     * Call this when files are synchronized between local and network storage.
     */
    public void publishFileSync(FilePath sourcePath, FilePath targetPath, String username, Integer userId,
                                boolean syncSuccess, String syncDirection, long syncDurationMs) {
        try {
            FileSyncEvent event = new FileSyncEvent(
                    this, sourcePath, targetPath, username, userId, syncSuccess, syncDirection, syncDurationMs);
            eventPublisher.publishEvent(event);
            eventMonitor.recordEventProcessed();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Published FileSyncEvent: %s (Event ID: %s)",
                    event.getDescription(), event.getEventId()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Failed to publish FileSyncEvent for %s -> %s: %s",
                    sourcePath.getPath().getFileName(), targetPath.getPath().getFileName(), e.getMessage()), e);
        }
    }

    /**
     * Convenience method for simple file operations where backup should be created.
     * Publishes both start and success events with timing.
     */
    public void publishSuccessfulFileWrite(FilePath filePath, String username, Integer userId, Object dataToWrite) {
        publishSuccessfulFileWrite(filePath, username, userId, true, dataToWrite);
    }

    /**
     * Convenience method for file operations with backup control.
     * Publishes both start and success events with timing.
     */
    public void publishSuccessfulFileWrite(FilePath filePath, String username, Integer userId,
                                           boolean shouldCreateBackup, Object dataToWrite) {
        long startTime = System.currentTimeMillis();

        // Publish start event
        publishFileWriteStart(filePath, username, userId, shouldCreateBackup, dataToWrite);

        // Create a successful result for the success event
        FileOperationResult result = FileOperationResult.success(filePath.getPath());
        long duration = System.currentTimeMillis() - startTime;

        // Publish success event
        publishFileWriteSuccess(filePath, username, userId, shouldCreateBackup, result, duration);
    }

    /**
     * Convenience method for failed file operations.
     * Publishes both start and failure events with timing.
     */
    public void publishFailedFileWrite(FilePath filePath, String username, Integer userId,
                                       boolean shouldCreateBackup, Object dataToWrite, Exception exception) {
        long startTime = System.currentTimeMillis();

        // Publish start event
        publishFileWriteStart(filePath, username, userId, shouldCreateBackup, dataToWrite);

        // Create a failure result for the failure event
        FileOperationResult result = FileOperationResult.failure(filePath.getPath(),
                "Write operation failed: " + exception.getMessage(), exception);
        long duration = System.currentTimeMillis() - startTime;

        // Publish failure event
        publishFileWriteFailure(filePath, username, userId, shouldCreateBackup, result, exception, duration);
    }

    /**
     * Gets a summary of event processing statistics.
     */
    public String getEventStatistics() {
        return eventMonitor.toString();
    }

    /**
     * Checks if the event system is healthy and processing events.
     */
    public boolean isEventSystemHealthy() {
        // Consider the system healthy if we've processed events recently or haven't had any failures
        long timeSinceLastEvent = System.currentTimeMillis() - eventMonitor.getLastEventTimestamp();
        return eventMonitor.getLastEventTimestamp() == 0 || // No events yet (startup)
                timeSinceLastEvent < 300000 || // Activity within 5 minutes
                eventMonitor.getBackupSuccessRate() > 90.0; // High success rate
    }

    /**
     * Gets detailed health status from the monitoring service.
     */
    public String getDetailedHealthStatus() {
        return eventMonitor.getDetailedHealthStatus();
    }
}