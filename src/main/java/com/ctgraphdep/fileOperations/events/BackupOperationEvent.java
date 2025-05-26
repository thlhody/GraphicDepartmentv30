package com.ctgraphdep.fileOperations.events;

import com.ctgraphdep.fileOperations.core.FilePath;
import lombok.Getter;

/**
 * Event fired when a backup operation completes (success or failure).
 * Can be used for backup monitoring and statistics.
 */
@Getter
public class BackupOperationEvent extends FileOperationEvent {
    private final boolean backupSuccess;
    private final String backupPath;
    private final String criticalityLevel;
    private final long backupDurationMs;
    private final String errorMessage;

    public BackupOperationEvent(Object source, FilePath originalFilePath, String username,
                                Integer userId, boolean backupSuccess, String backupPath,
                                String criticalityLevel, long backupDurationMs, String errorMessage) {
        super(source, originalFilePath, username, userId, "BACKUP_OPERATION", true);
        this.backupSuccess = backupSuccess;
        this.backupPath = backupPath;
        this.criticalityLevel = criticalityLevel;
        this.backupDurationMs = backupDurationMs;
        this.errorMessage = errorMessage;
    }

    @Override
    public String getDescription() {
        if (backupSuccess) {
            return String.format("Backup created for %s (level: %s, path: %s, duration: %dms)",
                    getFilePath().getPath().getFileName(),
                    criticalityLevel,
                    backupPath,
                    backupDurationMs);
        } else {
            return String.format("Backup failed for %s (level: %s, duration: %dms, error: %s)",
                    getFilePath().getPath().getFileName(),
                    criticalityLevel,
                    backupDurationMs,
                    errorMessage);
        }
    }
}
