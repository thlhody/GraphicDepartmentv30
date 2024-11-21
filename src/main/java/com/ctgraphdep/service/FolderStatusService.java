package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.SyncFolderStatus;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class FolderStatusService {
    private static final int MAX_RETRIES = 3;

    private final PathConfig pathConfig;
    private final AtomicInteger retryCount;
    private final AtomicLong lastSuccessfulSync;
    private final AtomicReference<String> lastError;

    public FolderStatusService(PathConfig pathConfig) {
        this.pathConfig = pathConfig;
        this.retryCount = new AtomicInteger(0);
        this.lastSuccessfulSync = new AtomicLong(0);
        this.lastError = new AtomicReference<>(null);
        LoggerUtil.initialize(this.getClass(), "Initializing Folder Status Service");
    }

    public SyncFolderStatus getStatus() {
        return new SyncFolderStatus(
                Files.exists(pathConfig.getNetworkPath()),
                Files.exists(pathConfig.getLocalPath()),
                lastError.get(),
                retryCount.get(),
                lastSuccessfulSync.get()
        );
    }

    public void updateStatus(boolean success, String error) {
        if (success) {
            lastSuccessfulSync.set(System.currentTimeMillis());
            retryCount.set(0);
            lastError.set(null);
        } else {
            int currentRetries = retryCount.incrementAndGet();
            lastError.set(error);
            if (currentRetries >= MAX_RETRIES) {
                retryCount.set(0);
            }
        }
    }
}