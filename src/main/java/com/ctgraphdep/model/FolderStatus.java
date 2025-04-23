package com.ctgraphdep.model;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.model.dto.SyncFolderStatusDTO;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class FolderStatus {

    private final PathConfig pathConfig;
    private final AtomicInteger retryCount;
    private final AtomicLong lastSuccessfulSync;
    private final AtomicReference<String> lastError;

    public FolderStatus(PathConfig pathConfig) {
        this.pathConfig = pathConfig;
        this.retryCount = new AtomicInteger(0);
        this.lastSuccessfulSync = new AtomicLong(0);
        this.lastError = new AtomicReference<>(null);
        LoggerUtil.initialize(this.getClass(), null);
    }

    public SyncFolderStatusDTO getStatus() {
        return new SyncFolderStatusDTO(
                Files.exists(pathConfig.getNetworkPath()),
                Files.exists(pathConfig.getLocalPath()),
                lastError.get(),
                retryCount.get(),
                lastSuccessfulSync.get()
        );
    }
}