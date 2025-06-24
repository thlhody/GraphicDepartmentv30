package com.ctgraphdep.model.dto.worktime;

import com.ctgraphdep.model.WorkTimeSummary;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WorkTimeSummaryDTO {
    // ENHANCED: Individual entries for display
    private List<WorkTimeEntryDTO> entries;

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

    // Time metrics (enhanced to include SN overtime)
    private int totalRegularMinutes;
    private String formattedRegularHours;
    private int totalOvertimeMinutes; // Now includes SN overtime
    private String formattedOvertimeHours;
    private int totalMinutes;
    private String formattedTotalHours;
    private int discardedMinutes;

    // ENHANCED: Convert from domain model with added formatted values and entries
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
                .totalOvertimeMinutes(summary.getTotalOvertimeMinutes()) // Includes SN overtime
                .formattedOvertimeHours(CalculateWorkHoursUtil.minutesToHHmm(summary.getTotalOvertimeMinutes()))
                .totalMinutes(summary.getTotalMinutes())
                .formattedTotalHours(CalculateWorkHoursUtil.minutesToHHmm(summary.getTotalMinutes()))
                .discardedMinutes(summary.getDiscardedMinutes())
                .build();
    }

    // ENHANCED: Create with entries for time management page
    public static WorkTimeSummaryDTO createWithEntries(List<WorkTimeEntryDTO> entries, WorkTimeCountsDTO counts, Integer availablePaidDays) {
        int totalMinutes = counts.getRegularMinutes() + counts.getOvertimeMinutes();

        return WorkTimeSummaryDTO.builder()
                .entries(entries)
                .daysWorked(counts.getDaysWorked())
                .snDays(counts.getSnDays())
                .coDays(counts.getCoDays())
                .cmDays(counts.getCmDays())
                .totalTimeOffDays(counts.getTotalTimeOffDays())
                .totalRegularMinutes(counts.getRegularMinutes())
                .formattedRegularHours(CalculateWorkHoursUtil.minutesToHHmm(counts.getRegularMinutes()))
                .totalOvertimeMinutes(counts.getOvertimeMinutes()) // Includes SN overtime
                .formattedOvertimeHours(CalculateWorkHoursUtil.minutesToHHmm(counts.getOvertimeMinutes()))
                .totalMinutes(totalMinutes)
                .formattedTotalHours(CalculateWorkHoursUtil.minutesToHHmm(totalMinutes))
                .discardedMinutes(counts.getDiscardedMinutes())
                .availablePaidDays(availablePaidDays != null ? availablePaidDays : 0)
                .build();
    }

    // Utility methods
    public int getEntryCount() {
        return entries != null ? entries.size() : 0;
    }

    public long getSNWorkDayCount() {
        if (entries == null) return 0;
        return entries.stream().filter(WorkTimeEntryDTO::isSNWorkDay).count();
    }
}