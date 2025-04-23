package com.ctgraphdep.fileOperations.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Metadata about a synchronization operation.
 * Used for reporting and analytics.
 */
@Data
public class SyncMetadata {
    private String sourcePath;
    private String targetPath;
    private LocalDateTime timestamp;
    private boolean success;
    private String errorMessage;
    private String username;
    private Integer userId;

    /**
     * Convert this metadata to a string representation
     */
    @Override
    public String toString() {
        return String.format("[%s] %s -> %s: %s",
                timestamp,
                sourcePath,
                targetPath,
                success ? "SUCCESS" : "FAILURE: " + errorMessage);
    }
}
