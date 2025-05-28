package com.ctgraphdep.model;

import com.ctgraphdep.enums.SyncStatusMerge;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkTimeTable {

    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("workDate")
    private LocalDate workDate;

    @JsonProperty("dayStartTime")
    private LocalDateTime dayStartTime;

    @JsonProperty("dayEndTime")
    private LocalDateTime dayEndTime;

    @JsonProperty("temporaryStopCount")
    private Integer temporaryStopCount;

    @JsonProperty("lunchBreakDeducted")
    private boolean lunchBreakDeducted;

    @JsonProperty("timeOffType")
    private String timeOffType;

    @JsonProperty("totalWorkedMinutes")
    private Integer totalWorkedMinutes;

    @JsonProperty("totalTemporaryStopMinutes")
    private Integer totalTemporaryStopMinutes;

    @JsonProperty("totalOvertimeMinutes")
    private Integer totalOvertimeMinutes;

    @JsonProperty("adminSync")
    private SyncStatusMerge adminSync;


}