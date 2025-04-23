package com.ctgraphdep.dashboard.service;

import com.ctgraphdep.dashboard.config.DashboardConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.DashboardViewModelDTO;
import com.ctgraphdep.model.dto.dashboard.DashboardMetricsDTO;
import com.ctgraphdep.service.OnlineMetricsService;
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

    public DashboardViewModelDTO buildDashboardViewModel(User currentUser, DashboardConfig config) {
        return DashboardViewModelDTO.builder()
                .pageTitle(config.getTitle())
                .username(currentUser.getUsername())
                .userFullName(currentUser.getName())
                .userRole(currentUser.getRole())
                .currentDateTime(LocalDateTime.now().format(WorkCode.DATE_TIME_FORMATTER))
                .cards(config.getCards())
                .metrics(buildDashboardMetrics())
                .build();
    }

    public DashboardMetricsDTO buildDashboardMetrics() {
        return DashboardMetricsDTO.builder()
                .onlineUsers(onlineMetricsService.getOnlineUserCount())
                .activeUsers(onlineMetricsService.getActiveUserCount())
                .systemStatus(folderStatus.getStatus().toString())
                .lastUpdate(LocalDateTime.now().format(WorkCode.DATE_TIME_FORMATTER))
                .build();
    }
}