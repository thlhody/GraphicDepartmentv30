package com.ctgraphdep.security;

import com.ctgraphdep.fileOperations.events.BackupEventListener;
import com.ctgraphdep.fileOperations.service.BackupService;
import com.ctgraphdep.service.cache.CheckValuesCacheManager;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.service.cache.RegisterCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    private final CheckValuesCacheManager checkValuesCacheManager;
    private final RegisterCacheService registerCacheService;
    private final MainDefaultUserContextService mainDefaultUserContextService;
    private final BackupEventListener backupEventListener;
    private final BackupService backupService;


    public CustomLogoutSuccessHandler(CheckValuesCacheManager checkValuesCacheManager, RegisterCacheService registerCacheService, MainDefaultUserContextService mainDefaultUserContextService, BackupEventListener backupEventListener, BackupService backupService) {
        this.checkValuesCacheManager = checkValuesCacheManager;
        this.registerCacheService = registerCacheService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        this.backupEventListener = backupEventListener;
        this.backupService = backupService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        try {
            // Clear all cached check values when any user logs out
            checkValuesCacheManager.clearAllCachedCheckValues();
            registerCacheService.clearAllCache();
            mainDefaultUserContextService.handleLogout();

            // Clear backup sync caches for the user
            if (authentication != null) {
                String username = authentication.getName();

                // Clear user-specific backup sync caches
                backupEventListener.clearUserBackupSyncCache(username);
                backupService.clearSyncedBackupFilesForUser(username);

                LoggerUtil.info(this.getClass(), "User logged out and cleared all caches including backup sync: " + username);
            } else {
                LoggerUtil.info(this.getClass(), "Session ended and cleared all caches");
            }


            // Log the logout
            if (authentication != null) {
                LoggerUtil.info(this.getClass(), "User logged out and cleared check values cache: " + authentication.getName());
            } else {
                LoggerUtil.info(this.getClass(), "Session ended and cleared check values cache");
            }

            // Redirect to home page (or login page)
            response.sendRedirect(request.getContextPath() + "/");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during logout: " + e.getMessage(), e);
            // Default redirect if there's an error
            response.sendRedirect(request.getContextPath() + "/");
        }
    }
}