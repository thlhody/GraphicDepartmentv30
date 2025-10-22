package com.ctgraphdep.register.service;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.data.CheckBonusDataService;
import com.ctgraphdep.model.CheckBonusEntry;
import com.ctgraphdep.model.CheckValuesEntry;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UsersCheckValueEntry;
import com.ctgraphdep.service.BonusCalculationService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.DateFormatUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for calculating and managing check register bonuses for team leads.
 * Handles bonus calculation based on work units, working hours, and efficiency.
 */
@Service
public class CheckBonusService {

    @Autowired
    private CheckBonusDataService checkBonusDataService;

    @Autowired
    private PathConfig pathConfig;

    @Autowired
    private CheckRegisterService checkRegisterService;

    @Autowired
    private CheckValuesService checkValuesService;

    @Autowired
    private WorktimeOperationService worktimeOperationService;

    @Autowired
    private UserService userService;

    @Autowired
    private BonusCalculationService bonusCalculationService;

    /**
     * Calculate bonus for a single user
     *
     * @param username Username of the user
     * @param userId User ID
     * @param year Year for calculation
     * @param month Month for calculation
     * @param bonusSum Base bonus sum
     * @param standardHours Standard hours per month (for reference)
     * @return ServiceResult containing CheckBonusEntry or error
     */
    public ServiceResult<CheckBonusEntry> calculateUserBonus(String username, Integer userId,
                                                               int year, int month,
                                                               Double bonusSum, Double standardHours) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                "Calculating bonus for user: %s (ID: %d) for %d-%02d", username, userId, year, month));

            // 1. Get Total WU/M (sum of all check register entry orderValues)
            Double totalWUM = calculateTotalWUM(username, userId, year, month);
            if (totalWUM == null) {
                return ServiceResult.systemError("Failed to calculate Total WU/M for user", "calc_total_wum_failed");
            }

            // 2. Get Working Hours (schedule-based, adjusted for time off)
            Double workingHours = calculateWorkingHours(username, year, month);
            if (workingHours == null) {
                workingHours = 0.0; // Default to 0 if calculation fails
                LoggerUtil.warn(this.getClass(), "Working hours calculation failed, using 0.0");
            }

            // 3. Get Target WU/HR from CheckValues
            Double targetWUHR = getTargetWUHR(username, userId);
            if (targetWUHR == null) {
                targetWUHR = 4.5; // Default value
                LoggerUtil.warn(this.getClass(), "Target WU/HR not found, using default 4.5");
            }

            // Create bonus entry
            CheckBonusEntry bonusEntry = new CheckBonusEntry();
            bonusEntry.setUsername(username);
            bonusEntry.setEmployeeId(userId);
            bonusEntry.setName(getUserName(username, userId));
            bonusEntry.setTotalWUM(totalWUM);
            bonusEntry.setWorkingHours(workingHours);
            bonusEntry.setTargetWUHR(targetWUHR);
            bonusEntry.setYear(year);
            bonusEntry.setMonth(month);
            bonusEntry.setCalculationDate(DateFormatUtil.formatForDisplay(LocalDate.now()));

            // 4. Calculate Total WU/HR/M (Working Hours × Target WU/HR)
            bonusCalculationService.calculateTotalWUHRM(bonusEntry);

            // 5. Calculate Efficiency %
            bonusCalculationService.calculateEfficiency(bonusEntry);

            // 6. Calculate Bonus Amount
            bonusCalculationService.calculateBonus(bonusEntry, bonusSum);

            LoggerUtil.info(this.getClass(), String.format(
                "Bonus calculated for %s: WU/M=%.2f, Hours=%.2f, Efficiency=%d%%, Bonus=%.2f",
                username, totalWUM, workingHours, bonusEntry.getEfficiencyPercent(), bonusEntry.getBonusAmount()));

            return ServiceResult.success(bonusEntry);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating bonus for user: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to calculate bonus: " + e.getMessage(), "calc_bonus_failed");
        }
    }

    /**
     * Calculate Total WU/M (sum of all check register entry orderValues)
     */
    private Double calculateTotalWUM(String username, Integer userId, int year, int month) {
        try {
            // Load check register entries using loadTeamCheckRegister
            ServiceResult<List<RegisterCheckEntry>> entriesResult =
                checkRegisterService.loadTeamCheckRegister(username, userId, year, month);

            if (!entriesResult.isSuccess() || entriesResult.getData() == null) {
                LoggerUtil.warn(this.getClass(), "No check register entries found for " + username);
                return 0.0;
            }

            List<RegisterCheckEntry> entries = entriesResult.getData();

            // Sum all orderValue fields
            double totalWUM = entries.stream()
                .filter(entry -> entry.getOrderValue() != null)
                .mapToDouble(RegisterCheckEntry::getOrderValue)
                .sum();

            LoggerUtil.debug(this.getClass(), String.format(
                "Total WU/M for %s: %.2f from %d entries", username, totalWUM, entries.size()));

            return totalWUM;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating Total WU/M: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Calculate Working Hours (schedule-based, adjusted for time off)
     * For now, we'll use a simplified calculation based on worktime data
     */
    private Double calculateWorkingHours(String username, int year, int month) {
        try {
            // Get worked days from worktime service
            // For now, use a simple approximation: 20 working days per month * 8 hours
            // TODO: Implement proper worktime calculation with time off adjustments

            // Temporary: Assume 20 working days * 8 hours = 160 hours
            double workingHours = 160.0;

            LoggerUtil.debug(this.getClass(), String.format(
                "Working hours for %s: %.2f (using default 160 hours)", username, workingHours));

            return workingHours;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating working hours: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get Target WU/HR from CheckValues
     */
    private Double getTargetWUHR(String username, Integer userId) {
        try {
            // Load check values for user using the correct method
            UsersCheckValueEntry userCheckValues = checkValuesService.getUserCheckValues(username, userId);

            if (userCheckValues == null || userCheckValues.getCheckValuesEntry() == null) {
                LoggerUtil.warn(this.getClass(), "No check values found for " + username);
                return null;
            }

            CheckValuesEntry checkValues = userCheckValues.getCheckValuesEntry();
            Double targetWUHR = checkValues.getWorkUnitsPerHour();

            if (targetWUHR == null) {
                LoggerUtil.warn(this.getClass(), "Target WU/HR is null for " + username);
                return null;
            }

            return targetWUHR;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting Target WU/HR: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get user's display name
     */
    private String getUserName(String username, Integer userId) {
        try {
            // Try to get user's full name from user service
            var userOptional = userService.getUserById(userId);
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                if (user.getName() != null && !user.getName().isEmpty()) {
                    return user.getName();
                }
            }
            // Fallback to capitalized username
            return username.substring(0, 1).toUpperCase() + username.substring(1);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Could not retrieve user name for " + username);
            return username;
        }
    }

    /**
     * Save bonus data to file
     *
     * @param bonusEntry Bonus entry to save
     * @return ServiceResult indicating success or failure
     */
    public ServiceResult<Void> saveBonusData(CheckBonusEntry bonusEntry) {
        try {
            int year = bonusEntry.getYear();
            int month = bonusEntry.getMonth();

            LoggerUtil.info(this.getClass(), String.format(
                "Saving bonus data for %d-%02d", year, month));

            // Load existing bonus data
            List<CheckBonusEntry> bonusList = checkBonusDataService.readTeamLeadCheckBonusLocalReadOnly(year, month);

            // Remove existing entry for this user if present
            bonusList.removeIf(entry ->
                entry.getUsername().equals(bonusEntry.getUsername()) &&
                entry.getEmployeeId().equals(bonusEntry.getEmployeeId()));

            // Add new/updated entry
            bonusList.add(bonusEntry);

            // Save to file using data service
            checkBonusDataService.writeTeamLeadCheckBonusWithSyncAndBackup(bonusList, year, month);

            LoggerUtil.info(this.getClass(), String.format(
                "Successfully saved bonus data for %s", bonusEntry.getUsername()));

            return ServiceResult.success(null);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving bonus data: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to save bonus data: " + e.getMessage(), "save_bonus_failed");
        }
    }

    /**
     * Load bonus data from file
     *
     * @param year Year
     * @param month Month
     * @return ServiceResult containing list of CheckBonusEntry or error
     */
    public ServiceResult<List<CheckBonusEntry>> loadBonusData(int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                "Loading bonus data for %d-%02d", year, month));

            // Load using data service
            List<CheckBonusEntry> bonusList = checkBonusDataService.readTeamLeadCheckBonusLocalReadOnly(year, month);

            if (bonusList == null) {
                bonusList = new ArrayList<>();
            }

            LoggerUtil.info(this.getClass(), String.format(
                "Loaded %d bonus entries for %d-%02d", bonusList.size(), year, month));

            return ServiceResult.success(bonusList);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading bonus data: " + e.getMessage(), e);
            // Return empty list instead of error for graceful handling
            return ServiceResult.success(new ArrayList<>());
        }
    }

    // REMOVED: calculateAllBonuses() - Never used since creation
    // UI calculates bonuses one user at a time using calculateUserBonus()
    // Git history: Added in commit 328942e but never called from any controller
}