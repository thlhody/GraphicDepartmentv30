package com.ctgraphdep.register.service;

import com.ctgraphdep.model.CheckBonusEntry;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

/**
 * Service for bonus calculation logic.
 * Extracted from CheckBonusEntry model to separate business logic from data.
 *
 * Responsibilities:
 * - Calculate efficiency percentage from work metrics
 * - Calculate bonus amounts from efficiency
 * - Calculate total Work Units per Hour per Month (WUHRM)
 * - Provide combined calculation methods
 *
 * All calculations previously embedded in CheckBonusEntry model class have been moved here.
 */
@Service
public class BonusCalculationService {

    public BonusCalculationService() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // PUBLIC CALCULATION METHODS
    // ========================================================================

    /**
     * Calculate efficiency percentage from totalWUM and totalWUHRM
     *
     * Formula: (totalWUM / totalWUHRM) × 100
     *
     * @param entry The bonus entry to calculate efficiency for
     */
    public void calculateEfficiency(CheckBonusEntry entry) {
        if (entry == null) {
            LoggerUtil.warn(this.getClass(), "Cannot calculate efficiency: entry is null");
            return;
        }

        Double totalWUHRM = entry.getTotalWUHRM();
        Double totalWUM = entry.getTotalWUM();

        if (totalWUHRM != null && totalWUHRM > 0 && totalWUM != null) {
            double efficiency = (totalWUM / totalWUHRM) * 100;
            int roundedEfficiency = (int) Math.round(efficiency);
            entry.setEfficiencyPercent(roundedEfficiency);

            LoggerUtil.debug(this.getClass(), String.format(
                "Calculated efficiency for %s: %.2f%% (rounded to %d%%)",
                entry.getUsername(), efficiency, roundedEfficiency));
        } else {
            entry.setEfficiencyPercent(0);

            if (totalWUHRM == null || totalWUHRM <= 0) {
                LoggerUtil.debug(this.getClass(), String.format(
                    "Efficiency set to 0 for %s: totalWUHRM is %s",
                    entry.getUsername(), totalWUHRM));
            }
        }
    }

    /**
     * Calculate bonus amount from bonusSum and efficiency percentage
     *
     * Formula: bonusSum × (efficiencyPercent / 100)
     *
     * @param entry The bonus entry to calculate bonus for
     * @param bonusSum The total bonus sum to distribute based on efficiency
     */
    public void calculateBonus(CheckBonusEntry entry, Double bonusSum) {
        if (entry == null) {
            LoggerUtil.warn(this.getClass(), "Cannot calculate bonus: entry is null");
            return;
        }

        if (bonusSum == null) {
            LoggerUtil.warn(this.getClass(), String.format(
                "Cannot calculate bonus for %s: bonusSum is null", entry.getUsername()));
            entry.setBonusAmount(0.0);
            return;
        }

        Integer efficiencyPercent = entry.getEfficiencyPercent();

        if (efficiencyPercent != null) {
            double bonus = bonusSum * (efficiencyPercent / 100.0);
            entry.setBonusAmount(bonus);

            LoggerUtil.debug(this.getClass(), String.format(
                "Calculated bonus for %s: %.2f (bonusSum=%.2f, efficiency=%d%%)",
                entry.getUsername(), bonus, bonusSum, efficiencyPercent));
        } else {
            entry.setBonusAmount(0.0);
            LoggerUtil.debug(this.getClass(), String.format(
                "Bonus set to 0 for %s: efficiencyPercent is null", entry.getUsername()));
        }
    }

    /**
     * Calculate total Work Units per Hour per Month (WUHRM)
     *
     * Formula: workingHours × targetWUHR
     *
     * @param entry The bonus entry to calculate WUHRM for
     */
    public void calculateTotalWUHRM(CheckBonusEntry entry) {
        if (entry == null) {
            LoggerUtil.warn(this.getClass(), "Cannot calculate totalWUHRM: entry is null");
            return;
        }

        Double workingHours = entry.getWorkingHours();
        Double targetWUHR = entry.getTargetWUHR();

        if (workingHours != null && targetWUHR != null) {
            double totalWUHRM = workingHours * targetWUHR;
            entry.setTotalWUHRM(totalWUHRM);

            LoggerUtil.debug(this.getClass(), String.format(
                "Calculated totalWUHRM for %s: %.2f (workingHours=%.2f, targetWUHR=%.2f)",
                entry.getUsername(), totalWUHRM, workingHours, targetWUHR));
        } else {
            entry.setTotalWUHRM(0.0);

            if (workingHours == null) {
                LoggerUtil.debug(this.getClass(), String.format(
                    "totalWUHRM set to 0 for %s: workingHours is null", entry.getUsername()));
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                    "totalWUHRM set to 0 for %s: targetWUHR is null", entry.getUsername()));
            }
        }
    }

    // ========================================================================
    // CONVENIENCE METHODS
    // ========================================================================

    /**
     * Perform all calculations in the correct order
     *
     * Order:
     * 1. Calculate totalWUHRM (workingHours × targetWUHR)
     * 2. Calculate efficiency (totalWUM / totalWUHRM × 100)
     * 3. Calculate bonus (bonusSum × efficiency / 100)
     *
     * @param entry The bonus entry to calculate all values for
     * @param bonusSum The total bonus sum for bonus calculation
     */
    public void calculateAll(CheckBonusEntry entry, Double bonusSum) {
        if (entry == null) {
            LoggerUtil.warn(this.getClass(), "Cannot calculate all: entry is null");
            return;
        }

        LoggerUtil.debug(this.getClass(), String.format(
            "Starting full bonus calculation for %s with bonusSum=%.2f",
            entry.getUsername(), bonusSum != null ? bonusSum : 0.0));

        // Step 1: Calculate total WUHRM (needed for efficiency)
        calculateTotalWUHRM(entry);

        // Step 2: Calculate efficiency (depends on totalWUHRM)
        calculateEfficiency(entry);

        // Step 3: Calculate bonus (depends on efficiency)
        calculateBonus(entry, bonusSum);

        LoggerUtil.info(this.getClass(), String.format(
            "Completed bonus calculation for %s: totalWUHRM=%.2f, efficiency=%d%%, bonus=%.2f",
            entry.getUsername(),
            entry.getTotalWUHRM(),
            entry.getEfficiencyPercent(),
            entry.getBonusAmount()));
    }

    /**
     * Validate that all required fields are present for calculations
     *
     * @param entry The bonus entry to validate
     * @return true if all required fields are present and valid
     */
    public boolean validateForCalculation(CheckBonusEntry entry) {
        if (entry == null) {
            LoggerUtil.warn(this.getClass(), "Validation failed: entry is null");
            return false;
        }

        boolean valid = true;
        StringBuilder issues = new StringBuilder();

        if (entry.getTotalWUM() == null) {
            issues.append("totalWUM is null; ");
            valid = false;
        }

        if (entry.getWorkingHours() == null) {
            issues.append("workingHours is null; ");
            valid = false;
        }

        if (entry.getTargetWUHR() == null) {
            issues.append("targetWUHR is null; ");
            valid = false;
        }

        if (!valid) {
            LoggerUtil.warn(this.getClass(), String.format(
                "Validation failed for %s: %s", entry.getUsername(), issues.toString()));
        }

        return valid;
    }

    // ========================================================================
    // BATCH CALCULATION METHODS
    // ========================================================================

    /**
     * Calculate all bonuses for a list of entries
     *
     * @param entries List of bonus entries
     * @param bonusSum Total bonus sum to distribute
     */
    public void calculateAllBonuses(Iterable<CheckBonusEntry> entries, Double bonusSum) {
        if (entries == null) {
            LoggerUtil.warn(this.getClass(), "Cannot calculate bonuses: entries list is null");
            return;
        }

        int count = 0;
        for (CheckBonusEntry entry : entries) {
            calculateAll(entry, bonusSum);
            count++;
        }

        LoggerUtil.info(this.getClass(), String.format(
            "Calculated bonuses for %d entries with bonusSum=%.2f", count, bonusSum != null ? bonusSum : 0.0));
    }
}
