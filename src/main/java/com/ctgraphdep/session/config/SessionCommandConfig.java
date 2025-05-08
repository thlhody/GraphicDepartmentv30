package com.ctgraphdep.session.config;

import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.service.SessionService;
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
    public SessionContext sessionContext(DataAccessService dataAccessService,
                                         WorktimeManagementService worktimeManagementService,
                                         UserService userService,
                                         SessionStatusService sessionStatusService,
                                         NotificationService notificationService,
                                         @Lazy SessionMonitorService sessionMonitorService,
                                         FolderStatus folderStatus,
                                         @Lazy SessionCommandFactory commandFactory,
                                         TimeValidationService timeValidationService,
                                         @Lazy SessionService sessionService) {

        return new SessionContext(dataAccessService,
                worktimeManagementService,
                userService,
                sessionStatusService,
                sessionMonitorService,
                folderStatus,
                commandFactory,
                timeValidationService,
                notificationService,
                sessionService);
    }
}