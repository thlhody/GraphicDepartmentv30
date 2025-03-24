package com.ctgraphdep.config;

import com.ctgraphdep.service.FileBackupService;
import com.ctgraphdep.service.FileSyncService;
import com.ctgraphdep.service.SyncStatusManager;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SyncServiceConfig {

    @Bean
    public SyncStatusManager syncStatusManager(PathConfig pathConfig, TimeValidationService timeValidationService) {
        return new SyncStatusManager(pathConfig, timeValidationService);
    }

    @Bean
    public FileSyncService fileSyncService(FileBackupService backupService, SyncStatusManager statusManager, TimeValidationService timeValidationService) {
        return new FileSyncService(backupService, statusManager, timeValidationService);
    }
}