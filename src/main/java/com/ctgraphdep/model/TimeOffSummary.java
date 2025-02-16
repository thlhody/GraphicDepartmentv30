package com.ctgraphdep.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter

public class TimeOffSummary {
    private int snDays;
    private int coDays;
    private int cmDays;
    private int availablePaidDays;
    private int paidDaysTaken;  // Total CO days taken
    private int remainingPaidDays; // Remaing CO days

}