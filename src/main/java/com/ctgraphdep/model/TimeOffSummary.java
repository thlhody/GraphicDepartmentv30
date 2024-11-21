package com.ctgraphdep.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeOffSummary {
    private int snDays;
    private int coDays;
    private int cmDays;
    private int availablePaidDays;

    // Additional fields that might be useful for the view
    private int totalRequestedDays;
    private int totalApprovedDays;
    private int remainingPaidDays;
}