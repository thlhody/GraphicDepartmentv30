package com.ctgraphdep.fileOperations.model.dto;

import lombok.Getter;

/**
 * Result tracking for backup sync operations.
 */
@Getter
public class BackupSyncResult {
    private int syncedCount = 0;
    private int skippedCount = 0;
    private int failedCount = 0;

    public void incrementSynced() {
        syncedCount++;
    }

    public void incrementSkipped() {
        skippedCount++;
    }

    public void incrementFailed() {
        failedCount++;
    }

    public boolean hasFiles() {
        return (syncedCount + skippedCount + failedCount) > 0;
    }

    public int getTotalFiles() {
        return syncedCount + skippedCount + failedCount;
    }
}