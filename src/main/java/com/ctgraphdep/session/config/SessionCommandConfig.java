package com.ctgraphdep.session.config;

import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.fileOperations.data.SessionDataService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.security.UserContextService;
import com.ctgraphdep.service.SessionService;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.service.*;
import org.springframework.context.annotation.Lazy;

@Configuration
public class SessionCommandConfig {

    @Bean
    public SessionContext sessionContext(
            WorktimeManagementService worktimeManagementService,
            UserService userService,
            UserContextService userContextService,
            SessionStatusService sessionStatusService,
            @Lazy SessionMonitorService sessionMonitorService,
            FolderStatus folderStatus,
            @Lazy SessionCommandFactory commandFactory,
            TimeValidationService validationService,
            NotificationService notificationService,
            @Lazy SessionService sessionService,
            SessionDataService sessionDataService, DataAccessService dataAccessService) {

        return new SessionContext(worktimeManagementService, userService, userContextService, sessionStatusService,
                sessionMonitorService, folderStatus, commandFactory, validationService, notificationService,
                sessionService,sessionDataService, dataAccessService);
    }
}