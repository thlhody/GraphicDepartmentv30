
package com.ctgraphdep.service;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Scheduled service to check for stalled notification that might have failed to auto-close
@Component
public class StalledNotificationChecker {

    private final SystemNotificationBackupService backupService;

    public StalledNotificationChecker(SystemNotificationBackupService backupService) {
        this.backupService = backupService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Check for stalled notification every 5 minutes
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void checkStalledNotifications() {
        try {
            LoggerUtil.debug(this.getClass(), "Running scheduled check for stalled notification");
            backupService.checkForStalledNotifications();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking for stalled notification: " + e.getMessage());
        }
    }
}
