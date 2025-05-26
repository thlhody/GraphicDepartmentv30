package com.ctgraphdep.fileOperations.events;

import com.ctgraphdep.fileOperations.core.FilePath;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


/**
 * Base class for all file operation events.
 * Contains common information about file operations that can trigger backups.
 */
@Getter
public abstract class FileOperationEvent extends ApplicationEvent {
    private final FilePath filePath;
    private final String username;
    private final Integer userId;
    private final String operationType;
    private final boolean shouldCreateBackup;
    private final String eventId;

    protected FileOperationEvent(Object source, FilePath filePath, String username,
                                 Integer userId, String operationType, boolean shouldCreateBackup) {
        super(source);
        this.filePath = filePath;
        this.username = username;
        this.userId = userId;
        this.operationType = operationType;
        this.shouldCreateBackup = shouldCreateBackup;
        this.eventId = generateEventId();
    }

    /**
     * Generates a unique event ID for tracking
     */
    private String generateEventId() {
        return String.format("%s_%s_%d",
                operationType,
                username != null ? username : "system",
                System.currentTimeMillis());
    }

    /**
     * Gets a human-readable description of the event
     */
    public String getDescription() {
        return String.format("%s operation on %s by user %s (backup: %s)",
                operationType,
                filePath.getPath().getFileName(),
                username != null ? username : "system",
                shouldCreateBackup ? "enabled" : "disabled");
    }

    @Override
    public String toString() {
        return String.format("FileOperationEvent{eventId='%s', operationType='%s', filePath='%s', username='%s', shouldCreateBackup=%s}",
                eventId, operationType, filePath.getPath(), username, shouldCreateBackup);
    }
}