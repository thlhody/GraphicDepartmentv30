package com.ctgraphdep.fileOperations.events;

import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.core.FilePath;
import lombok.Getter;

/**
 * Event fired after a successful file write operation.
 * This is the primary trigger for backup creation.
 */
@Getter
public class FileWriteSuccessEvent extends FileOperationEvent {
    private final FileOperationResult operationResult;
    private final long bytesWritten;
    private final long operationDurationMs;

    public FileWriteSuccessEvent(Object source, FilePath filePath, String username,
                                 Integer userId, boolean shouldCreateBackup,
                                 FileOperationResult operationResult, long operationDurationMs) {
        super(source, filePath, username, userId, "WRITE_SUCCESS", shouldCreateBackup);
        this.operationResult = operationResult;
        this.operationDurationMs = operationDurationMs;
        this.bytesWritten = calculateBytesWritten();
    }

    private long calculateBytesWritten() {
        try {
            if (operationResult.isSuccess() && operationResult.getFilePath() != null) {
                return java.nio.file.Files.size(operationResult.getFilePath());
            }
        } catch (Exception e) {
            // Ignore errors getting file size
        }
        return 0;
    }

    @Override
    public String getDescription() {
        return String.format("Completed write operation on %s by user %s (size: %d bytes, duration: %dms, backup: %s)",
                getFilePath().getPath().getFileName(),
                getUsername() != null ? getUsername() : "system",
                bytesWritten,
                operationDurationMs,
                isShouldCreateBackup() ? "enabled" : "disabled");
    }
}
