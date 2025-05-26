package com.ctgraphdep.fileOperations.events;

import com.ctgraphdep.fileOperations.core.FilePath;
import lombok.Getter;

/**
 * Event fired when files are synchronized between local and network storage.
 * Can be used for sync monitoring and conflict resolution.
 */
@Getter
public class FileSyncEvent extends FileOperationEvent {
    private final FilePath sourcePath;
    private final FilePath targetPath;
    private final boolean syncSuccess;
    private final String syncDirection;
    private final long syncDurationMs;

    public FileSyncEvent(Object source, FilePath sourcePath, FilePath targetPath, String username,
                         Integer userId, boolean syncSuccess, String syncDirection, long syncDurationMs) {
        super(source, sourcePath, username, userId, "SYNC", false); // Sync operations don't trigger backups
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.syncSuccess = syncSuccess;
        this.syncDirection = syncDirection;
        this.syncDurationMs = syncDurationMs;
    }

    @Override
    public String getDescription() {
        return String.format("File sync %s: %s -> %s (duration: %dms)",
                syncSuccess ? "completed" : "failed",
                sourcePath.getPath().getFileName(),
                targetPath.getPath().getFileName(),
                syncDurationMs);
    }
}
