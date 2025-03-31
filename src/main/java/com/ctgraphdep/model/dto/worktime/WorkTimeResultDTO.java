package com.ctgraphdep.model.dto.worktime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkTimeResultDTO {

    private final int totalRegularMinutes;
    private final int totalOvertimeMinutes;

    public WorkTimeResultDTO(int totalRegularMinutes, int totalOvertimeMinutes) {
        this.totalRegularMinutes = totalRegularMinutes;
        this.totalOvertimeMinutes = totalOvertimeMinutes;
    }
}
