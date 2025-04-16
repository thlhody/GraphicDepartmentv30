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
    private volatile boolean lastKnownNetworkStatus = false;
    private int consecutiveFailures = 0;

    public NetworkMonitorService(
            PathConfig pathConfig,
            FileLocationStrategy locationStrategy,
            SyncStatusManager syncStatusManager,
            FileSyncService syncService) {
        this.pathConfig = pathConfig;
        this.locationStrategy = locationStrategy;
        this.syncStatusManager = syncStatusManager;
        this.syncService = syncService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        startMonitoring();
        startAggressiveNetworkDetection();

    }

    private void startAggressiveNetworkDetection() {
        Thread aggressiveChecker = new Thread(() -> {
            try {
                // Wait 30 seconds to let the system settle
                Thread.sleep(30000);

                LoggerUtil.info(this.getClass(), "Starting aggressive network detection");

                // Check every 10 seconds for first minute, then every 30 for 5 min, then every minute for 10 min
                for (int attempt = 0; attempt < 30; attempt++) {
                    int delay;
                    if (attempt < 6) {
                        delay = 10000; // Every 10 seconds for first minute
                    } else if (attempt < 16) {
                        delay = 30000; // Every 30 seconds for next 5 minutes
                    } else {
                        delay = 60000; // Every minute for remaining time
                    }

                    // Check network status
                    checkNetworkStatusAggressively();

                    // Exit if network is now available
                    if (lastKnownNetworkStatus) {
                        LoggerUtil.info(this.getClass(),
                                String.format("Network became available after %d aggressive checks", attempt + 1));
                        break;
                    }

                    LoggerUtil.debug(this.getClass(),
                            String.format("Aggressive check #%d: Network still unavailable, waiting %dms",
                                    attempt + 1, delay));
                    Thread.sleep(delay);
                }

                LoggerUtil.info(this.getClass(), "Completed aggressive network detection phase");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error in aggressive network detection: " + e.getMessage());
            }
        });

        aggressiveChecker.setDaemon(true);
        aggressiveChecker.setName("AggressiveNetworkDetection");
        aggressiveChecker.start();
    }

    private synchronized void checkNetworkStatusAggressively() {
        try {
            // Check network path with extra validation
            Path networkPath = pathConfig.getNetworkPath();
            String pathStr = networkPath.toString();

            // Check for valid UNC path format
            if (!pathStr.startsWith("\\\\")) {
                LoggerUtil.warn(this.getClass(), "Invalid network path format: " + pathStr);
                return;
            }

            boolean networkAccessible = Files.exists(networkPath) && Files.isDirectory(networkPath);

            // Test more thoroughly if exists check passes
            if (networkAccessible) {
                try {
                    Path testFile = networkPath.resolve(".test_" + System.currentTimeMillis() + ".tmp");
                    Files.createFile(testFile);
                    Files.delete(testFile);
                    LoggerUtil.info(this.getClass(), "Network write test successful: " + networkPath);
                } catch (Exception e) {
                    networkAccessible = false;
                    LoggerUtil.debug(this.getClass(), "Network write test failed: " + e.getMessage());
                }
            }

            if (networkAccessible != lastKnownNetworkStatus) {
                handleNetworkStatusChange(networkAccessible);
            }

            // Reset failure count on success
            if (networkAccessible) {
                consecutiveFailures = 0;
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error checking network status aggressively: " + e.getMessage());
            handleNetworkStatusChange(false);
        }
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
            Path networkPath = pathConfig.getNetworkPath();

            boolean currentStatus = false;
            if (Files.exists(networkPath) && Files.isDirectory(networkPath)) {
                try {
                    // Use same test file method as the aggressive check
                    Path testFile = networkPath.resolve(".test_" + System.currentTimeMillis());
                    Files.createFile(testFile);
                    Files.delete(testFile);
                    currentStatus = true;
                    LoggerUtil.debug(this.getClass(), "Network write test successful in regular check");
                } catch (Exception e) {
                    LoggerUtil.debug(this.getClass(), "Network write test failed in regular check: " + e.getMessage());
                    currentStatus = false;
                }
            }

            // If status changed, update it
            if (currentStatus != lastKnownNetworkStatus) {
                handleNetworkStatusChange(currentStatus);
            } else if (!currentStatus) {
                // If still not available, increment failure counter
                consecutiveFailures++;

                // Log with decreasing frequency as failures increase
                if (consecutiveFailures % getLogFrequency() == 0) {
                    LoggerUtil.info(this.getClass(),
                            String.format("Network still unavailable after %d checks", consecutiveFailures));
                }
            }

            if (currentStatus) {
                consecutiveFailures = 0;
                attemptPendingSyncs();
            }

            if (Files.exists(networkPath)) {
                boolean isReadable = Files.isReadable(networkPath);
                boolean isWritable = Files.isWritable(networkPath);
                boolean isDirectory = Files.isDirectory(networkPath);

                LoggerUtil.debug(this.getClass(), String.format(
                        "Network path checks - Exists: true, Readable: %b, Writable: %b, Directory: %b",
                        isReadable, isWritable, isDirectory));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error checking network status: " + e.getMessage());
            consecutiveFailures++;
            handleNetworkStatusChange(false);
        }
    }

    private int getLogFrequency() {
        // Log less frequently as failures increase: 1, 2, 4, 8, 16, 32, 64, 128, 256, 512
        return Math.max(1, 1 << Math.min(9, consecutiveFailures / 10));
    }

    private void handleNetworkStatusChange(boolean newStatus) {
        lastKnownNetworkStatus = newStatus;
        pathConfig.updateNetworkStatus();
        locationStrategy.setForcedLocalMode(!newStatus);

        LoggerUtil.info(this.getClass(),
                String.format("Network status changed to: %s", newStatus ? "Available" : "Unavailable"));
        // Reset failure counter on status change
        if (newStatus) {
            consecutiveFailures = 0;
        }
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

    @PreDestroy
    public void shutdown() {
        stopMonitoring();
    }
}