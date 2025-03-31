package com.ctgraphdep.model.dto.worktime;

import com.ctgraphdep.model.WorkTimeSummary;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkTimeSummaryDTO {
    // Work day metrics
    private int totalWorkDays;
    private int daysWorked;
    private int totalWorkedDaysWithTimeOff;
    private int remainingWorkDays;

    // Time off metrics
    private int availablePaidDays;
    private int snDays;
    private int coDays;
    private int cmDays;
    private int totalTimeOffDays;

    // Time metrics
    private int totalRegularMinutes;
    private String formattedRegularHours;
    private int totalOvertimeMinutes;
    private String formattedOvertimeHours;
    private int totalMinutes;
    private String formattedTotalHours;
    private int discardedMinutes;

    // Convert from domain model with added formatted values
    public static WorkTimeSummaryDTO fromWorkTimeSummary(WorkTimeSummary summary) {
        return WorkTimeSummaryDTO.builder()
                .totalWorkDays(summary.getTotalWorkDays())
                .daysWorked(summary.getDaysWorked())
                .totalWorkedDaysWithTimeOff(summary.getTotalWorkedDaysWithTimeOff())
                .remainingWorkDays(summary.getRemainingWorkDays())
                .availablePaidDays(summary.getAvailablePaidDays())
                .snDays(summary.getSnDays())
                .coDays(summary.getCoDays())
                .cmDays(summary.getCmDays())
                .totalTimeOffDays(summary.getTotalTimeOffDays())
                .totalRegularMinutes(summary.getTotalRegularMinutes())
                .formattedRegularHours(CalculateWorkHoursUtil.minutesToHHmm(summary.getTotalRegularMinutes()))
                .totalOvertimeMinutes(summary.getTotalOvertimeMinutes())
                .formattedOvertimeHours(CalculateWorkHoursUtil.minutesToHHmm(summary.getTotalOvertimeMinutes()))
                .totalMinutes(summary.getTotalMinutes())
                .formattedTotalHours(CalculateWorkHoursUtil.minutesToHHmm(summary.getTotalMinutes()))
                .discardedMinutes(summary.getDiscardedMinutes())
                .build();
    }
}