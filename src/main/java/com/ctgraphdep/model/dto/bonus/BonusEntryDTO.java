package com.ctgraphdep.model.dto.bonus;

import com.ctgraphdep.model.BonusEntry;
import com.ctgraphdep.model.PreviousMonthsBonuses;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BonusEntryDTO {
    private String displayName; // User's full name for display
    private String username;
    private Integer employeeId;
    private Integer entries;
    private Double articleNumbers;
    private Double graphicComplexity;
    private Double misc;
    private Integer workedDays;
    private Double workedPercentage;
    private Double bonusPercentage;
    private Double bonusAmount;
    private PreviousMonthsBonuses previousMonths;
    private String calculationDate;

    public BonusEntryDTO(BonusEntry entry, String displayName) {
        this.displayName = displayName != null ? displayName : entry.getUsername();
        this.username = entry.getUsername();
        this.employeeId = entry.getEmployeeId();
        this.entries = entry.getEntries();
        this.articleNumbers = entry.getArticleNumbers();
        this.graphicComplexity = entry.getGraphicComplexity();
        this.misc = entry.getMisc();
        this.workedDays = entry.getWorkedDays();
        this.workedPercentage = entry.getWorkedPercentage();
        this.bonusPercentage = entry.getBonusPercentage();
        this.bonusAmount = entry.getBonusAmount();
        this.previousMonths = entry.getPreviousMonths();
        this.calculationDate = entry.getCalculationDate();
    }
}