package com.ctgraphdep.model.dto.worktime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WorkTimeCalculationResultDTO {

    private int rawMinutes;
    private int processedMinutes;
    private int overtimeMinutes;
    private boolean lunchDeducted;
    private int finalTotalMinutes;

}
