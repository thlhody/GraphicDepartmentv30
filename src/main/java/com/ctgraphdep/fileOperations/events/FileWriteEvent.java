package com.ctgraphdep.fileOperations.events;

import com.ctgraphdep.fileOperations.core.FilePath;
import lombok.Getter;

/**
 * Event fired before a file write operation begins.
 * Can be used for validation, logging, or preparation tasks.
 */
@Getter
public class FileWriteEvent extends FileOperationEvent {
    private final Object dataToWrite;
    private final long dataSize;

    public FileWriteEvent(Object source, FilePath filePath, String username,
                          Integer userId, boolean shouldCreateBackup, Object dataToWrite) {
        super(source, filePath, username, userId, "WRITE", shouldCreateBackup);
        this.dataToWrite = dataToWrite;
        this.dataSize = calculateDataSize(dataToWrite);
    }

    private long calculateDataSize(Object data) {
        if (data == null) return 0;
        if (data instanceof String) return ((String) data).length();
        if (data instanceof byte[]) return ((byte[]) data).length;
        // For other objects, return a default estimate
        return data.toString().length();
    }

    @Override
    public String getDescription() {
        return String.format("Starting write operation on %s by user %s (size: %d bytes, backup: %s)",
                getFilePath().getPath().getFileName(),
                getUsername() != null ? getUsername() : "system",
                dataSize,
                isShouldCreateBackup() ? "enabled" : "disabled");
    }
}