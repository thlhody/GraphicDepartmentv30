package com.ctgraphdep.fileOperations.monitor;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Service
public class NetworkCleanupService {
    private final PathConfig pathConfig;

    public NetworkCleanupService(PathConfig pathConfig) {
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Scheduled(fixedRate = 3600000) // Run every 60 minutes
    public void cleanupOrphanedBackups() {
        if (!pathConfig.isNetworkAvailable()) {
            return;
        }

        try {
            Path sessionDir = pathConfig.getNetworkSessionPath("", 0).getParent();
            if (!Files.exists(sessionDir)) {
                return;
            }

            // Use try-with-resources to ensure the stream is closed
            try (Stream<Path> paths = Files.list(sessionDir)) {
                paths.filter(path -> path.getFileName().toString().endsWith(WorkCode.BACKUP_EXTENSION))
                        .forEach(backupPath -> {
                            try {
                                // Get the main file path
                                String backupName = backupPath.getFileName().toString();
                                String mainName = backupName.substring(0, backupName.length() - WorkCode.BACKUP_EXTENSION.length());
                                Path mainPath = backupPath.resolveSibling(mainName);

                                // If main file exists and is valid, we can remove the backup
                                if (Files.exists(mainPath) && Files.size(mainPath) >= 3) {
                                    // Also check that main file is newer than backup
                                    if (Files.getLastModifiedTime(mainPath).compareTo(
                                            Files.getLastModifiedTime(backupPath)) >= 0) {
                                        // Main file exists and is newer, delete backup
                                        Files.deleteIfExists(backupPath);
                                        LoggerUtil.info(this.getClass(), "Cleaned up backup file: " + backupPath);
                                    }
                                }
                                // If main file doesn't exist or is invalid, keep the backup
                            } catch (Exception e) {
                                LoggerUtil.warn(this.getClass(), "Error checking backup file: " + e.getMessage());
                            }
                        });
            } // Stream is automatically closed here
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during backup cleanup: " + e.getMessage());
        }
    }
}
