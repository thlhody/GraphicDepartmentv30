package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Model class for check register bonus calculation entries.
 * Matches the format of admin bonus JSON structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckBonusEntry {

    /**
     * Username of the employee
     */
    @JsonProperty("username")
    private String username;

    /**
     * Employee ID
     */
    @JsonProperty("employeeId")
    private Integer employeeId;

    /**
     * Employee full name (for display)
     */
    @JsonProperty("name")
    private String name;

    /**
     * Total Work Units per Month (sum of all check register entry values)
     */
    @JsonProperty("totalWUM")
    private Double totalWUM;

    /**
     * Working hours for the month (schedule-based, adjusted for time off)
     */
    @JsonProperty("workingHours")
    private Double workingHours;

    /**
     * Target Work Units per Hour (from CheckValues)
     */
    @JsonProperty("targetWUHR")
    private Double targetWUHR;

    /**
     * Total Work Units per Hour per Month (calculated: workingHours × targetWUHR)
     */
    @JsonProperty("totalWUHRM")
    private Double totalWUHRM;

    /**
     * Efficiency percentage (calculated: totalWUM / totalWUHRM × 100)
     */
    @JsonProperty("efficiencyPercent")
    private Integer efficiencyPercent;

    /**
     * Calculated bonus amount (bonusSum × efficiencyPercent / 100)
     */
    @JsonProperty("bonusAmount")
    private Double bonusAmount;

    /**
     * Date when bonus was calculated
     */
    @JsonProperty("calculationDate")
    @JsonFormat(pattern = "dd/MM/yyyy")
    private String calculationDate;

    /**
     * Year of the bonus calculation
     */
    @JsonProperty("year")
    private Integer year;

    /**
     * Month of the bonus calculation
     */
    @JsonProperty("month")
    private Integer month;

    /**
     * Default constructor with null-safe initialization
     */
    public CheckBonusEntry(String username, Integer employeeId, String name) {
        this.username = username;
        this.employeeId = employeeId;
        this.name = name;
        this.totalWUM = 0.0;
        this.workingHours = 0.0;
        this.targetWUHR = 0.0;
        this.totalWUHRM = 0.0;
        this.efficiencyPercent = 0;
        this.bonusAmount = 0.0;
        this.calculationDate = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /**
     * Calculate efficiency percentage from totalWUM and totalWUHRM
     */
    public void calculateEfficiency() {
        if (totalWUHRM != null && totalWUHRM > 0 && totalWUM != null) {
            double efficiency = (totalWUM / totalWUHRM) * 100;
            this.efficiencyPercent = (int) Math.round(efficiency);
        } else {
            this.efficiencyPercent = 0;
        }
    }

    /**
     * Calculate bonus amount from bonusSum and efficiency
     */
    public void calculateBonus(Double bonusSum) {
        if (bonusSum != null && efficiencyPercent != null) {
            this.bonusAmount = bonusSum * (efficiencyPercent / 100.0);
        } else {
            this.bonusAmount = 0.0;
        }
    }

    /**
     * Calculate totalWUHRM from workingHours and targetWUHR
     */
    public void calculateTotalWUHRM() {
        if (workingHours != null && targetWUHR != null) {
            this.totalWUHRM = workingHours * targetWUHR;
        } else {
            this.totalWUHRM = 0.0;
        }
    }

    /**
     * Null-safe getter for totalWUM
     */
    public Double getTotalWUM() {
        return totalWUM != null ? totalWUM : 0.0;
    }

    /**
     * Null-safe getter for workingHours
     */
    public Double getWorkingHours() {
        return workingHours != null ? workingHours : 0.0;
    }

    /**
     * Null-safe getter for targetWUHR
     */
    public Double getTargetWUHR() {
        return targetWUHR != null ? targetWUHR : 0.0;
    }

    /**
     * Null-safe getter for totalWUHRM
     */
    public Double getTotalWUHRM() {
        return totalWUHRM != null ? totalWUHRM : 0.0;
    }

    /**
     * Null-safe getter for efficiencyPercent
     */
    public Integer getEfficiencyPercent() {
        return efficiencyPercent != null ? efficiencyPercent : 0;
    }

    /**
     * Null-safe getter for bonusAmount
     */
    public Double getBonusAmount() {
        return bonusAmount != null ? bonusAmount : 0.0;
    }
}
