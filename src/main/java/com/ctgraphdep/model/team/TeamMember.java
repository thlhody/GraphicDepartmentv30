package com.ctgraphdep.model.team;

import com.ctgraphdep.model.statistics.RegisterStatistics;
import com.ctgraphdep.model.statistics.UserWorktimeStats;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@Builder
public class TeamMember {
    private Integer userId;
    private String name;
    private String employeeId;
    private Integer schedule;
    private String username;
    private LocalDateTime lastActive;
    private String currentStatus;

    // Work statistics
    private UserWorktimeStats workStats;
    private RegisterStatistics registerStats;
}
