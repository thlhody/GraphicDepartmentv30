package com.ctgraphdep.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
@Getter
@Setter
public class BonusCalculationResult {
    private final int entries;
    private final double articleNumbers;
    private final double graphicComplexity;
    private final double misc;
    private final int workedDays;
    private final double workedPercentage;
    private final double bonusPercentage;
    private final double bonusAmount;
    private final PreviousMonthsBonuses previousMonths;
}
