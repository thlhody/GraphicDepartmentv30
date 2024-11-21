package com.ctgraphdep.model.dashboard;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import java.util.List;

@Data
@Builder
public class DashboardViewModel {
    @NonNull
    private String pageTitle;

    @NonNull
    private String username;

    @NonNull
    private String userFullName;

    @NonNull
    private String userRole;

    @NonNull
    private String currentDateTime;

    @NonNull
    private List<DashboardCard> cards;

    @NonNull
    private DashboardMetrics metrics;
}