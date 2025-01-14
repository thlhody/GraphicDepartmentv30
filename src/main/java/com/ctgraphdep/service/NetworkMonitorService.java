package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Service
public class NetworkMonitorService {

    @Value("${app.sync.interval:300000}")
    private long monitorInterval; // Default 5 minutes

    private final PathConfig pathConfig;
    private final FileLocationStrategy locationStrategy;
    private final SyncStatusManager syncStatusManager;
    private final FileSyncService syncService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isRunning = false;
    private volatile LocalDateTime lastNetworkCheck = null;
    private volatile boolean lastKnownNetworkStatus = false;

    private final List<NetworkStatusListener> statusListeners = new CopyOnWriteArrayList<>();

    public interface NetworkStatusListener {
        void onNetworkStatusChanged(boolean isAvailable);
    }

    public NetworkMonitorService(
            PathConfig pathConfig,
            FileLocationStrategy locationStrategy,
            SyncStatusManager syncStatusManager,
            FileSyncService syncService) {
        this.pathConfig = pathConfig;
        this.locationStrategy = locationStrategy;
        this.syncStatusManager = syncStatusManager;
        this.syncService = syncService;
        LoggerUtil.initialize(this.getClass(), "Initializing Network Monitor Service");
    }

    @PostConstruct
    public void init() {
        startMonitoring();
    }

    @PreDestroy
    public void shutdown() {
        stopMonitoring();
    }

    public void startMonitoring() {
        if (!isRunning) {
            isRunning = true;
            scheduler.scheduleWithFixedDelay(
                    this::checkNetworkStatus,
                    0,
                    monitorInterval,
                    TimeUnit.MILLISECONDS
            );
            LoggerUtil.info(this.getClass(), "Network monitoring started");
        }
    }

    public void stopMonitoring() {
        isRunning = false;
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LoggerUtil.info(this.getClass(), "Network monitoring stopped");
    }

    @Scheduled(fixedDelayString = "${app.sync.interval:300000}")
    public void checkNetworkStatus() {
        if (!isRunning) {
            return;
        }

        try {
            lastNetworkCheck = LocalDateTime.now();
            Path networkPath = pathConfig.getNetworkPath();
            boolean currentStatus = Files.exists(networkPath) && Files.isWritable(networkPath);

            if (currentStatus != lastKnownNetworkStatus) {
                handleNetworkStatusChange(currentStatus);
            }

            if (currentStatus) {
                attemptPendingSyncs();
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error checking network status: " + e.getMessage());
            handleNetworkStatusChange(false);
        }
    }

    private void handleNetworkStatusChange(boolean newStatus) {
        lastKnownNetworkStatus = newStatus;
        pathConfig.updateNetworkStatus();
        locationStrategy.setForcedLocalMode(!newStatus);

        // Notify all listeners
        notifyStatusListeners(newStatus);

        LoggerUtil.info(this.getClass(),
                String.format("Network status changed to: %s", newStatus ? "Available" : "Unavailable"));
    }

    private void attemptPendingSyncs() {
        Set<String> failedSyncs = syncStatusManager.getFailedSyncs();
        if (!failedSyncs.isEmpty()) {
            LoggerUtil.info(this.getClass(),
                    String.format("Attempting to sync %d failed files", failedSyncs.size()));

            for (String filename : failedSyncs) {
                Path localPath = pathConfig.getLocalPath().resolve(filename);
                Path networkPath = pathConfig.getNetworkPath().resolve(filename);
                syncService.syncToNetwork(localPath, networkPath);
            }
        }
    }

    public void addNetworkStatusListener(NetworkStatusListener listener) {
        statusListeners.add(listener);
    }

    public void removeNetworkStatusListener(NetworkStatusListener listener) {
        statusListeners.remove(listener);
    }

    private void notifyStatusListeners(boolean status) {
        for (NetworkStatusListener listener : statusListeners) {
            try {
                listener.onNetworkStatusChanged(status);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(),
                        String.format("Error notifying listener: %s", e.getMessage()));
            }
        }
    }

    public boolean isNetworkAvailable() {
        return lastKnownNetworkStatus;
    }

    public LocalDateTime getLastCheckTime() {
        return lastNetworkCheck;
    }

    public void forceNetworkCheck() {
        checkNetworkStatus();
    }
}