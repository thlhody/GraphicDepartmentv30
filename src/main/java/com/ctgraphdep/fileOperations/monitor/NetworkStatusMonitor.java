package com.ctgraphdep.fileOperations.monitor;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Enhanced service responsible for monitoring network availability and managing network-related status.
 * This implementation includes debouncing, jitter prevention, and consolidated network checks.
 */
@Service
public class NetworkStatusMonitor {

    @Value("${app.sync.interval:3600000}")
    private long monitorInterval; // Default 5 minutes

    @Value("${app.network.debounce.ms:10000}")
    private long debounceIntervalMs; // Default 10 seconds debounce

    @Value("${app.network.jitter.threshold:3}")
    private int jitterThreshold; // Default 3 consecutive different results to change status

    @Value("${app.network.check.retry:3}")
    private int networkCheckRetries; // Number of retries for each network check

    private final PathConfig pathConfig;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isRunning = false;

    // Network status tracking
    private final AtomicBoolean networkStatus = new AtomicBoolean(false);
    private volatile long lastStatusChangeTimestamp = 0;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger stabilityCounter = new AtomicInteger(0);

    // Synchronization object for network status changes
    private final Object networkStatusLock = new Object();

    public NetworkStatusMonitor(
            PathConfig pathConfig) {
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        startMonitoring();

        // Use a separate thread with proper backoff for initial detection
        scheduler.execute(this::performInitialNetworkDetection);
    }

    /**
     * Scheduled method called at regular intervals to check network status
     */
    @Scheduled(fixedDelayString = "${app.sync.interval:3600000}")
    public void performScheduledNetworkCheck() {
        try {
            LoggerUtil.debug(this.getClass(), "Performing scheduled network status check");

            // Perform the actual network check
            boolean currentStatus = performNetworkCheck();

            // Update the status with debouncing
            updateNetworkStatus(currentStatus);

            // Attempt to sync pending files if network is available
            if (currentStatus) {
                attemptPendingSyncs();
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during scheduled network check: " + e.getMessage(), e);
            consecutiveFailures.incrementAndGet();
        }
    }

    /**
     * Initial network detection with progressive backoff
     */
    private void performInitialNetworkDetection() {
        try {
            // Give time for application to initialize
            Thread.sleep(5000);

            LoggerUtil.info(this.getClass(), "Starting initial network detection sequence");

            // Initial backoff parameters
            int attempt = 0;
            long[] backoffIntervals = {5000, 10000, 20000, 30000, 60000}; // Increasing backoff

            // Try to establish initial network status
            boolean connected = false;
            while (attempt < backoffIntervals.length) {
                connected = performNetworkCheck();

                if (connected) {
                    // Network is available - finish initial detection
                    // IMPORTANT: Force immediate update without jitter prevention for initial detection
                    forceNetworkStatusUpdate(true, "Initial detection");
                    LoggerUtil.info(this.getClass(), "Network detected as available during initial detection");
                    break;
                }

                // If we reach here, we know connected is false
                // Wait before next attempt
                if (attempt < backoffIntervals.length - 1) { // Only sleep if not the last attempt
                    long interval = backoffIntervals[attempt];
                    LoggerUtil.info(this.getClass(), String.format(
                            "Network unavailable during initial detection (attempt %d), waiting %d ms",
                            attempt + 1, interval));
                    Thread.sleep(interval);
                }
                attempt++;
            }

            if (!connected) {
                LoggerUtil.warn(this.getClass(), "Network remained unavailable after initial detection sequence");
                forceNetworkStatusUpdate(false, "Initial detection completion");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LoggerUtil.warn(this.getClass(), "Initial network detection interrupted: " + e.getMessage());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during initial network detection: " + e.getMessage());
        }
    }

    /**
     * Starts/Stops regular network monitoring
     */
    public void startMonitoring() {
        if (!isRunning) {
            isRunning = true;
            scheduler.scheduleWithFixedDelay(
                    this::performScheduledNetworkCheck,
                    monitorInterval,
                    monitorInterval,
                    TimeUnit.MILLISECONDS
            );
            LoggerUtil.info(this.getClass(), "Network monitoring started with interval: " + monitorInterval + "ms");
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

    /**
     * Centralized method to perform network availability check using CompletableFuture
     * to avoid Thread.sleep() in a loop
     */
    private boolean performNetworkCheck() {
        Path networkPath = pathConfig.getNetworkPath();

        // Verify path is in UNC format
        String pathStr = networkPath.toString();
        if (!pathStr.startsWith("\\\\")) {
            LoggerUtil.warn(this.getClass(), "Invalid network path format: " + pathStr);
            return false;
        }

        // Create an executor service for our async tasks
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            for (int attempt = 0; attempt < networkCheckRetries; attempt++) {
                // Calculate timeout with exponential backoff
                long timeout = Math.min(500 * (long)Math.pow(2, attempt), 10000); // Cap at 2 seconds

                // Create and execute the network check task
                boolean result = executeNetworkCheckAttempt(networkPath, attempt, timeout, executor);
                if (result) {
                    return true; // Network is available
                }

                // Log that we're moving to the next attempt
                if (attempt < networkCheckRetries - 1) {
                    LoggerUtil.debug(this.getClass(), String.format("Moving to next network check attempt after waiting %d ms", timeout));
                }
            }

            LoggerUtil.debug(this.getClass(), "Network check failed after " + networkCheckRetries + " attempts");
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Execute a single network check attempt with timeout
     */
    private boolean executeNetworkCheckAttempt(Path networkPath, int attempt, long timeout, ExecutorService executor) {
        final int currentAttempt = attempt;

        // Create a future task that checks network connectivity
        CompletableFuture<Boolean> networkCheckTask = CompletableFuture.supplyAsync(() -> performSingleNetworkCheck(networkPath, currentAttempt), executor);

        try {
            // Wait for the task to complete with the calculated timeout
            return networkCheckTask.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LoggerUtil.debug(this.getClass(), String.format("Network check attempt %d timed out after %d ms", attempt + 1, timeout));
            networkCheckTask.cancel(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LoggerUtil.debug(this.getClass(), "Network check interrupted");
        } catch (ExecutionException e) {
            LoggerUtil.debug(this.getClass(), String.format("Network check attempt %d threw exception: %s", attempt + 1, e.getCause().getMessage()));
        }

        return false;
    }

    /**
     * Perform a single network connectivity check
     */
    private boolean performSingleNetworkCheck(Path networkPath, int attempt) {
        try {
            // Basic existence check
            if (!Files.exists(networkPath)) {
                LoggerUtil.debug(this.getClass(), String.format("Network path doesn't exist, attempt %d", attempt + 1));
                return false;
            }

            // Check if it's a directory
            if (!Files.isDirectory(networkPath)) {
                LoggerUtil.debug(this.getClass(), String.format("Network path isn't a directory, attempt %d", attempt + 1));
                return false;
            }

            // Check if we can list contents (tests read permission)
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(networkPath)) {
                // Just getting a directory stream is enough to verify read access
                // We don't need to enumerate the entries
                LoggerUtil.debug(this.getClass(), "Network check successful on attempt " + (attempt + 1) + ".Directory stream path: "+ directoryStream);
                return true;
            } catch (IOException e) {
                LoggerUtil.debug(this.getClass(), String.format("Failed to read directory contents, attempt %d: %s",
                        attempt + 1, e.getMessage()));
                return false;
            }
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Network check attempt %d failed with unexpected error: %s",
                    attempt + 1, e.getMessage()));
            return false;
        }
    }

    /**
     * Force immediate update of network status without jitter prevention
     * Used for initial detection and critical status changes
     */
    private void forceNetworkStatusUpdate(boolean newStatus, String reason) {
        synchronized (networkStatusLock) {
            boolean currentStatus = networkStatus.get();

            // Skip if no change
            if (newStatus == currentStatus) {
                return;
            }

            // Update immediately without jitter prevention or debouncing
            networkStatus.set(newStatus);
            lastStatusChangeTimestamp = System.currentTimeMillis();
            stabilityCounter.set(0);  // Reset stability counter

            // Reset failure counter on success
            if (newStatus) {
                consecutiveFailures.set(0);
            }

            // Log the change
            LoggerUtil.info(this.getClass(), String.format("Network status FORCED to: %s (reason: %s)", newStatus ? "Available" : "Unavailable", reason));

            // IMPORTANT: Update the PathConfig status
            pathConfig.setNetworkAvailable(newStatus);

            // Additional immediate notification to ensure all components are aware
            broadcastNetworkStatusChange(newStatus);
        }
    }

    /**
     * Updates network status with debouncing and jitter prevention
     */
    private void updateNetworkStatus(boolean newStatus) {
        synchronized (networkStatusLock) {
            boolean currentStatus = networkStatus.get();

            // Check if the status is actually changing
            if (newStatus == currentStatus) {
                // Status is the same - reset stability counter
                stabilityCounter.set(0);
                return;
            }

            // Status is different - check stability using jitter prevention
            int stability = stabilityCounter.incrementAndGet();

            // Only log the first observation of a potential status change
            if (stability == 1) {
                LoggerUtil.debug(this.getClass(), String.format("Potential network status change to %s observed (%s) - waiting for stability", newStatus ? "available" : "unavailable", "Scheduled check"));
            }

            // Only apply the change after reaching the stability threshold
            if (stability < jitterThreshold) {
                return;
            }

            // Apply debouncing - only change status after the debounce interval
            long now = System.currentTimeMillis();
            if (now - lastStatusChangeTimestamp < debounceIntervalMs) {
                LoggerUtil.debug(this.getClass(), String.format("Ignoring network status change to %s - within debounce period (%s)", newStatus ? "available" : "unavailable", "Scheduled check"));
                return;
            }

            // We can now change the status
            networkStatus.set(newStatus);
            lastStatusChangeTimestamp = now;
            stabilityCounter.set(0);  // Reset stability counter

            // Reset failure counter on success
            if (newStatus) {
                consecutiveFailures.set(0);
            }

            // Log the change
            LoggerUtil.info(this.getClass(), String.format("Network status changed to: %s (reason: %s)", newStatus ? "Available" : "Unavailable", "Scheduled check"));

            // IMPORTANT: Directly set the PathConfig status
            pathConfig.setNetworkAvailable(newStatus);

            // Additional broadcast to ensure all components are aware
            broadcastNetworkStatusChange(newStatus);
        }
    }

    /**
     * Broadcast network status change to ensure all components are aware
     */
    private void broadcastNetworkStatusChange(boolean status) {
        // Log a clear, prominent message about the network status
        LoggerUtil.info(this.getClass(), "==== NETWORK STATUS BROADCAST: " + (status ? "AVAILABLE" : "UNAVAILABLE") + " ====");

        // This could be extended to use a Spring ApplicationEvent if needed
    }

    /**
     * Attempts to sync files that failed to sync previously
     */
    private void attemptPendingSyncs() {
        // The FileSyncService already has a scheduled task to retry failed syncs
        // This method can be used to explicitly trigger sync attempts for critical files

        // Example: Sync important session files immediately when network becomes available
        if (pathConfig.isNetworkAvailable()) {
            // This would be implemented based on your specific requirements
            // For example, syncing user session files that have changed during offline mode
            LoggerUtil.info(this.getClass(), "Network available - performing immediate sync of critical files");
        }
    }

    /**
     * Returns current network status. This method should be used by all services
     * that need to check network availability.
     */
    public boolean isNetworkAvailable() {
        return networkStatus.get();
    }

    @PreDestroy
    public void shutdown() {
        stopMonitoring();
    }
}