package com.ctgraphdep.service;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dashboard.*;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DashboardService {
    private final OnlineMetricsService onlineMetricsService;
    private final FolderStatusService folderStatusService;
    private final UserService userService;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public DashboardService(
            OnlineMetricsService onlineMetricsService,
            FolderStatusService folderStatusService,
            UserService userService) {
        this.onlineMetricsService = onlineMetricsService;
        this.folderStatusService = folderStatusService;
        this.userService = userService;
    }

    public DashboardViewModel buildDashboardViewModel(User currentUser, DashboardConfiguration config) {
        return DashboardViewModel.builder()
                .pageTitle(config.getTitle())
                .username(currentUser.getUsername())
                .userFullName(currentUser.getName())
                .userRole(currentUser.getRole())
                .currentDateTime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                .cards(config.getCards())
                .metrics(buildDashboardMetrics())
                .build();
    }

    public DashboardMetrics buildDashboardMetrics() {
        return DashboardMetrics.builder()
                .onlineUsers(onlineMetricsService.getOnlineUserCount())
                .activeUsers(onlineMetricsService.getActiveUserCount())
                .pendingTasks(0) // TODO: Implement task tracking
                .systemStatus(folderStatusService.getStatus().toString())
                .lastUpdate(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                .build();
    }
}