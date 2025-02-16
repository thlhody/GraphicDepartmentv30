package com.ctgraphdep.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class SessionCalculationResult {
    private final int scheduleDuration;
    private final LocalDateTime calculatedEndTime;
    private final WorkTimeCalculationResult workTimeResult;
    private final LocalDateTime originalStartTime;
    private final int originalTempStopCount;
    private final int originalTempStopMinutes;
    private final List<TemporaryStop> originalTempStops;
    private final LocalDateTime originalLastTempStopTime;
}