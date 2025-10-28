package com.ctgraphdep.register.service;

import com.ctgraphdep.model.CheckBonusEntry;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

/**
 * Service for bonus calculation logic.
 * Extracted from CheckBonusEntry model to separate business logic from data.
 * Responsibilities:
 * - Calculate efficiency percentage from work metrics
 * - Calculate bonus amounts from efficiency
 * - Calculate total Work Units per Hour per Month (WUHRM)
 * - Provide combined calculation methods
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
     * Formula: (totalWUM / totalWUHRM) × 100
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

            LoggerUtil.debug(this.getClass(), String.format("Calculated efficiency for %s: %.2f%% (rounded to %d%%)", entry.getUsername(), efficiency, roundedEfficiency));
        } else {
            entry.setEfficiencyPercent(0);

            if (totalWUHRM == null || totalWUHRM <= 0) {
                LoggerUtil.debug(this.getClass(), String.format("Efficiency set to 0 for %s: totalWUHRM is %s", entry.getUsername(), totalWUHRM));
            }
        }
    }

    /**
     * Calculate bonus amount from bonusSum and efficiency percentage
     * Formula: bonusSum × (efficiencyPercent / 100)
     * @param entry The bonus entry to calculate bonus for
     * @param bonusSum The total bonus sum to distribute based on efficiency
     */
    public void calculateBonus(CheckBonusEntry entry, Double bonusSum) {
        if (entry == null) {
            LoggerUtil.warn(this.getClass(), "Cannot calculate bonus: entry is null");
            return;
        }

        if (bonusSum == null) {
            LoggerUtil.warn(this.getClass(), String.format("Cannot calculate bonus for %s: bonusSum is null", entry.getUsername()));
            entry.setBonusAmount(0.0);
            return;
        }

        Integer efficiencyPercent = entry.getEfficiencyPercent();

        if (efficiencyPercent != null) {
            double bonus = bonusSum * (efficiencyPercent / 100.0);
            entry.setBonusAmount(bonus);

            LoggerUtil.debug(this.getClass(), String.format("Calculated bonus for %s: %.2f (bonusSum=%.2f, efficiency=%d%%)",
                entry.getUsername(), bonus, bonusSum, efficiencyPercent));
        } else {
            entry.setBonusAmount(0.0);
            LoggerUtil.debug(this.getClass(), String.format("Bonus set to 0 for %s: efficiencyPercent is null", entry.getUsername()));
        }
    }

    /**
     * Calculate total Work Units per Hour per Month (WUHRM)
     * Formula: workingHours × targetWUHR
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

            LoggerUtil.debug(this.getClass(), String.format("Calculated totalWUHRM for %s: %.2f (workingHours=%.2f, targetWUHR=%.2f)",
                entry.getUsername(), totalWUHRM, workingHours, targetWUHR));
        } else {
            entry.setTotalWUHRM(0.0);

            if (workingHours == null) {
                LoggerUtil.debug(this.getClass(), String.format("totalWUHRM set to 0 for %s: workingHours is null", entry.getUsername()));
            } else {
                LoggerUtil.debug(this.getClass(), String.format("totalWUHRM set to 0 for %s: targetWUHR is null", entry.getUsername()));
            }
        }
    }
}
