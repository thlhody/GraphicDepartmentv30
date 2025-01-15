package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.FileLocationInfo;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FileLocationStrategy {

    private final PathConfig pathConfig;
    private final FileSyncService fileSyncService;

    @Value("${app.sync.enabled:true}")
    private boolean syncEnabled;

    private final Map<String, FileLocationInfo> locationCache = new ConcurrentHashMap<>();
    private final AtomicBoolean forcedLocalMode = new AtomicBoolean(false);

    public FileLocationStrategy(PathConfig pathConfig, FileSyncService fileSyncService) {
        this.pathConfig = pathConfig;
        this.fileSyncService = fileSyncService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public Path resolveReadLocation(String filename) {
        FileLocationInfo locationInfo = getOrCreateLocationInfo(filename);
        updateLocationInfo(locationInfo);

        // For local-only files, always return local path
        if (pathConfig.isLocalOnlyFile(filename)) {
            return pathConfig.getLocalPath().resolve(filename);
        }

        // For network-primary files, try network first
        if (pathConfig.isNetworkPrimaryFile(filename)) {
            if (isNetworkAccessible()) {
                return pathConfig.getNetworkPath().resolve(filename);
            }
            // Fallback to local if we have a copy
            Path localPath = pathConfig.getLocalPath().resolve(filename);
            if (Files.exists(localPath)) {
                return localPath;
            }
            throw new RuntimeException("Network file unavailable and no local copy exists: " + filename);
        }

        // For dual-location files
        if (shouldReadFromNetwork(filename, locationInfo)) {
            return pathConfig.getNetworkPath().resolve(filename);
        }

        return pathConfig.getLocalPath().resolve(filename);
    }

    public Path resolveWriteLocation(String filename) {
        FileLocationInfo locationInfo = getOrCreateLocationInfo(filename);
        updateLocationInfo(locationInfo);

        // Always write local-only files to local path
        if (pathConfig.isLocalOnlyFile(filename)) {
            return pathConfig.getLocalPath().resolve(filename);
        }

        // For network-primary files
        if (pathConfig.isNetworkPrimaryFile(filename)) {
            if (isNetworkAccessible()) {
                Path networkPath = pathConfig.getNetworkPath().resolve(filename);
                locationInfo.setCurrentLocation(networkPath);
                return networkPath;
            }
            // Fallback to local if network unavailable
            locationInfo.setSyncPending(true);
            return pathConfig.getLocalPath().resolve(filename);
        }

        // For dual-location files, always write to local first
        Path localPath = pathConfig.getLocalPath().resolve(filename);
        locationInfo.setCurrentLocation(localPath);
        locationInfo.setSyncPending(true);

        return localPath;
    }

    public void handleSuccessfulWrite(String filename, Path location) {
        FileLocationInfo locationInfo = locationCache.get(filename);
        if (locationInfo != null) {
            locationInfo.setCurrentLocation(location);
            locationInfo.setLastAccessTime(LocalDateTime.now());

            if (syncEnabled && !pathConfig.isLocalOnlyFile(filename) &&
                    !location.startsWith(pathConfig.getNetworkPath())) {
                initiateSync(filename, location);
            }
        }
    }

    private void initiateSync(String filename, Path localPath) {
        if (isNetworkAccessible() && !pathConfig.isLocalOnlyFile(filename)) {
            Path networkPath = pathConfig.getNetworkPath().resolve(filename);
            fileSyncService.syncToNetwork(localPath, networkPath);

            FileLocationInfo locationInfo = locationCache.get(filename);
            if (locationInfo != null) {
                locationInfo.setSyncPending(true);
            }
        }
    }

    private boolean shouldReadFromNetwork(String filename, FileLocationInfo locationInfo) {
        if (forcedLocalMode.get() || !isNetworkAccessible()) {
            return false;
        }

        // Check if we have a pending sync
        if (locationInfo.isSyncPending() &&
                fileSyncService.isSyncPending(locationInfo.getCurrentLocation())) {
            return false;
        }

        return pathConfig.isNetworkAvailable();
    }

    private FileLocationInfo getOrCreateLocationInfo(String filename) {
        return locationCache.computeIfAbsent(filename,
                k -> new FileLocationInfo(pathConfig.getLocalPath().resolve(k)));
    }

    private void updateLocationInfo(FileLocationInfo info) {
        info.setNetworkAvailable(isNetworkAccessible());
        info.setLastAccessTime(LocalDateTime.now());
    }

    public boolean isNetworkAccessible() {
        return !forcedLocalMode.get() && pathConfig.isNetworkAvailable();
    }

    public void setForcedLocalMode(boolean forced) {
        boolean previous = forcedLocalMode.get();
        forcedLocalMode.set(forced);

        if (previous != forced) {
            LoggerUtil.info(this.getClass(),
                    String.format("Forced local mode changed from %s to %s",
                            previous, forced));
        }
    }

    public boolean isSyncPending(String filename) {
        FileLocationInfo info = locationCache.get(filename);
        return info != null && info.isSyncPending();
    }

    public void clearLocationCache() {
        locationCache.clear();
        LoggerUtil.info(this.getClass(), "Location cache cleared");
    }

    public Path getCurrentLocation(String filename) {
        FileLocationInfo info = locationCache.get(filename);
        return info != null ? info.getCurrentLocation() : null;
    }
}