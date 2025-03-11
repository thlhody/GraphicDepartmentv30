package com.ctgraphdep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.service.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Configuration for session command infrastructure
 */
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
            ContinuationTrackingService continuationTrackingService,
            PathConfig pathConfig,
            FolderStatusService folderStatusService,
            SessionCommandFactory commandFactory) {

        return new SessionContext(
                dataAccessService,
                workTimeService,
                userService,
                sessionStatusService,
                notificationService,
                backupService,
                sessionMonitorService,
                continuationTrackingService,
                pathConfig,
                folderStatusService,
                commandFactory);
    }
}