package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.SyncStatus;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.concurrent.*;
@Service
@EnableAsync(proxyTargetClass = true) //remove this
public class FileSyncService implements SyncStatusManager.SyncOperation {

    @Value("${app.sync.retry.max:3}")
    private int maxRetries;

    @Value("${app.sync.retry.delay:3600000}")
    private long retryDelay; // Default 1 hour

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final SyncStatusManager statusManager;
    private final TimeValidationService timeValidationService;
    private final FileBackupService fileBackupService;

    public FileSyncService(SyncStatusManager statusManager, TimeValidationService timeValidationService, FileBackupService fileBackupService) {
        this.statusManager = statusManager;
        this.timeValidationService = timeValidationService;
        this.fileBackupService = fileBackupService;
        LoggerUtil.initialize(this.getClass(), null);

        // Register this instance as the sync operation implementation
        this.statusManager.setSyncOperation(this);
    }

    @Async
    @Override
    public boolean syncFile(Path localPath, Path networkPath) {
        return syncToNetwork(localPath, networkPath);
    }

    @Async
    public boolean syncToNetwork(Path localPath, Path networkPath) {
        LoggerUtil.info(this.getClass(), String.format("Syncing file\nFrom: %s\nTo: %s", localPath, networkPath));

        String filename = localPath.getFileName().toString();
        SyncStatus status = statusManager.createSyncStatus(filename, localPath, networkPath);
        // Add null check before using status
        if (status != null) {
            status.setSyncInProgress(true);
        }

        try {
            // Ensure network parent directory exists
            Files.createDirectories(networkPath.getParent());

            // Step 1: First write the local file as a backup on the network
            Path backupPath = fileBackupService.getBackupPath(networkPath);
            Files.copy(localPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.debug(this.getClass(), "Created backup on network: " + backupPath);

            // Step 2: Then replace the actual network file
            Files.copy(localPath, networkPath, StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.debug(this.getClass(), "Updated main file on network: " + networkPath);

            // Step 3: If all went well, delete the backup file
            Files.deleteIfExists(backupPath);
            LoggerUtil.info(this.getClass(), "File sync completed successfully");

            // Get standardized time
            GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);

            // Update status
            if (status != null) {
                status.setSyncInProgress(false);
                status.setSyncPending(false);
                status.setLastSuccessfulSync(timeValues.getCurrentTime());
                status.resetRetryCount();
                status.setErrorMessage(null);
            }


            return true;  // Return success

        } catch (Exception e) {
            // If any part fails, log the error but don't delete backup
            LoggerUtil.error(this.getClass(), String.format("Failed to sync file: %s", e.getMessage()), e);

            // Update status
            if (status != null) {
                status.setSyncInProgress(false);
                status.setSyncPending(true);
                status.setErrorMessage(e.getMessage());
            }

            return false;  // Return failure
        }
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