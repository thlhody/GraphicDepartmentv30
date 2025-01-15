package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.SyncStatus;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class FileSyncService {

    @Value("${app.sync.retry.max:3}")
    private int maxRetries;

    @Value("${app.sync.retry.delay:3600000}")
    private long retryDelay; // Default 1 hour

    private final Map<Path, SyncStatus> syncStatusMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private PathConfig pathConfig;

    public FileSyncService() {
        LoggerUtil.initialize(this.getClass(), "Initializing File Sync Service");
    }

    @Autowired
    public void setPathConfig(PathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    @Async
    public void syncToNetwork(Path localPath, Path networkPath) {
        if (!shouldAttemptSync(localPath)) {
            return;
        }

        SyncStatus status = syncStatusMap.computeIfAbsent(localPath,
                k -> new SyncStatus(localPath, networkPath));

        if (status.isSyncInProgress()) {
            LoggerUtil.info(this.getClass(),
                    "Sync already in progress for: " + localPath);
            return;
        }

        status.setSyncInProgress(true);
        try {
            Path relativePath = pathConfig.getLocalPath().relativize(localPath);
            Path fullNetworkPath = pathConfig.getNetworkPath().resolve(relativePath);
            performSync(localPath, fullNetworkPath, status);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Unexpected error during sync process: %s", e.getMessage()));
            handleSyncFailure(localPath, networkPath, status, e);
        } finally {
            status.setSyncInProgress(false);
        }
    }

    private void performSync(Path localPath, Path networkPath, SyncStatus status) {
        try {
            Files.createDirectories(networkPath.getParent());
            Files.copy(localPath, networkPath, StandardCopyOption.REPLACE_EXISTING);

            syncStatusMap.remove(localPath);
            cancelScheduledRetry(status);
            status.setLastSuccessfulSync(LocalDateTime.now());

            LoggerUtil.info(this.getClass(),
                    "Successfully synced file to network: " + networkPath);

        } catch (Exception e) {
            handleSyncFailure(localPath, networkPath, status, e);
        }
    }

    private boolean shouldAttemptSync(Path localPath) {
        if (!pathConfig.isNetworkAvailable()) {
            LoggerUtil.warn(this.getClass(),
                    "Network unavailable, skipping sync for: " + localPath);
            return false;
        }

        SyncStatus status = syncStatusMap.get(localPath);
        if (status != null &&
                status.getRetryCount() >= maxRetries &&
                !hasDelayPassed(status.getLastAttempt())) {
            LoggerUtil.info(this.getClass(),
                    "Max retries reached and delay not passed for: " + localPath);
            return false;
        }

        return true;
    }

    private void handleSyncFailure(Path localPath, Path networkPath,
                                   SyncStatus status, Exception e) {
        int currentRetries = status.incrementRetryCount();
        status.setLastAttempt(LocalDateTime.now());
        status.setErrorMessage(e.getMessage());
        status.setSyncPending(true);

        LoggerUtil.error(this.getClass(),
                String.format("Failed to sync file: %s, attempt %d/%d: %s",
                        localPath, currentRetries, maxRetries, e.getMessage()));

        if (currentRetries < maxRetries) {
            scheduleQuickRetry(localPath, networkPath, status);
        } else {
            scheduleLongRetry(localPath, networkPath, status);
        }
    }

    private void scheduleQuickRetry(Path localPath, Path networkPath, SyncStatus status) {
        cancelScheduledRetry(status);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> syncToNetwork(localPath, networkPath),
                WorkCode.ON_FOR_TEN_MINUTES,
                TimeUnit.MINUTES
        );
        status.setScheduledRetry(future);
    }

    private void scheduleLongRetry(Path localPath, Path networkPath, SyncStatus status) {
        cancelScheduledRetry(status);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> {
                    status.resetRetryCount();
                    syncToNetwork(localPath, networkPath);
                },
                retryDelay,
                TimeUnit.MILLISECONDS
        );
        status.setScheduledRetry(future);
    }

    private void cancelScheduledRetry(SyncStatus status) {
        ScheduledFuture<?> scheduledRetry = status.getScheduledRetry();
        if (scheduledRetry != null && !scheduledRetry.isDone()) {
            scheduledRetry.cancel(false);
        }
    }

    private boolean hasDelayPassed(LocalDateTime lastAttempt) {
        return LocalDateTime.now()
                .isAfter(lastAttempt.plusNanos(retryDelay * 1_000_000));
    }

    public boolean isSyncPending(Path localPath) {
        SyncStatus status = syncStatusMap.get(localPath);
        return status != null && status.isSyncPending();
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(WorkCode.ONE_MINUTE_DELAY, TimeUnit.MINUTES)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LoggerUtil.info(this.getClass(), "File sync service scheduler shutdown completed");
    }
}