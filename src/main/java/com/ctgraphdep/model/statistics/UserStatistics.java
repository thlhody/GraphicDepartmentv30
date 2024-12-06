package com.ctgraphdep.model.statistics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserStatistics {
    private UserWorktimeStats worktime;
    private UserRegisterStats register;
}