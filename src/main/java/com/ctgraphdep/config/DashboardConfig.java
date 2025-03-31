package com.ctgraphdep.config;

import com.ctgraphdep.model.dto.dashboard.DashboardCardDTO;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class DashboardConfig {
    @NonNull
    private String title;

    @NonNull
    private String description;

    @NonNull
    private String role;

    @Builder.Default
    private boolean refreshEnabled = true;

    @Builder.Default
    private int refreshInterval = 30000;

    @Builder.Default
    private List<DashboardCardDTO> cards = new ArrayList<>();

    public List<DashboardCardDTO> getCards() {
        return cards != null ? cards : new ArrayList<>();
    }
}