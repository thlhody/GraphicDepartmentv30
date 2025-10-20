package com.ctgraphdep.fileOperations.model;

import com.ctgraphdep.fileOperations.core.FileOperationResult;

/**
 * Result of a file synchronization operation.
 * Immutable record containing information about the success/failure status
 * and the direction of the sync operation.
 *
 * @param result The file operation result containing success status and error details
 * @param direction The direction of the synchronization (LOCAL_TO_NETWORK, NETWORK_TO_LOCAL, etc.)
 */
public record SyncResult(
        FileOperationResult result,
        SyncDirection direction
) {

    /**
     * Check if the sync was successful.
     *
     * @return true if the sync operation succeeded
     */
    public boolean isSuccess() {
        return result.isSuccess();
    }

    /**
     * Get the error message if the sync failed.
     *
     * @return The error message, or "No error" if the sync succeeded
     */
    public String getErrorMessage() {
        return result.getErrorMessage().orElse("No error");
    }
}