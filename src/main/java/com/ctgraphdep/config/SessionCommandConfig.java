package com.ctgraphdep.config;

import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.service.*;
import org.springframework.context.annotation.Lazy;

// Configuration for session command infrastructure

@Configuration
public class SessionCommandConfig {

    @Bean
    public SessionContext sessionContext(
            DataAccessService dataAccessService,
            UserWorkTimeService workTimeService,
            UserService userService,
            SessionStatusService sessionStatusService,
            SystemNotificationService notificationService,
            SystemNotificationBackupService backupService,
            @Lazy SessionMonitorService sessionMonitorService,
            PathConfig pathConfig,
            FolderStatusService folderStatusService,
            SessionCommandFactory commandFactory,
            TimeValidationService timeValidationService) {

        return new SessionContext(
                dataAccessService,
                workTimeService,
                userService,
                sessionStatusService,
                notificationService,
                backupService,
                sessionMonitorService,
                pathConfig,
                folderStatusService,
                commandFactory,
                timeValidationService);
    }
}