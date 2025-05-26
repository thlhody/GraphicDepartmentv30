package com.ctgraphdep.fileOperations.events;

import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.core.FilePath;
import lombok.Getter;

/**
 * Event fired when a file write operation fails.
 * Can be used for error handling and recovery operations.
 */
@Getter
public class FileWriteFailureEvent extends FileOperationEvent {
    private final FileOperationResult operationResult;
    private final Exception exception;
    private final long operationDurationMs;

    public FileWriteFailureEvent(Object source, FilePath filePath, String username,
                                 Integer userId, boolean shouldCreateBackup,
                                 FileOperationResult operationResult, Exception exception,
                                 long operationDurationMs) {
        super(source, filePath, username, userId, "WRITE_FAILURE", shouldCreateBackup);
        this.operationResult = operationResult;
        this.exception = exception;
        this.operationDurationMs = operationDurationMs;
    }

    @Override
    public String getDescription() {
        return String.format("Failed write operation on %s by user %s (duration: %dms, error: %s)",
                getFilePath().getPath().getFileName(),
                getUsername() != null ? getUsername() : "system",
                operationDurationMs,
                exception != null ? exception.getMessage() : "Unknown error");
    }
}
