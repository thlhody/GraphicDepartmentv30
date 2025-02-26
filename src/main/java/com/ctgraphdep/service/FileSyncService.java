package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.concurrent.*;

@Service
public class FileSyncService {

    @Value("${app.sync.retry.max:3}")
    private int maxRetries;

    @Value("${app.sync.retry.delay:3600000}")
    private long retryDelay; // Default 1 hour

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final FileBackupService backupService;

    public FileSyncService(FileBackupService backupService) {
        this.backupService = backupService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Async
    public void syncToNetwork(Path localPath, Path networkPath) {
        LoggerUtil.info(this.getClass(),
                String.format("Syncing file\nFrom: %s\nTo: %s", localPath, networkPath));

        try {
            // Ensure network parent directory exists
            Files.createDirectories(networkPath.getParent());

            // Step 1: First write the local file as a backup on the network
            Path backupPath = backupService.getBackupPath(networkPath);
            Files.copy(localPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.debug(this.getClass(), "Created backup on network: " + backupPath);

            // Step 2: Then replace the actual network file
            Files.copy(localPath, networkPath, StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.debug(this.getClass(), "Updated main file on network: " + networkPath);

            // Step 3: If all went well, delete the backup file
            Files.deleteIfExists(backupPath);
            LoggerUtil.info(this.getClass(), "File sync completed successfully");

        } catch (Exception e) {
            // If any part fails, log the error but don't delete backup
            LoggerUtil.error(this.getClass(),
                    String.format("Failed to sync file: %s", e.getMessage()), e);
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