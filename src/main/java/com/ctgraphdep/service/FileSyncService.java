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

    public FileSyncService() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Async
    public void syncToNetwork(Path localPath, Path networkPath) {
        LoggerUtil.info(this.getClass(),
                String.format("Syncing file\nFrom: %s\nTo: %s", localPath, networkPath));

        try {
            // Ensure network parent directory exists
            Files.createDirectories(networkPath.getParent());
            LoggerUtil.debug(this.getClass(), "Created network directories");

            // Copy file with replace option
            Files.copy(localPath, networkPath, StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.info(this.getClass(), "File sync completed successfully");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Failed to sync file: %s", e.getMessage()), e);
            throw new RuntimeException("Failed to sync file: " + e.getMessage(), e);
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