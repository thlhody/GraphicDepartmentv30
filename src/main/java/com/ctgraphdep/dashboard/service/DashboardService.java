package com.ctgraphdep.dashboard.service;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.dashboard.config.DashboardConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UsersCheckValueEntry;
import com.ctgraphdep.model.dto.DashboardViewModelDTO;
import com.ctgraphdep.model.dto.dashboard.DashboardMetricsDTO;
import com.ctgraphdep.service.cache.CheckValuesCacheManager;
import com.ctgraphdep.register.service.CheckValuesService;
import com.ctgraphdep.service.OnlineMetricsService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DashboardService {
    private final OnlineMetricsService onlineMetricsService;
    private final FolderStatus folderStatus;
    private final CheckValuesService checkValuesService;
    private final CheckValuesCacheManager checkValuesCacheManager;
    private final TimeValidationService timeValidationService;

    public DashboardService(
            OnlineMetricsService onlineMetricsService,
            FolderStatus folderStatus,
            CheckValuesService checkValuesService,
            CheckValuesCacheManager checkValuesCacheManager, TimeValidationService timeValidationService) {
        this.onlineMetricsService = onlineMetricsService;
        this.folderStatus = folderStatus;
        this.checkValuesService = checkValuesService;
        this.checkValuesCacheManager = checkValuesCacheManager;
        this.timeValidationService = timeValidationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public DashboardViewModelDTO buildDashboardViewModel(User currentUser, DashboardConfig config) {
        try {
            // Debug: Print the exact role string
            LoggerUtil.info(this.getClass(), "User role in buildDashboardViewModel: '" + currentUser.getRole() + "'");

            // For debugging, conditionally load for specific roles without the check
            if (currentUser.getRole().equals(SecurityConstants.ROLE_USER_CHECKING) ||
                    currentUser.getRole().equals(SecurityConstants.ROLE_CHECKING) ||
                    currentUser.getRole().equals(SecurityConstants.ROLE_TL_CHECKING)) {

                if (!checkValuesCacheManager.hasCachedCheckValues(currentUser.getUsername())) {
                    LoggerUtil.info(this.getClass(), "Loading check values for " + currentUser.getUsername() + " with role " + currentUser.getRole());
                    loadAndCacheCheckValues(currentUser);
                }
            } else {
                LoggerUtil.info(this.getClass(), "Not loading check values for role: " + currentUser.getRole());
            }

            return DashboardViewModelDTO.builder()
                    .pageTitle(config.getTitle())
                    .username(currentUser.getUsername())
                    .userFullName(currentUser.getName())
                    .userRole(currentUser.getRole())
                    .currentDateTime(getStandardCurrentTime().format(WorkCode.DATE_TIME_FORMATTER))
                    .cards(config.getCards())
                    .metrics(buildDashboardMetrics())
                    .build();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in buildDashboardViewModel: " + e.getMessage());
            // Return a basic DTO without the loading attempt
            return DashboardViewModelDTO.builder()
                    .pageTitle(config.getTitle())
                    .username(currentUser.getUsername())
                    .userFullName(currentUser.getName())
                    .userRole(currentUser.getRole())
                    .currentDateTime(getStandardCurrentTime().format(WorkCode.DATE_TIME_FORMATTER))
                    .cards(config.getCards())
                    .metrics(buildDashboardMetrics())
                    .build();
        }
    }

    /**
     * Loads and caches check values for a user with checking roles
     */
    public void loadAndCacheCheckValues(User user) {
        if (user == null || user.getUsername() == null || user.getUserId() == null) {
            LoggerUtil.warn(this.getClass(), "Cannot load check values: user, username, or userId is null");
            return;
        }

        try {
            LoggerUtil.info(this.getClass(), String.format("LOADING VALUES: Attempting to load check values for %s (ID: %d)",
                    user.getUsername(), user.getUserId()));

            UsersCheckValueEntry userCheckValues = checkValuesService.getUserCheckValues(user.getUsername(), user.getUserId());

            if (userCheckValues == null) {
                LoggerUtil.warn(this.getClass(), String.format("NO VALUES FOUND: No check values found for user %s", user.getUsername()));
                return;
            }

            if (userCheckValues.getCheckValuesEntry() == null) {
                LoggerUtil.warn(this.getClass(), String.format("NULL CHECK VALUES: Check values entry is null for user %s", user.getUsername()));
                return;
            }

            LoggerUtil.info(this.getClass(), String.format("VALUES FOUND: Found values for %s: workUnitsPerHour=%f",
                    user.getUsername(), userCheckValues.getCheckValuesEntry().getWorkUnitsPerHour()));

            checkValuesCacheManager.cacheCheckValues(user.getUsername(), userCheckValues.getCheckValuesEntry());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("ERROR LOADING VALUES: Error for user %s: %s",
                    user.getUsername(), e.getMessage()), e);
            // Consider whether we should throw a more specific exception or handle it differently
        }
    }

    public DashboardMetricsDTO buildDashboardMetrics() {
        return DashboardMetricsDTO.builder()
                .onlineUsers(onlineMetricsService.getOnlineUserCount())
                .activeUsers(onlineMetricsService.getActiveUserCount())
                .systemStatus(folderStatus.getStatus().toString())
                .lastUpdate(getStandardCurrentTime().format(WorkCode.DATE_TIME_FORMATTER))
                .build();
    }

    private LocalDateTime getStandardCurrentTime() {
        // Get standardized time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        return timeValues.getCurrentTime();
    }
}