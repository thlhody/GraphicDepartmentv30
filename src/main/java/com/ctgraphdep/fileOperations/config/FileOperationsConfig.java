package com.ctgraphdep.fileOperations.config;

import com.ctgraphdep.fileOperations.service.*;
import com.ctgraphdep.fileOperations.data.*;
import com.ctgraphdep.fileOperations.events.FileEventPublisher;
import com.ctgraphdep.fileOperations.events.BackupEventListener;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.monitoring.NetworkStatusMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Complete configuration for the simplified file operations architecture.
 * Includes event-driven backup system and domain-specific services.
 */
@Configuration
@EnableAsync
@EnableScheduling
@Import(BackupEventConfiguration.class)  // Import the backup event configuration
public class FileOperationsConfig {

    // ===== CORE FILE SERVICES =====

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
    public BackupService backupService(PathConfig pathConfig) {
        return new BackupService(pathConfig);
    }

    /**
     * Creates a backup utility service bean for admin operations.
     */
    @Bean
    public BackupUtilityService backupUtilityService(
            PathConfig pathConfig,
            BackupService backupService) {
        return new BackupUtilityService(pathConfig, backupService);
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
     * Creates a file obfuscation service bean.
     */
    @Bean
    public FileObfuscationService fileObfuscationService() {
        return new FileObfuscationService();
    }

    // ===== EVENT-DRIVEN BACKUP SYSTEM =====

    /**
     * Creates a file event publisher bean for publishing file operation events.
     * This is the central component of the event-driven backup system.
     */
    @Bean
    public FileEventPublisher fileEventPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            BackupEventConfiguration.BackupEventMonitor backupEventMonitor) {
        return new FileEventPublisher(applicationEventPublisher, backupEventMonitor);
    }

    /**
     * Creates a backup event listener bean for handling file operation events.
     * This listens to file write events and creates backups asynchronously.
     */
    @Bean
    public BackupEventListener backupEventListener(
            BackupService backupService,
            ApplicationEventPublisher applicationEventPublisher) {
        return new BackupEventListener(backupService, applicationEventPublisher);
    }

    // ===== READER AND WRITER SERVICES =====

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
     * Creates a file writer service bean with event-driven backup system.
     * Note: No direct BackupService dependency - backups now handled via events.
     */
    @Bean
    public FileWriterService fileWriterService(
            ObjectMapper objectMapper,
            FilePathResolver filePathResolver,
            SyncFilesService syncFilesService,
            PathConfig pathConfig,
            FileObfuscationService fileObfuscationService,
            FileEventPublisher fileEventPublisher) {
        return new FileWriterService(objectMapper, filePathResolver, syncFilesService, pathConfig, fileObfuscationService, fileEventPublisher);
    }

    // ===== DOMAIN-SPECIFIC DATA SERVICES =====

    /**
     * Creates the user data service for all user-related operations.
     */
    @Bean
    public UserDataService userDataService(
            FileWriterService fileWriterService,
            FileReaderService fileReaderService,
            FilePathResolver pathResolver,
            PathConfig pathConfig,
            ObjectMapper objectMapper) {
        return new UserDataService(fileWriterService, fileReaderService, pathConfig);
    }

    /**
     * Creates the worktime data service for all worktime-related operations.
     */
    @Bean
    public WorktimeDataService worktimeDataService(
            FileWriterService fileWriterService,
            FileReaderService fileReaderService,
            FilePathResolver pathResolver,
            PathConfig pathConfig, SyncFilesService syncFilesService) {
        return new WorktimeDataService(fileWriterService, fileReaderService, pathResolver, pathConfig, syncFilesService);
    }

    /**
     * Creates the register data service for all register-related operations.
     */
    @Bean
    public RegisterDataService registerDataService(
            FileWriterService fileWriterService,
            FileReaderService fileReaderService,
            FilePathResolver pathResolver,
            PathConfig pathConfig, SyncFilesService syncFilesService) {
        return new RegisterDataService(fileWriterService, fileReaderService, pathResolver, pathConfig, syncFilesService);
    }

    /**
     * Creates the session data service for all session-related operations.
     */
    @Bean
    public SessionDataService sessionDataService(
            FileWriterService fileWriterService,
            FileReaderService fileReaderService,
            FilePathResolver pathResolver,
            PathConfig pathConfig) {
        return new SessionDataService(fileWriterService, fileReaderService, pathResolver, pathConfig);
    }

    /**
     * Creates the time off data service for all time-off related operations.
     */
    @Bean
    public TimeOffDataService timeOffDataService(
            FileWriterService fileWriterService,
            FileReaderService fileReaderService,
            FilePathResolver pathResolver,
            PathConfig pathConfig, SyncFilesService syncFilesService){
        return new TimeOffDataService(fileWriterService, fileReaderService, pathResolver, pathConfig, syncFilesService);
    }

    // ===== MAIN FACADE SERVICE =====

    /**
     * Creates the main DataAccessService as a pure facade over the specialized services.
     * This maintains backward compatibility while providing a cleaner architecture.
     */
    @Bean
    public DataAccessService dataAccessService(
            UserDataService userDataService,
            WorktimeDataService worktimeDataService,
            RegisterDataService registerDataService,
            SessionDataService sessionDataService,
            TimeOffDataService timeOffDataService,
            PathConfig pathConfig) {
        return new DataAccessService(
                userDataService,
                worktimeDataService,
                registerDataService,
                sessionDataService,
                timeOffDataService,
                pathConfig);
    }

    // ===== NETWORK AND MONITORING =====

    /**
     * Creates the network monitor service bean.
     */
    @Bean
    @Primary
    public NetworkStatusMonitor networkStatusMonitor(PathConfig pathConfig) {
        return new NetworkStatusMonitor(pathConfig);
    }
}