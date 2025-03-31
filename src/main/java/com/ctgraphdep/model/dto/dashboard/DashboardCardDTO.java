package com.ctgraphdep.model.dto.dashboard;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class DashboardCardDTO {
    @NonNull
    private String title;

    @NonNull
    private String subtitle;

    @Builder.Default
    private String color = "primary";

    @NonNull
    private String icon;

    private String badge;

    @Builder.Default
    private String badgeColor = "secondary";

    @NonNull
    private String actionText;

    @NonNull
    private String actionUrl;

    @Builder.Default
    private boolean external = false;

    @Builder.Default
    private String permission = "VIEW";
}