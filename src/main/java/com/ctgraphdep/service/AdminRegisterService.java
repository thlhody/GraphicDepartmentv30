// ========================================================================
// 2. REFACTORED AdminRegisterService.java
// ========================================================================

package com.ctgraphdep.service;

import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.bonus.BonusCalculationResultDTO;
import com.ctgraphdep.model.dto.RegisterSummaryDTO;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.service.result.ValidationServiceResult;
import com.ctgraphdep.utils.BonusCalculatorUtil;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REFACTORED AdminRegisterService with ServiceResult pattern.
 * Key Changes:
 * - All methods now return ServiceResult<T> instead of throwing exceptions
 * - Uses ValidationServiceResult for complex validation scenarios
 * - Proper error categorization and graceful error handling
 * - Still delegates merge operations to RegisterMergeService
 * - Preserved all existing business logic with better error management
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminRegisterService {

    private final BonusCalculatorUtil bonusCalculator;
    private final WorktimeManagementService worktimeManagementService;
    private final UserService userService;
    private final TimeValidationService timeValidationService;
    private final RegisterDataService registerDataService;
    private final RegisterMergeService registerMergeService;

    @Autowired
    public AdminRegisterService(BonusCalculatorUtil bonusCalculator, WorktimeManagementService worktimeManagementService, UserService userService,
                                TimeValidationService timeValidationService, RegisterDataService registerDataService, RegisterMergeService registerMergeService) {
        this.bonusCalculator = bonusCalculator;
        this.worktimeManagementService = worktimeManagementService;
        this.userService = userService;
        this.timeValidationService = timeValidationService;
        this.registerDataService = registerDataService;
        this.registerMergeService = registerMergeService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // REFACTORED METHODS - Now using ServiceResult with RegisterMergeService
    // ========================================================================

    /**
     * REFACTORED: Load user register entries using the new merge service.
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return ServiceResult with merged register entries or error details
     */
    public ServiceResult<List<RegisterEntry>> loadUserRegisterEntries(String username, Integer userId, Integer year, Integer month) {
        LoggerUtil.info(this.getClass(), String.format("Loading register entries for %s - %d/%d using RegisterMergeService", username, year, month));

        try {
            // Validate input parameters
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotEmpty(username, "Username", "missing_username")
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .requireNotNull(year, "Year", "missing_year")
                    .requireNotNull(month, "Month", "missing_month")
                    .validate(() -> userId > 0, "User ID must be positive", "invalid_user_id")
                    .validate(() -> year >= 2000 && year <= 2100, "Year must be between 2000 and 2100", "invalid_year")
                    .validate(() -> month >= 1 && month <= 12, "Month must be between 1 and 12", "invalid_month");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            // Delegate to the merge service - handles everything:
            ServiceResult<List<RegisterEntry>> mergeResult = registerMergeService.performAdminLoadMerge(username, userId, year, month);

            if (mergeResult.isFailure()) {
                LoggerUtil.error(this.getClass(), String.format("Failed to load register entries for %s - %d/%d: %s", username, year, month, mergeResult.getErrorMessage()));
                return ServiceResult.businessError("Failed to load register entries: " + mergeResult.getErrorMessage(), "load_entries_failed");
            }

            List<RegisterEntry> mergedEntries = mergeResult.getData();
            List<RegisterEntry> sortedEntries = sortEntriesForDisplay(mergedEntries);

            LoggerUtil.info(this.getClass(), String.format("Successfully loaded %d register entries for %s - %d/%d", sortedEntries.size(), username, year, month));

            if (mergeResult.hasWarnings()) {
                return ServiceResult.successWithWarnings(sortedEntries, mergeResult.getWarnings());
            }

            return ServiceResult.success(sortedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error loading register entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error loading register entries", "load_entries_system_error");
        }
    }

    /**
     * Reads already-merged admin register entries without triggering new merge operations.
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return ServiceResult with already-merged register entries
     */
    public ServiceResult<List<RegisterEntry>> readMergedAdminEntries(String username, Integer userId, Integer year, Integer month) {
        try {
            // Validate input parameters
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotEmpty(username, "Username", "missing_username")
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .requireNotNull(year, "Year", "missing_year")
                    .requireNotNull(month, "Month", "missing_month");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            LoggerUtil.debug(this.getClass(), String.format("Reading merged admin entries for %s - %d/%d (read-only)", username, year, month));

            // Read directly from admin's local file (already merged by previous load operation)
            List<RegisterEntry> entries = registerDataService.readAdminLocalReadOnly(username, userId, year, month);
            List<RegisterEntry> sortedEntries = sortEntriesForDisplay(entries);

            LoggerUtil.debug(this.getClass(), String.format("Successfully read %d merged admin entries for %s - %d/%d", sortedEntries.size(), username, year, month));

            return ServiceResult.success(sortedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading merged admin entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("Failed to read merged admin entries", "read_entries_failed");
        }
    }

    /**
     * REFACTORED: Save admin register entries using the new merge service.
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @param entries Entries to save
     * @return ServiceResult indicating success or failure
     */
    public ServiceResult<Void> saveAdminRegisterEntries(String username, Integer userId, Integer year, Integer month, List<RegisterEntry> entries) {
        try {
            // Validate input parameters
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotEmpty(username, "Username", "missing_username")
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .requireNotNull(year, "Year", "missing_year")
                    .requireNotNull(month, "Month", "missing_month")
                    .requireNotNull(entries, "Entries", "missing_entries");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            LoggerUtil.info(this.getClass(), String.format("Saving %d entries for user %s - %d/%d using RegisterMergeService", entries.size(), username, year, month));

            // Process entries using the merge service
            ServiceResult<List<RegisterEntry>> processResult = registerMergeService.performAdminSaveProcessing(entries);
            if (processResult.isFailure()) {
                return ServiceResult.businessError("Failed to process entries: " + processResult.getErrorMessage(), "process_entries_failed");
            }

            List<RegisterEntry> processedEntries = processResult.getData();

            // Save processed entries to admin file
            try {
                registerDataService.writeAdminLocalWithSyncAndBackup(username, userId, processedEntries, year, month);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error saving processed entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
                return ServiceResult.systemError("Failed to save processed entries", "save_entries_failed");
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully saved %d processed entries for user %s - %d/%d", processedEntries.size(), username, year, month));

            if (processResult.hasWarnings()) {
                return ServiceResult.successWithWarnings(null, processResult.getWarnings());
            }

            return ServiceResult.success();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error saving admin register entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error saving admin register entries", "save_entries_system_error");
        }
    }

    /**
     * Resolve all ADMIN_CHECK conflicts by changing them to ADMIN_EDITED
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return ServiceResult with count of resolved conflicts
     */
    public ServiceResult<Integer> confirmAllAdminChanges(String username, Integer userId, Integer year, Integer month) {
        try {
            // Validate input parameters
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotEmpty(username, "Username", "missing_username")
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .requireNotNull(year, "Year", "missing_year")
                    .requireNotNull(month, "Month", "missing_month");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            // Read current admin entries
            List<RegisterEntry> currentEntries;
            try {
                currentEntries = registerDataService.readAdminLocalReadOnly(username, userId, year, month);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error reading admin entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
                return ServiceResult.systemError("Failed to read current admin entries", "read_entries_failed");
            }

            if (currentEntries == null || currentEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("No entries found to confirm for %s - %d/%d", username, year, month));
                return ServiceResult.success(0);
            }

            // Count and resolve ADMIN_CHECK conflicts
            int resolvedCount = 0;
            List<RegisterEntry> updatedEntries = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (RegisterEntry entry : currentEntries) {
                if (SyncStatusMerge.ADMIN_CHECK.name().equals(entry.getAdminSync())) {
                    try {
                        // Resolve conflict: ADMIN_CHECK → ADMIN_EDITED (admin's final decision)
                        RegisterEntry resolvedEntry = RegisterEntry.builder()
                                .entryId(entry.getEntryId())
                                .userId(entry.getUserId())
                                .date(entry.getDate())
                                .orderId(entry.getOrderId())
                                .productionId(entry.getProductionId())
                                .omsId(entry.getOmsId())
                                .clientName(entry.getClientName())
                                .actionType(entry.getActionType())
                                .printPrepTypes(entry.getPrintPrepTypes() != null ? List.copyOf(entry.getPrintPrepTypes()) : null)
                                .colorsProfile(entry.getColorsProfile())
                                .articleNumbers(entry.getArticleNumbers())
                                .graphicComplexity(entry.getGraphicComplexity())
                                .observations(entry.getObservations())
                                .adminSync(SyncStatusMerge.ADMIN_EDITED.name()) // ADMIN_CHECK → ADMIN_EDITED
                                .build();

                        updatedEntries.add(resolvedEntry);
                        resolvedCount++;

                        LoggerUtil.info(this.getClass(), String.format("Resolved conflict for entry %d: ADMIN_CHECK → ADMIN_EDITED (admin decision)", entry.getEntryId()));
                    } catch (Exception e) {
                        warnings.add("Failed to resolve conflict for entry " + entry.getEntryId() + ": " + e.getMessage());
                        updatedEntries.add(entry); // Keep original if resolution fails
                        LoggerUtil.warn(this.getClass(), String.format("Error resolving conflict for entry %d: %s", entry.getEntryId(), e.getMessage()));
                    }
                } else {
                    // Keep other entries unchanged
                    updatedEntries.add(entry);
                }
            }

            if (resolvedCount > 0) {
                // Save the updated entries
                try {
                    registerDataService.writeAdminLocalWithSyncAndBackup(username, userId, updatedEntries, year, month);
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format("Error saving resolved conflicts for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
                    return ServiceResult.systemError("Failed to save resolved conflicts", "save_resolved_failed");
                }

                LoggerUtil.info(this.getClass(), String.format("Successfully resolved %d ADMIN_CHECK conflicts for %s - %d/%d", resolvedCount, username, year, month));
            } else {
                LoggerUtil.info(this.getClass(), String.format("No ADMIN_CHECK conflicts found to resolve for %s - %d/%d", username, year, month));
            }

            if (!warnings.isEmpty()) {
                return ServiceResult.successWithWarnings(resolvedCount, warnings);
            }

            return ServiceResult.success(resolvedCount);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error resolving admin conflicts for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error resolving admin conflicts", "resolve_conflicts_system_error");
        }
    }

    // ========================================================================
    // REFACTORED BONUS METHODS - Now using ServiceResult
    // ========================================================================

    /**
     * Load bonus entry with error handling
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return ServiceResult with bonus entry or empty if not found
     */
    public ServiceResult<Optional<BonusEntry>> loadBonusEntry(Integer userId, Integer year, Integer month) {
        try {
            // Validate parameters
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .requireNotNull(year, "Year", "missing_year")
                    .requireNotNull(month, "Month", "missing_month")
                    .validate(() -> userId > 0, "User ID must be positive", "invalid_user_id");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            List<BonusEntry> bonusEntries = registerDataService.readAdminBonus(year, month);
            Optional<BonusEntry> result = bonusEntries.stream()
                    .filter(entry -> entry.getEmployeeId().equals(userId))
                    .findFirst();

            return ServiceResult.success(result);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading bonus entry for user %d: %s", userId, e.getMessage()), e);
            return ServiceResult.systemError("Failed to load bonus entry", "load_bonus_failed");
        }
    }

    /**
     * Calculate bonus from request with validation
     * @param request Request map containing bonus calculation parameters
     * @return ServiceResult with bonus calculation result
     */
    public ServiceResult<BonusCalculationResultDTO> calculateBonusFromRequest(Map<String, Object> request) {
        try {
            // Validate request structure
            if (request == null) {
                return ServiceResult.validationError("Request cannot be null", "null_request");
            }

            // Validate required fields
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotNull(request.get("entries"), "Entries", "missing_entries")
                    .requireNotNull(request.get("userId"), "User ID", "missing_user_id")
                    .requireNotNull(request.get("year"), "Year", "missing_year")
                    .requireNotNull(request.get("month"), "Month", "missing_month")
                    .requireNotNull(request.get("bonusConfig"), "Bonus configuration", "missing_bonus_config");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            // Convert and validate entries
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entriesData = (List<Map<String, Object>>) request.get("entries");

            ServiceResult<List<RegisterEntry>> entriesResult = convertToRegisterEntries(entriesData);
            if (entriesResult.isFailure()) {
                return ServiceResult.validationError("Invalid entries data: " + entriesResult.getErrorMessage(), "invalid_entries");
            }

            List<RegisterEntry> entries = entriesResult.getData();
            Integer userId = (Integer) request.get("userId");
            Integer year = (Integer) request.get("year");
            Integer month = (Integer) request.get("month");

            // Convert and validate bonus configuration
            @SuppressWarnings("unchecked")
            Map<String, Object> configValues = (Map<String, Object>) request.get("bonusConfig");

            ServiceResult<BonusConfiguration> configResult = convertToBonusConfiguration(configValues);
            if (configResult.isFailure()) {
                return ServiceResult.validationError("Invalid bonus configuration: " + configResult.getErrorMessage(), "invalid_bonus_config");
            }

            BonusConfiguration config = configResult.getData();

            if (config.notValid()) {
                return ServiceResult.validationError("Bonus configuration is not valid", "invalid_bonus_config");
            }

            // Call the original calculateBonus method
            return calculateBonus(entries, userId, year, month, config);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error in bonus calculation: " + e.getMessage(), e);
            return ServiceResult.systemError("Unexpected error in bonus calculation", "bonus_calculation_system_error");
        }
    }

    /**
     * Calculate bonus with validation and error handling
     * @param entries Register entries
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @param config Bonus configuration
     * @return ServiceResult with bonus calculation result
     */
    public ServiceResult<BonusCalculationResultDTO> calculateBonus(List<RegisterEntry> entries, Integer userId, Integer year, Integer month, BonusConfiguration config) {
        try {
            // Validate parameters
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotNull(entries, "Entries", "missing_entries")
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .requireNotNull(year, "Year", "missing_year")
                    .requireNotNull(month, "Month", "missing_month")
                    .requireNotNull(config, "Bonus configuration", "missing_bonus_config");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            // Filter valid entries for bonus calculation
            List<RegisterEntry> validEntries = filterValidEntriesForBonus(entries);

            // Get worked days from worktime service
            int workedDays = worktimeManagementService.getWorkedDays(userId, year, month);

            // Calculate sums
            int numberOfEntries = validEntries.size();
            double sumArticleNumbers = validEntries.stream().mapToDouble(RegisterEntry::getArticleNumbers).sum();
            double sumComplexity = validEntries.stream().mapToDouble(RegisterEntry::getGraphicComplexity).sum();

            // Load previous months' data
            PreviousMonthsBonuses previousMonths = loadPreviousMonthsBonuses(userId, year, month);

            // Calculate bonus
            BonusCalculationResultDTO result = bonusCalculator.calculateBonus(numberOfEntries, workedDays, sumArticleNumbers, sumComplexity, config);

            // Create a new result with previous months included
            BonusCalculationResultDTO finalResult = BonusCalculationResultDTO.builder()
                    .entries(result.getEntries())
                    .articleNumbers(result.getArticleNumbers())
                    .graphicComplexity(result.getGraphicComplexity())
                    .misc(result.getMisc())
                    .workedDays(result.getWorkedDays())
                    .workedPercentage(result.getWorkedPercentage())
                    .bonusPercentage(result.getBonusPercentage())
                    .bonusAmount(result.getBonusAmount())
                    .previousMonths(previousMonths)
                    .build();

            LoggerUtil.info(this.getClass(), String.format("Successfully calculated bonus for user %d: entries=%d, amount=%.2f", userId, numberOfEntries, finalResult.getBonusAmount()));

            return ServiceResult.success(finalResult);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error calculating bonus for user %d: %s", userId, e.getMessage()), e);
            return ServiceResult.systemError("Failed to calculate bonus", "bonus_calculation_failed");
        }
    }

    /**
     * Save bonus result with validation
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @param result Bonus calculation result
     * @param username Username
     * @return ServiceResult indicating success or failure
     */
    public ServiceResult<Void> saveBonusResult(Integer userId, Integer year, Integer month, BonusCalculationResultDTO result, String username) {
        try {
            // Validate parameters
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .requireNotNull(year, "Year", "missing_year")
                    .requireNotNull(month, "Month", "missing_month")
                    .requireNotNull(result, "Bonus result", "missing_bonus_result")
                    .requireNotEmpty(username, "Username", "missing_username");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            // Get user's employeeId
            Integer employeeId;
            try {
                employeeId = userService.getUserById(userId).map(User::getEmployeeId).orElseThrow(() -> new RuntimeException("User not found: " + userId));
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error getting employee ID for user %d: %s", userId, e.getMessage()), e);
                return ServiceResult.notFound("User not found: " + userId, "user_not_found");
            }

            // Create bonus entry from calculation result
            BonusEntry bonusEntry = BonusEntry.fromBonusCalculationResult(username, employeeId, result);
            LoggerUtil.info(this.getClass(), String.format("Created bonus entry for employee %d with amount %f", employeeId, bonusEntry.getBonusAmount()));

            // Load and set previous months' bonuses
            PreviousMonthsBonuses previousMonths = loadPreviousMonthsBonuses(employeeId, year, month);
            bonusEntry.setPreviousMonths(previousMonths);

            // Read existing bonus entries
            List<BonusEntry> existingEntries;
            try {
                existingEntries = registerDataService.readAdminBonus(year, month);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("No existing bonus entries found for %d/%d, creating new list", year, month));
                existingEntries = new ArrayList<>();
            }

            // Find and replace or add the entry for this employee
            existingEntries.removeIf(entry -> entry.getEmployeeId().equals(employeeId));
            existingEntries.add(bonusEntry);

            // Save all entries
            try {
                registerDataService.writeAdminBonus(existingEntries, year, month);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error saving bonus entries for %d/%d: %s", year, month, e.getMessage()), e);
                return ServiceResult.systemError("Failed to save bonus entries", "save_bonus_failed");
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully saved bonus calculation for user %s (Employee ID: %d) for %d/%d", username, employeeId, year, month));
            return ServiceResult.success();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error saving bonus calculation for user %d: %s", userId, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error saving bonus calculation", "save_bonus_system_error");
        }
    }

    /**
     * Load saved bonus result with error handling
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return ServiceResult with bonus calculation result or null if not found
     */
    public ServiceResult<BonusCalculationResultDTO> loadSavedBonusResult(Integer userId, Integer year, Integer month) {
        try {
            // Validate parameters
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .requireNotNull(year, "Year", "missing_year")
                    .requireNotNull(month, "Month", "missing_month");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            // Get user's employeeId
            Integer employeeId;
            try {
                employeeId = userService.getUserById(userId).map(User::getEmployeeId).orElseThrow(() -> new RuntimeException("User not found: " + userId));
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error getting employee ID for user %d: %s", userId, e.getMessage()), e);
                return ServiceResult.notFound("User not found: " + userId, "user_not_found");
            }

            ServiceResult<Optional<BonusEntry>> bonusEntryResult = loadBonusEntry(employeeId, year, month);
            if (bonusEntryResult.isFailure()) {
                return ServiceResult.systemError("Failed to load bonus entry: " + bonusEntryResult.getErrorMessage(), "load_bonus_entry_failed");
            }

            Optional<BonusEntry> bonusEntryOpt = bonusEntryResult.getData();
            if (bonusEntryOpt.isEmpty()) {
                return ServiceResult.success(null);
            }

            BonusEntry entry = bonusEntryOpt.get();

            BonusCalculationResultDTO result = BonusCalculationResultDTO.builder()
                    .entries(entry.getEntries())
                    .articleNumbers(entry.getArticleNumbers())
                    .graphicComplexity(entry.getGraphicComplexity())
                    .misc(entry.getMisc())
                    .workedDays(entry.getWorkedDays())
                    .workedPercentage(entry.getWorkedPercentage())
                    .bonusPercentage(entry.getBonusPercentage())
                    .bonusAmount(entry.getBonusAmount())
                    .previousMonths(entry.getPreviousMonths())
                    .build();

            return ServiceResult.success(result);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error loading bonus result for user %d: %s", userId, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error loading bonus result", "load_bonus_result_system_error");
        }
    }

    /**
     * Calculate register summary with error handling
     * @param entries Register entries
     * @return ServiceResult with register summary
     */
    public ServiceResult<RegisterSummaryDTO> calculateRegisterSummary(List<RegisterEntry> entries) {
        try {
            if (entries == null) {
                return ServiceResult.validationError("Entries cannot be null", "null_entries");
            }

            // Filter valid entries
            List<RegisterEntry> validEntries = filterValidEntriesForBonus(entries);

            RegisterSummaryDTO summary = RegisterSummaryDTO.builder()
                    .totalEntries(validEntries.size())
                    .averageArticleNumbers(validEntries.stream()
                            .mapToDouble(RegisterEntry::getArticleNumbers)
                            .average()
                            .orElse(0.0))
                    .averageGraphicComplexity(validEntries.stream()
                            .mapToDouble(RegisterEntry::getGraphicComplexity)
                            .average()
                            .orElse(0.0))
                    .build();

            return ServiceResult.success(summary);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating register summary: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to calculate register summary", "calculate_summary_failed");
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS - ENHANCED WITH ERROR HANDLING
    // ========================================================================

    private List<RegisterEntry> filterValidEntriesForBonus(List<RegisterEntry> entries) {
        if (entries == null) return new ArrayList<>();

        List<String> bonusEligibleTypes = ActionType.getBonusEligibleValues();
        return entries.stream().filter(entry -> bonusEligibleTypes.contains(entry.getActionType())).collect(Collectors.toList());
    }

    private ServiceResult<List<RegisterEntry>> convertToRegisterEntries(List<Map<String, Object>> entriesData) {
        try {
            if (entriesData == null) {
                return ServiceResult.success(new ArrayList<>());
            }

            List<RegisterEntry> entries = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (int i = 0; i < entriesData.size(); i++) {
                try {
                    RegisterEntry entry = convertToRegisterEntry(entriesData.get(i));
                    entries.add(entry);
                } catch (Exception e) {
                    warnings.add("Failed to convert entry at index " + i + ": " + e.getMessage());
                    LoggerUtil.warn(this.getClass(), String.format("Error converting entry at index %d: %s", i, e.getMessage()));
                }
            }

            if (!warnings.isEmpty()) {
                return ServiceResult.successWithWarnings(entries, warnings);
            }

            return ServiceResult.success(entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error converting entries data: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to convert entries data", "convert_entries_failed");
        }
    }

    @SuppressWarnings("unchecked")
    private RegisterEntry convertToRegisterEntry(Map<String, Object> data) {
        // Get printPrepTypes as a list from the data
        List<String> printPrepTypes = new ArrayList<>();
        if (data.get("printPrepTypes") instanceof List) {
            printPrepTypes = new ArrayList<>(new LinkedHashSet<>((List<String>) data.get("printPrepTypes")));
        } else if (data.get("printPrepTypes") instanceof String) {
            printPrepTypes = new ArrayList<>(new LinkedHashSet<>(Arrays.asList(((String) data.get("printPrepTypes")).split("\\s*,\\s*"))));
        }

        return RegisterEntry.builder()
                .entryId(convertToInteger(data.get("entryId")))
                .userId(convertToInteger(data.get("userId")))
                .date(parseLocalDate(data.get("date")))
                .orderId(String.valueOf(data.get("orderId")))
                .productionId(String.valueOf(data.get("productionId")))
                .omsId(String.valueOf(data.get("omsId")))
                .clientName(String.valueOf(data.get("clientName")))
                .actionType(String.valueOf(data.get("actionType")))
                .printPrepTypes(printPrepTypes)
                .colorsProfile(String.valueOf(data.get("colorsProfile")))
                .articleNumbers(convertToInteger(data.get("articleNumbers")))
                .graphicComplexity(convertToDouble(data.get("graphicComplexity")))
                .observations(String.valueOf(data.get("observations")))
                .adminSync(String.valueOf(data.get("adminSync")))
                .build();
    }

    private ServiceResult<BonusConfiguration> convertToBonusConfiguration(Map<String, Object> configValues) {
        try {
            if (configValues == null) {
                return ServiceResult.validationError("Bonus configuration cannot be null", "null_bonus_config");
            }

            BonusConfiguration config = BonusConfiguration.builder()
                    .entriesPercentage(convertToDouble(configValues.get("entriesPercentage")))
                    .articlesPercentage(convertToDouble(configValues.get("articlesPercentage")))
                    .complexityPercentage(convertToDouble(configValues.get("complexityPercentage")))
                    .miscPercentage(convertToDouble(configValues.get("miscPercentage")))
                    .normValue(convertToDouble(configValues.get("normValue")))
                    .sumValue(convertToDouble(configValues.get("sumValue")))
                    .miscValue(convertToDouble(configValues.get("miscValue")))
                    .build();

            return ServiceResult.success(config);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error converting bonus configuration: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to convert bonus configuration", "convert_bonus_config_failed");
        }
    }

    private PreviousMonthsBonuses loadPreviousMonthsBonuses(Integer userId, Integer year, Integer month) {
        try {
            // Get employee ID first
            Integer employeeId = userService.getUserById(userId).map(User::getEmployeeId).orElse(userId); // Fallback to userId if employeeId not found

            YearMonth currentMonth = YearMonth.of(year, month);
            LoggerUtil.info(this.getClass(), String.format("Loading previous months bonuses for employee %d, current month: %s", employeeId, currentMonth));

            // Calculate previous months
            YearMonth month1 = currentMonth.minusMonths(1);
            YearMonth month2 = currentMonth.minusMonths(2);
            YearMonth month3 = currentMonth.minusMonths(3);

            Double bonus1 = loadMonthBonus(employeeId, month1);
            Double bonus2 = loadMonthBonus(employeeId, month2);
            Double bonus3 = loadMonthBonus(employeeId, month3);

            LoggerUtil.info(this.getClass(), String.format("Previous months bonuses for employee %d: %f, %f, %f", employeeId, bonus1, bonus2, bonus3));

            return PreviousMonthsBonuses.builder()
                    .month1(bonus1)
                    .month2(bonus2)
                    .month3(bonus3)
                    .build();

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error loading previous bonuses for user %d, returning zeros: %s", userId, e.getMessage()));
            return PreviousMonthsBonuses.builder()
                    .month1(0.0)
                    .month2(0.0)
                    .month3(0.0)
                    .build();
        }
    }

    private Double loadMonthBonus(Integer employeeId, YearMonth month) {
        try {
            List<BonusEntry> entries = registerDataService.readAdminBonus(month.getYear(), month.getMonthValue());

            return entries.stream()
                    .filter(entry -> entry.getEmployeeId().equals(employeeId))
                    .map(BonusEntry::getBonusAmount)
                    .findFirst()
                    .orElse(0.0);

        } catch (Exception e) {
            LoggerUtil.info(this.getClass(), String.format("No bonus entry found for employee %d in %s: %s", employeeId, month, e.getMessage()));
            return 0.0;
        }
    }

    private Integer convertToInteger(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Double convertToDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private LocalDate parseLocalDate(Object value) {
        if (value == null) return getStandardCurrentDate();
        if (value instanceof LocalDate) return (LocalDate) value;
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception e) {
            return getStandardCurrentDate();
        }
    }

    private List<RegisterEntry> sortEntriesForDisplay(List<RegisterEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return entries;
        }

        return entries.stream()
                .sorted((e1, e2) -> {
                    // First sort by date (newest first)
                    int dateCompare = e2.getDate().compareTo(e1.getDate());
                    if (dateCompare != 0) {
                        return dateCompare;
                    }

                    // Then by ID (highest first)
                    if (e1.getEntryId() == null && e2.getEntryId() == null) return 0;
                    if (e1.getEntryId() == null) return 1;
                    if (e2.getEntryId() == null) return -1;

                    return e2.getEntryId().compareTo(e1.getEntryId());
                })
                .collect(Collectors.toList());
    }

    // Gets the standard current date from the time validation service
    private LocalDate getStandardCurrentDate() {
        try {
            GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
            return timeValues.getCurrentDate();
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error getting standard time, using system time: " + e.getMessage());
            return LocalDate.now();
        }
    }
}