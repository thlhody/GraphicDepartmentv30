package com.ctgraphdep.model;

import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class WorkTimeEntryDTO {
    private Integer userId;
    private LocalDate workDate;
    private String formattedDate;

    private LocalDateTime dayStartTime;
    private String formattedStartTime;

    private LocalDateTime dayEndTime;
    private String formattedEndTime;

    private Integer temporaryStopCount;
    private Integer totalTemporaryStopMinutes;
    private String formattedTemporaryStop;

    private boolean lunchBreakDeducted;
    private boolean lunchBreakApplied;

    private String timeOffType;
    private String timeOffClass;

    private Integer rawMinutes;
    private String formattedRawTime;

    private Integer scheduledMinutes;
    private String formattedScheduledTime;

    private Integer overtimeMinutes;
    private String formattedOvertimeTime;

    private Integer discardedMinutes;

    // Helper method to convert a WorkTimeTable to DTO with calculated values
    public static WorkTimeEntryDTO fromWorkTimeTable(WorkTimeTable entry, int userSchedule) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        WorkTimeEntryDTOBuilder builder = WorkTimeEntryDTO.builder()
                .userId(entry.getUserId())
                .workDate(entry.getWorkDate())
                .formattedDate(entry.getWorkDate().format(dateFormatter))
                .dayStartTime(entry.getDayStartTime())
                .dayEndTime(entry.getDayEndTime())
                .temporaryStopCount(entry.getTemporaryStopCount())
                .totalTemporaryStopMinutes(entry.getTotalTemporaryStopMinutes())
                .lunchBreakDeducted(entry.isLunchBreakDeducted())
                .timeOffType(entry.getTimeOffType());

        // Format start and end times
        if (entry.getDayStartTime() != null) {
            builder.formattedStartTime(entry.getDayStartTime().format(timeFormatter));
        }

        if (entry.getDayEndTime() != null) {
            builder.formattedEndTime(entry.getDayEndTime().format(timeFormatter));
        }

        // Format temporary stop if available
        if (entry.getTemporaryStopCount() != null && entry.getTemporaryStopCount() > 0) {
            builder.formattedTemporaryStop(entry.getTemporaryStopCount() + " (" + entry.getTotalTemporaryStopMinutes() + "m)");
        }

        // Set time off CSS class
        if (entry.getTimeOffType() != null) {
            String cssClass = switch (entry.getTimeOffType()) {
                case "CO" -> "bg-info";
                case "CM" -> "bg-danger";
                default -> "bg-secondary";
            };
            builder.timeOffClass(cssClass);
        }

        // Calculate work times if this is a work entry (not time off)
        if (entry.getTimeOffType() == null && entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            int rawMinutes = entry.getTotalWorkedMinutes();
            builder.rawMinutes(rawMinutes);
            builder.formattedRawTime(CalculateWorkHoursUtil.minutesToHHmm(rawMinutes));

            // Use the utility to calculate everything
            WorkTimeCalculationResult result = CalculateWorkHoursUtil.calculateWorkTime(
                    rawMinutes,
                    userSchedule
            );

            // Set lunch break applied flag using utility class logic
            builder.lunchBreakApplied(result.isLunchDeducted());

            // Set scheduled minutes from processed minutes
            builder.scheduledMinutes(result.getProcessedMinutes());
            builder.formattedScheduledTime(CalculateWorkHoursUtil.minutesToHHmm(result.getProcessedMinutes()));

            // Set overtime minutes
            builder.overtimeMinutes(result.getOvertimeMinutes());
            if (result.getOvertimeMinutes() > 0) {
                builder.formattedOvertimeTime(CalculateWorkHoursUtil.minutesToHHmm(result.getOvertimeMinutes()));
            }

            // Calculate discarded minutes - get remainder of adjusted minutes
            int adjustedMinutes = CalculateWorkHoursUtil.calculateAdjustedMinutes(rawMinutes, userSchedule);
            builder.discardedMinutes(adjustedMinutes % 60);
        }

        return builder.build();
    }
}