package com.ctgraphdep.config;


import com.ctgraphdep.service.FileBackupService;
import com.ctgraphdep.service.FileSyncService;
import com.ctgraphdep.service.SyncStatusManager;
import com.ctgraphdep.validation.TimeValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SyncServiceConfig {

    @Bean
    public SyncStatusManager syncStatusManager(PathConfig pathConfig, TimeValidationService timeValidationService, ObjectMapper objectMapper) {
        return new SyncStatusManager(pathConfig, timeValidationService, objectMapper);
    }

    @Bean
    public FileSyncService fileSyncService(FileBackupService fileBackupService,SyncStatusManager syncStatusManager, TimeValidationService timeValidationService) {
        return new FileSyncService(syncStatusManager,timeValidationService,fileBackupService);
    }
}