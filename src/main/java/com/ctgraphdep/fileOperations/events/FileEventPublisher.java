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
    public void publishFileWriteStart(FilePath filePath, String username, Integer userId, boolean shouldCreateBackup, Object dataToWrite) {
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
}