
package com.ctgraphdep.service;

import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.notification.CheckStalledNotificationsCommand;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Scheduled service to check for stalled notification that might have failed to auto-close
@Component
public class StalledNotificationChecker {
    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;

    public StalledNotificationChecker(SessionCommandService commandService, SessionCommandFactory commandFactory) {
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void checkStalledNotifications() {
        try {
            LoggerUtil.debug(this.getClass(), "Running scheduled check for stalled notification");
            CheckStalledNotificationsCommand command = commandFactory.createCheckStalledNotificationsCommand();
            commandService.executeCommand(command);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking for stalled notification: " + e.getMessage());
        }
    }
}
