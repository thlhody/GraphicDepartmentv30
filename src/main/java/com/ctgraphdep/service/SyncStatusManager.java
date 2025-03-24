package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.SyncStatus;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import jakarta.annotation.PreDestroy;
import lombok.Setter;
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
    private final TimeValidationService timeValidationService;
    private final Map<String, SyncStatus> syncStatusMap = new ConcurrentHashMap<>();

    // Function interface for sync operations
    public interface SyncOperation {
        boolean syncFile(Path localPath, Path networkPath);
    }

    // Method to set the sync operation implementation
    // Reference to the sync operation, set after construction
    @Setter
    private SyncOperation syncOperation;

    // Updated constructor without FileSyncService dependency
    public SyncStatusManager(PathConfig pathConfig, TimeValidationService timeValidationService) {
        this.pathConfig = pathConfig;
        this.timeValidationService = timeValidationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public SyncStatus createSyncStatus(String filename, Path localPath, Path networkPath) {
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                .createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);

        SyncStatus status = new SyncStatus(timeValues, localPath, networkPath);
        syncStatusMap.put(filename, status);
        return status;
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void processFailedSyncs() {
        // Make sure syncOperation is set
        if (syncOperation == null) {
            LoggerUtil.error(this.getClass(), "No sync operation implementation set!");
            return;
        }

        Set<String> failedFiles = getFailedSyncs();

        for (String filename : failedFiles) {
            SyncStatus status = syncStatusMap.get(filename);
            if (status != null && shouldRetry(status)) {
                // Only retry if we haven't exceeded max retries
                if (status.getRetryCount() < 5) {
                    retrySync(filename, status);
                } else {
                    LoggerUtil.warn(this.getClass(),
                            String.format("Max retries exceeded for file: %s, marking as failed", filename));
                    status.setSyncPending(false);
                }
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

        // Get current standardized time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                .createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        LocalDateTime currentTime = timeValues.getCurrentTime();

        // Exponential backoff based on retry count
        long currentBackoff = retryDelay * (long)Math.pow(2, status.getRetryCount() - 1);
        // Cap the maximum delay at 1 hour
        long actualDelay = Math.min(currentBackoff, 3600000);

        LocalDateTime nextRetryTime = status.getLastAttempt()
                .plusNanos(actualDelay * 1_000_000);
        return currentTime.isAfter(nextRetryTime);
    }

    private void retrySync(String filename, SyncStatus status) {
        if (pathConfig.isNetworkAvailable() && syncOperation != null) {
            int attempt = status.incrementRetryCount();
            LoggerUtil.info(this.getClass(),
                    String.format("Retrying sync for file: %s (attempt #%d)", filename, attempt));

            // Update last attempt time with standardized time
            GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                    .createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
            status.setLastAttempt(timeValues.getCurrentTime());

            boolean success = syncOperation.syncFile(status.getLocalPath(), status.getNetworkPath());

            if (success) {
                status.resetRetryCount();
                status.setSyncPending(false);
                status.setLastSuccessfulSync(timeValues.getCurrentTime());
                status.setErrorMessage(null);
            }
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