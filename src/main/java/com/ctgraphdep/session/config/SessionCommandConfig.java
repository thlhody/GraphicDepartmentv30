package com.ctgraphdep.session.config;

import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.fileOperations.data.SessionDataService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.session.service.SessionMonitorService;
import com.ctgraphdep.session.service.SessionService;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
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
            UserService userService,
            MainDefaultUserContextService mainDefaultUserContextService,
            ReadFileNameStatusService readFileNameStatusService,
            @Lazy SessionMonitorService sessionMonitorService,
            FolderStatus folderStatus,
            @Lazy SessionCommandFactory commandFactory,
            TimeValidationService validationService,
            NotificationService notificationService,
            @Lazy SessionService sessionService,
            SessionDataService sessionDataService,
            DataAccessService dataAccessService,
            WorktimeOperationContext worktimeOperationContext) {

        return new SessionContext(userService,
                mainDefaultUserContextService,
                readFileNameStatusService,
                sessionMonitorService,
                folderStatus,
                commandFactory,
                validationService,
                notificationService,
                sessionService,
                sessionDataService,
                dataAccessService,
                worktimeOperationContext);
    }
}