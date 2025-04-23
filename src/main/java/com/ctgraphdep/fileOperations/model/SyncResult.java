package com.ctgraphdep.fileOperations.model;

import com.ctgraphdep.fileOperations.core.FileOperationResult;
import lombok.Data;

/**
 * Result of a synchronization operation.
 * Contains information about the success/failure status and the direction of the sync.
 */
@Data
public class SyncResult {
    private final FileOperationResult result;
    private final Direction direction;

    /**
     * Direction of the synchronization
     */
    public enum Direction {
        LOCAL_TO_NETWORK,
        NETWORK_TO_LOCAL,
        NONE,
        ERROR
    }

    /**
     * Check if the sync was successful
     * @return True if the sync was successful
     */
    public boolean isSuccess() {
        return result.isSuccess();
    }

    /**
     * Get the error message if the sync failed
     * @return The error message, or "No error" if the sync succeeded
     */
    public String getErrorMessage() {
        return result.getErrorMessage().orElse("No error");
    }
}
