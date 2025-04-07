package com.ctgraphdep.model.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter

public class TimeOffSummaryDTO {
    private int snDays;
    private int coDays;
    private int cmDays;
    private int availablePaidDays;
    private int paidDaysTaken;  // Total CO days taken
    private int remainingPaidDays; // Remaining CO days

}