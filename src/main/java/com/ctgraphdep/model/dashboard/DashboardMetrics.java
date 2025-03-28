package com.ctgraphdep.model.dashboard;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardMetrics {
    private int onlineUsers;
    private int activeUsers;
    private String systemStatus;
    private String lastUpdate;
}