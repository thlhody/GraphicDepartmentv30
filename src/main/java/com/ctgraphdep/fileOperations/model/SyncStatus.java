package com.ctgraphdep.fileOperations.model;

import lombok.Data;
import java.time.LocalDateTime;


/**
 * Status of a file synchronization operation.
 * Tracks metadata about sync operations including success/failure status and retry information.
 */
@Data
public class SyncStatus {
    private String sourcePath;
    private String targetPath;
    private boolean syncInProgress = false;
    private boolean syncPending = false;
    private int retryCount = 0;
    private LocalDateTime createdTime;
    private LocalDateTime lastAttempt;
    private LocalDateTime lastSuccessfulSync;
    private String errorMessage;

    /**
     * Reset the retry count to zero
     */
    public void resetRetryCount() {
        this.retryCount = 0;
    }

    /**
     * Increment the retry count
     * @return The new retry count
     */
    public int incrementRetryCount() {
        return ++retryCount;
    }
}
