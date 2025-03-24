package com.ctgraphdep.model;

import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class SyncStatus {
    private AtomicInteger retryCount;
    private LocalDateTime lastAttempt;
    private LocalDateTime lastSuccessfulSync;
    private ScheduledFuture<?> scheduledRetry;
    private boolean syncInProgress;
    private boolean syncPending;
    private String errorMessage;
    private Path localPath;
    private Path networkPath;

    // Constructor for when you know the paths immediately
    public SyncStatus(GetStandardTimeValuesCommand.StandardTimeValues timeValues, Path localPath, Path networkPath) {
        this.retryCount = new AtomicInteger(0);
        this.lastAttempt = timeValues.getCurrentTime();
        this.syncInProgress = false;
        this.syncPending = false;
        this.localPath = localPath;
        this.networkPath = networkPath;
    }

    public void resetRetryCount() {
        this.retryCount.set(0);
    }

    public int incrementRetryCount() {
        return this.retryCount.incrementAndGet();
    }

    public int getRetryCount() {
        return this.retryCount.get();
    }
}