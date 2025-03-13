package com.ctgraphdep.service;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;


@Service
public class OnlineMetricsService {

    private final SessionStatusService sessionStatusService;

    public OnlineMetricsService(SessionStatusService sessionStatusService) {
        this.sessionStatusService = sessionStatusService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public int getOnlineUserCount() {
        return sessionStatusService.getOnlineUserCount();
    }

    public int getActiveUserCount() {
        return sessionStatusService.getActiveUserCount();
    }

}