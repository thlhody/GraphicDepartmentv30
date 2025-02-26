package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.SyncStatus;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PreDestroy;
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

    @Value("${app.sync.retry.delay:3600000}")
    private long retryDelay;

    private final PathConfig pathConfig;
    private final FileSyncService fileSyncService;
    private final Map<String, SyncStatus> syncStatusMap = new ConcurrentHashMap<>();

    public SyncStatusManager(PathConfig pathConfig, FileSyncService fileSyncService) {
        this.pathConfig = pathConfig;
        this.fileSyncService = fileSyncService;
        LoggerUtil.initialize(this.getClass(), null);
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

    @PreDestroy
    public void clearAll() {
        syncStatusMap.clear();
        LoggerUtil.info(this.getClass(), "Cleared all sync statuses");
    }
}