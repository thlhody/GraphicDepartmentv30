package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dashboard.*;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class DashboardService {
    private final OnlineMetricsService onlineMetricsService;
    private final FolderStatus folderStatus;

    public DashboardService(OnlineMetricsService onlineMetricsService, FolderStatus folderStatus) {
        this.onlineMetricsService = onlineMetricsService;
        this.folderStatus = folderStatus;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public DashboardViewModel buildDashboardViewModel(User currentUser, DashboardConfiguration config) {
        return DashboardViewModel.builder()
                .pageTitle(config.getTitle())
                .username(currentUser.getUsername())
                .userFullName(currentUser.getName())
                .userRole(currentUser.getRole())
                .currentDateTime(LocalDateTime.now().format(WorkCode.DATE_TIME_FORMATTER))
                .cards(config.getCards())
                .metrics(buildDashboardMetrics())
                .build();
    }

    public DashboardMetrics buildDashboardMetrics() {
        return DashboardMetrics.builder()
                .onlineUsers(onlineMetricsService.getOnlineUserCount())
                .activeUsers(onlineMetricsService.getActiveUserCount())
                .systemStatus(folderStatus.getStatus().toString())
                .lastUpdate(LocalDateTime.now().format(WorkCode.DATE_TIME_FORMATTER))
                .build();
    }
}