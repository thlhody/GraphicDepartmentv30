package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SyncStatusManager {

    @Value("${app.sync.retry.max:3}")
    private int maxRetries;

    @Value("${app.sync.retry.delay:3600000}")
    private long retryDelay;

    private final PathConfig pathConfig;
    private final FileSyncService fileSyncService;
    private final Map<String, SyncStatus> syncStatusMap = new ConcurrentHashMap<>();

    private static class SyncStatus {
        LocalDateTime lastSyncAttempt;
        LocalDateTime lastSuccessfulSync;
        int retryCount;
        boolean syncPending;
        String errorMessage;
        Path localPath;
        Path networkPath;

        SyncStatus(Path localPath, Path networkPath) {
            this.localPath = localPath;
            this.networkPath = networkPath;
            this.lastSyncAttempt = null;
            this.lastSuccessfulSync = null;
            this.retryCount = 0;
            this.syncPending = false;
            this.errorMessage = null;
        }
    }

    public SyncStatusManager(PathConfig pathConfig, FileSyncService fileSyncService) {
        this.pathConfig = pathConfig;
        this.fileSyncService = fileSyncService;
        LoggerUtil.initialize(this.getClass(), "Initializing Sync Status Manager");
    }

    public void trackSync(String filename, Path localPath, Path networkPath) {
        SyncStatus status = syncStatusMap.computeIfAbsent(filename,
                k -> new SyncStatus(localPath, networkPath));

        status.lastSyncAttempt = LocalDateTime.now();
        status.syncPending = true;

        LoggerUtil.info(this.getClass(),
                String.format("Started tracking sync for file: %s", filename));
    }

    public void markSyncSuccess(String filename) {
        SyncStatus status = syncStatusMap.get(filename);
        if (status != null) {
            status.lastSuccessfulSync = LocalDateTime.now();
            status.syncPending = false;
            status.retryCount = 0;
            status.errorMessage = null;

            LoggerUtil.info(this.getClass(),
                    String.format("Sync successful for file: %s", filename));
        }
    }

    public void markSyncFailure(String filename, String errorMessage) {
        SyncStatus status = syncStatusMap.get(filename);
        if (status != null) {
            status.retryCount++;
            status.errorMessage = errorMessage;

            LoggerUtil.error(this.getClass(),
                    String.format("Sync failed for file %s: %s (Attempt %d/%d)",
                            filename, errorMessage, status.retryCount, maxRetries));

            if (status.retryCount >= maxRetries) {
                scheduleRetryAfterDelay(filename);
            }
        }
    }

    private void scheduleRetryAfterDelay(String filename) {
        SyncStatus status = syncStatusMap.get(filename);
        if (status != null) {
            status.retryCount = 0;  // Reset retry count

            LoggerUtil.info(this.getClass(),
                    String.format("Scheduling retry after delay for file: %s", filename));
        }
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void processFailedSyncs() {
        Set<String> failedFiles = getFailedSyncs();

        for (String filename : failedFiles) {
            SyncStatus status = syncStatusMap.get(filename);
            if (status != null && shouldRetry(status)) {
                retrySync(filename, status);
            }
        }
    }

    private boolean shouldRetry(SyncStatus status) {
        if (!status.syncPending) {
            return false;
        }

        if (status.lastSyncAttempt == null) {
            return true;
        }

        LocalDateTime nextRetryTime = status.lastSyncAttempt
                .plusNanos(retryDelay * 1_000_000);
        return LocalDateTime.now().isAfter(nextRetryTime);
    }

    private void retrySync(String filename, SyncStatus status) {
        if (pathConfig.isNetworkAvailable()) {
            LoggerUtil.info(this.getClass(),
                    String.format("Retrying sync for file: %s", filename));

            fileSyncService.syncToNetwork(status.localPath, status.networkPath);
        }
    }

    public Set<String> getFailedSyncs() {
        return syncStatusMap.entrySet().stream()
                .filter(e -> e.getValue().syncPending && e.getValue().errorMessage != null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public boolean isSyncPending(String filename) {
        SyncStatus status = syncStatusMap.get(filename);
        return status != null && status.syncPending;
    }

    public String getSyncError(String filename) {
        SyncStatus status = syncStatusMap.get(filename);
        return status != null ? status.errorMessage : null;
    }

    public LocalDateTime getLastSuccessfulSync(String filename) {
        SyncStatus status = syncStatusMap.get(filename);
        return status != null ? status.lastSuccessfulSync : null;
    }

    public void clearSyncStatus(String filename) {
        syncStatusMap.remove(filename);
        LoggerUtil.info(this.getClass(),
                String.format("Cleared sync status for file: %s", filename));
    }

    public Map<String, SyncStatus> getSyncStatusSnapshot() {
        return new ConcurrentHashMap<>(syncStatusMap);
    }

    public void clearAll() {
        syncStatusMap.clear();
        LoggerUtil.info(this.getClass(), "Cleared all sync statuses");
    }
}