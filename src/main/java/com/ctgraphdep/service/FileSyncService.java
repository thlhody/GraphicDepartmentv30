package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
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
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FileSyncService {

    @Value("${app.sync.retry.max:3}")
    private int maxRetries;

    @Value("${app.sync.retry.delay:3600000}")
    private long retryDelay; // Default 1 hour

    private final Map<Path, SyncStatus> syncStatusMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private PathConfig pathConfig;

    private static class SyncStatus {
        AtomicInteger retryCount = new AtomicInteger(0);
        LocalDateTime lastAttempt = LocalDateTime.now();
        ScheduledFuture<?> scheduledRetry;
        boolean syncInProgress = false;
    }

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

        SyncStatus status = syncStatusMap.computeIfAbsent(localPath, k -> new SyncStatus());
        if (status.syncInProgress) {
            LoggerUtil.info(this.getClass(),
                    "Sync already in progress for: " + localPath);
            return;
        }

        status.syncInProgress = true;
        try {
            // Use PathConfig to get the relative path from the local base path
            Path relativePath = pathConfig.getLocalPath().relativize(localPath);

            // Construct the full network destination path
            Path fullNetworkPath = pathConfig.getNetworkPath().resolve(relativePath);

            // Call the performSync method with the local and full network paths
            performSync(localPath, fullNetworkPath, status);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Unexpected error during sync process: %s", e.getMessage()));
            handleSyncFailure(localPath, networkPath, status, e);
        } finally {
            status.syncInProgress = false;
        }
    }

    private void performSync(Path localPath, Path networkPath, SyncStatus status) {
        try {
            // Create parent directories if they don't exist
            Files.createDirectories(networkPath.getParent());

            // Copy file with replace existing option
            Files.copy(localPath, networkPath, StandardCopyOption.REPLACE_EXISTING);

            // Reset sync status on success
            syncStatusMap.remove(localPath);
            cancelScheduledRetry(status);

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
                status.retryCount.get() >= maxRetries &&
                !hasDelayPassed(status.lastAttempt)) {
            LoggerUtil.info(this.getClass(),
                    "Max retries reached and delay not passed for: " + localPath);
            return false;
        }

        return true;
    }


    private void handleSyncFailure(Path localPath, Path networkPath,
                                   SyncStatus status, Exception e) {
        int currentRetries = status.retryCount.incrementAndGet();
        status.lastAttempt = LocalDateTime.now();

        LoggerUtil.error(this.getClass(),
                String.format("Failed to sync file: %s, attempt %d/%d: %s",
                        localPath, currentRetries, maxRetries, e.getMessage()));

        if (currentRetries < maxRetries) {
            scheduleQuickRetry(localPath, networkPath, status);
        } else {
            scheduleLongRetry(localPath, networkPath, status);
        }
    }

    private void scheduleQuickRetry(Path localPath, Path networkPath,
                                    SyncStatus status) {
        cancelScheduledRetry(status);
        status.scheduledRetry = scheduler.schedule(
                () -> syncToNetwork(localPath, networkPath),
                10, // 10 minutes
                TimeUnit.MINUTES
        );
    }

    private void scheduleLongRetry(Path localPath, Path networkPath,
                                   SyncStatus status) {
        cancelScheduledRetry(status);
        status.scheduledRetry = scheduler.schedule(
                () -> {
                    status.retryCount.set(0);
                    syncToNetwork(localPath, networkPath);
                },
                retryDelay,
                TimeUnit.MILLISECONDS
        );
    }

    private void cancelScheduledRetry(SyncStatus status) {
        if (status.scheduledRetry != null && !status.scheduledRetry.isDone()) {
            status.scheduledRetry.cancel(false);
        }
    }

    private boolean hasDelayPassed(LocalDateTime lastAttempt) {
        return LocalDateTime.now()
                .isAfter(lastAttempt.plusNanos(retryDelay * 1_000_000));
    }

    public boolean isSyncPending(Path localPath) {
        return syncStatusMap.containsKey(localPath);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LoggerUtil.info(this.getClass(), "File sync service scheduler shutdown completed");
    }
}