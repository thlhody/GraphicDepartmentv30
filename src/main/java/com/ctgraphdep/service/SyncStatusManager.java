package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.SyncStatus;
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

    public SyncStatusManager(PathConfig pathConfig, FileSyncService fileSyncService) {
        this.pathConfig = pathConfig;
        this.fileSyncService = fileSyncService;
        LoggerUtil.initialize(this.getClass(), "Initializing Sync Status Manager");
    }

    public void trackSync(String filename, Path localPath, Path networkPath) {
        SyncStatus status = new SyncStatus(localPath, networkPath);
        status.setLastAttempt(LocalDateTime.now());
        status.setSyncPending(true);
        syncStatusMap.put(filename, status);

        LoggerUtil.info(this.getClass(),
                String.format("Started tracking sync for file: %s", filename));
    }

    public void markSyncSuccess(String filename) {
        SyncStatus status = syncStatusMap.get(filename);
        if (status != null) {
            status.setLastSuccessfulSync(LocalDateTime.now());
            status.setSyncPending(false);
            status.resetRetryCount();
            status.setErrorMessage(null);

            LoggerUtil.info(this.getClass(),
                    String.format("Sync successful for file: %s", filename));
        }
    }

    public void markSyncFailure(String filename, String errorMessage) {
        SyncStatus status = syncStatusMap.get(filename);
        if (status != null) {
            status.incrementRetryCount();
            status.setErrorMessage(errorMessage);

            LoggerUtil.error(this.getClass(),
                    String.format("Sync failed for file %s: %s (Attempt %d/%d)",
                            filename, errorMessage, status.getRetryCount(), maxRetries));

            if (status.getRetryCount() >= maxRetries) {
                scheduleRetryAfterDelay(filename);
            }
        }
    }

    private void scheduleRetryAfterDelay(String filename) {
        SyncStatus status = syncStatusMap.get(filename);
        if (status != null) {
            status.resetRetryCount();

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
        if (!status.isSyncPending()) {
            return false;
        }

        if (status.getLastAttempt() == null) {
            return true;
        }

        LocalDateTime nextRetryTime = status.getLastAttempt()
                .plusNanos(retryDelay * 1_000_000);
        return LocalDateTime.now().isAfter(nextRetryTime);
    }

    private void retrySync(String filename, SyncStatus status) {
        if (pathConfig.isNetworkAvailable()) {
            LoggerUtil.info(this.getClass(),
                    String.format("Retrying sync for file: %s", filename));

            fileSyncService.syncToNetwork(status.getLocalPath(), status.getNetworkPath());
        }
    }

    public Set<String> getFailedSyncs() {
        return syncStatusMap.entrySet().stream()
                .filter(e -> e.getValue().isSyncPending() && e.getValue().getErrorMessage() != null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public boolean isSyncPending(String filename) {
        SyncStatus status = syncStatusMap.get(filename);
        return status != null && status.isSyncPending();
    }

    public String getSyncError(String filename) {
        SyncStatus status = syncStatusMap.get(filename);
        return status != null ? status.getErrorMessage() : null;
    }

    public LocalDateTime getLastSuccessfulSync(String filename) {
        SyncStatus status = syncStatusMap.get(filename);
        return status != null ? status.getLastSuccessfulSync() : null;
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