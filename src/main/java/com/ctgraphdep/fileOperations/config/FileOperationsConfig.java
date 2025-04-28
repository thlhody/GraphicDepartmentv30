package com.ctgraphdep.fileOperations.config;

import com.ctgraphdep.fileOperations.monitor.NetworkStatusMonitor;
import com.ctgraphdep.fileOperations.service.*;
import com.ctgraphdep.validation.TimeValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for the file operations package.
 * Sets up the necessary beans and services.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class FileOperationsConfig {

    /**
     * Creates a file transaction manager bean.
     */
    @Bean
    public FileTransactionManager fileTransactionManager() {
        return new FileTransactionManager();
    }

    /**
     * Creates a file backup service bean.
     */
    @Bean
    public BackupService backupService() {
        return new BackupService();
    }

    /**
     * Creates a file path resolver bean.
     */
    @Bean
    public FilePathResolver filePathResolver(PathConfig pathConfig) {
        return new FilePathResolver(pathConfig);
    }

    /**
     * Creates a file sync service bean.
     */
    @Bean
    public SyncFilesService syncFilesService(
            BackupService backupService,
            TimeValidationService timeValidationService,
            FilePathResolver filePathResolver) {
        return new SyncFilesService(backupService, timeValidationService, filePathResolver);
    }

    /**
     * Creates a file reader service bean.
     */
    @Bean
    public FileReaderService fileReaderService(
            ObjectMapper objectMapper,
            FilePathResolver filePathResolver,
            BackupService backupService,
            PathConfig pathConfig,
            FileObfuscationService fileObfuscationService) {
        return new FileReaderService(objectMapper, filePathResolver, backupService, pathConfig, fileObfuscationService);
    }

    /**
     * Creates a file writer service bean.
     */
    @Bean
    public FileWriterService fileWriterService(
            ObjectMapper objectMapper,
            FilePathResolver filePathResolver,
            BackupService backupService,
            SyncFilesService syncFilesService,
            FileTransactionManager fileTransactionManager,
            PathConfig pathConfig,
            FileObfuscationService fileObfuscationService) {
        return new FileWriterService(objectMapper, filePathResolver, backupService, syncFilesService, fileTransactionManager, pathConfig, fileObfuscationService);
    }

    /**
     * Creates the network monitor service bean.
     */
    @Bean
    @Primary
    public NetworkStatusMonitor networkStatusMonitor(PathConfig pathConfig) {
        return new NetworkStatusMonitor(pathConfig);
    }
}
