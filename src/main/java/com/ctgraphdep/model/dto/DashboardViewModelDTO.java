package com.ctgraphdep.model.dto;

import com.ctgraphdep.model.dto.dashboard.DashboardCardDTO;
import com.ctgraphdep.model.dto.dashboard.DashboardMetricsDTO;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import java.util.List;

@Data
@Builder
public class DashboardViewModelDTO {
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
    private List<DashboardCardDTO> cards;

    @NonNull
    private DashboardMetricsDTO metrics;
}