package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WorkUsersSessionsStates {

    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("sessionStatus")
    private String sessionStatus;

    @JsonProperty("dayStartTime")
    private LocalDateTime dayStartTime;

    @JsonProperty("currentStartTime")
    private LocalDateTime currentStartTime;

    @JsonProperty("totalWorkedMinutes")
    private Integer totalWorkedMinutes;

    @JsonProperty("finalWorkedMinutes")
    private Integer finalWorkedMinutes;

    @JsonProperty("totalOvertimeMinutes")
    private Integer totalOvertimeMinutes;

    @JsonProperty("lunchBreakDeducted")
    private Boolean lunchBreakDeducted;

    @JsonProperty("workdayCompleted")
    private Boolean workdayCompleted;

    @JsonProperty("temporaryStopCount")
    private Integer temporaryStopCount;

    @JsonProperty("totalTemporaryStopMinutes")
    private Integer totalTemporaryStopMinutes;

    @JsonProperty("temporaryStops")
    private List<TemporaryStop> temporaryStops;

    @JsonProperty("lastTemporaryStopTime")
    private LocalDateTime lastTemporaryStopTime;

    @JsonProperty("lastActivity")
    private LocalDateTime lastActivity;

}