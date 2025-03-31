package com.ctgraphdep.model.dto.dashboard;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardMetricsDTO {
    private int onlineUsers;
    private int activeUsers;
    private String systemStatus;
    private String lastUpdate;
}