package com.ctgraphdep.service;

import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.bonus.BonusCalculationResultDTO;
import com.ctgraphdep.model.dto.RegisterSummaryDTO;
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
 * REFACTORED AdminRegisterService to use the new RegisterMergeService.
 * Key Changes:
 * - Removed old merge logic from loadUserRegisterEntries()
 * - Removed manual status processing from saveAdminRegisterEntries()
 * - Now delegates merge operations to RegisterMergeService
 * - Simplified and cleaner code focused on business logic
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminRegisterService {

    private final BonusCalculatorUtil bonusCalculator;
    private final WorktimeManagementService worktimeManagementService;
    private final UserService userService;
    private final TimeValidationService timeValidationService;
    private final RegisterDataService registerDataService;
    private final RegisterMergeService registerMergeService; // NEW: Added merge service dependency

    @Autowired
    public AdminRegisterService(BonusCalculatorUtil bonusCalculator,
                                WorktimeManagementService worktimeManagementService,
                                UserService userService,
                                TimeValidationService timeValidationService,
                                RegisterDataService registerDataService,
                                RegisterMergeService registerMergeService) { // NEW: Added parameter
        this.bonusCalculator = bonusCalculator;
        this.worktimeManagementService = worktimeManagementService;
        this.userService = userService;
        this.timeValidationService = timeValidationService;
        this.registerDataService = registerDataService;
        this.registerMergeService = registerMergeService; // NEW: Initialize dependency
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // REFACTORED METHODS - Now using RegisterMergeService
    // ========================================================================

    /**
     * REFACTORED: Load user register entries using the new merge service.
     * This method now simply delegates to RegisterMergeService.performAdminLoadMerge()
     * which handles all the complex merge logic, bootstrapping, and file operations.
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return Merged register entries ready for admin view
     */
    public List<RegisterEntry> loadUserRegisterEntries(String username, Integer userId, Integer year, Integer month) {
        LoggerUtil.info(this.getClass(), String.format(
                "Loading register entries for %s - %d/%d using RegisterMergeService", username, year, month));
        LoggerUtil.info(this.getClass(), String.format(
                "=== SERVICE ENTRY === Loading register entries for %s - %d/%d using RegisterMergeService, Thread: %s, Timestamp: %d",
                username, year, month, Thread.currentThread().getName(), System.currentTimeMillis()));

        try {
            // Delegate to the new merge service - handles everything:
            // 1. Load user entries from network
            // 2. Load admin entries from local
            // 3. Bootstrap or merge as needed
            // 4. Save result to admin local file
            // 5. Return merged entries for display
            List<RegisterEntry> mergedEntries = registerMergeService.performAdminLoadMerge(username, userId, year, month);

            LoggerUtil.info(this.getClass(), String.format(
                    "=== SERVICE EXIT === Successfully loaded %d register entries for %s - %d/%d, Thread: %s, Timestamp: %d",
                    mergedEntries.size(), username, year, month, Thread.currentThread().getName(), System.currentTimeMillis()));

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully loaded %d register entries for %s - %d/%d",
                    mergedEntries.size(), username, year, month));

            return sortEntriesForDisplay(mergedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading register entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            LoggerUtil.error(this.getClass(), String.format(
                    "=== SERVICE ERROR === Error loading register entries for %s - %d/%d: %s, Thread: %s",
                    username, year, month, e.getMessage(), Thread.currentThread().getName()), e);
            return new ArrayList<>();
        }
    }

    /**
     * Reads already-merged admin register entries without triggering new merge operations.
     * Use this for read-only operations like summaries after the initial load/merge has been completed.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return Already-merged register entries from admin file
     */
    public List<RegisterEntry> readMergedAdminEntries(String username, Integer userId, Integer year, Integer month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Reading merged admin entries for %s - %d/%d (read-only)", username, year, month));

            // Read directly from admin's local file (already merged by previous load operation)
            List<RegisterEntry> entries = registerDataService.readAdminLocalReadOnly(username, userId, year, month);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Successfully read %d merged admin entries for %s - %d/%d",
                    entries.size(), username, year, month));

            return sortEntriesForDisplay(entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading merged admin entries for %s - %d/%d: %s",
                    username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    /**
     * REFACTORED: Save admin register entries using the new merge service.
     * This method now delegates status processing to RegisterMergeService.performAdminSaveProcessing()
     * which handles all status transitions and cleanup operations.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @param entries Entries to save
     */
    public void saveAdminRegisterEntries(String username, Integer userId, Integer year, Integer month, List<RegisterEntry> entries) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Saving %d entries for user %s - %d/%d using RegisterMergeService",
                    entries.size(), username, year, month));

            // 1. Process entries using the new merge service - handles:
            //    - Status transitions (USER_INPUT → USER_DONE, etc.)
            //    - ADMIN_BLANK removal
            //    - Final status consolidation
            List<RegisterEntry> processedEntries = registerMergeService.performAdminSaveProcessing(entries);

            // 2. Save processed entries to admin file
            registerDataService.writeAdminLocalWithSyncAndBackup(username, userId, processedEntries, year, month);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully saved %d processed entries for user %s - %d/%d",
                    processedEntries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error saving admin register entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            throw new RuntimeException("Failed to save admin register entries", e);
        }
    }

    /**
     * Resolve all ADMIN_CHECK conflicts by changing them to ADMIN_EDITED
     * This ensures admin's decision takes precedence over user edits
     */
    public int confirmAllAdminChanges(String username, Integer userId, Integer year, Integer month) {
        try {
            // Read current admin entries
            List<RegisterEntry> currentEntries = registerDataService.readAdminLocalReadOnly(username, userId, year, month);

            if (currentEntries == null || currentEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("No entries found to confirm for %s - %d/%d", username, year, month));
                return 0;
            }

            // Count and resolve ADMIN_CHECK conflicts
            int resolvedCount = 0;
            List<RegisterEntry> updatedEntries = new ArrayList<>();

            for (RegisterEntry entry : currentEntries) {
                if (SyncStatusMerge.ADMIN_CHECK.name().equals(entry.getAdminSync())) {
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
                            .printPrepTypes(entry.getPrintPrepTypes() != null ?
                                    List.copyOf(entry.getPrintPrepTypes()) : null)
                            .colorsProfile(entry.getColorsProfile())
                            .articleNumbers(entry.getArticleNumbers())
                            .graphicComplexity(entry.getGraphicComplexity())
                            .observations(entry.getObservations())
                            .adminSync(SyncStatusMerge.ADMIN_EDITED.name()) // ADMIN_CHECK → ADMIN_EDITED (admin decision)
                            .build();

                    updatedEntries.add(resolvedEntry);
                    resolvedCount++;

                    LoggerUtil.info(this.getClass(), String.format(
                            "Resolved conflict for entry %d: ADMIN_CHECK → ADMIN_EDITED (admin decision)", entry.getEntryId()));
                } else {
                    // Keep other entries unchanged
                    updatedEntries.add(entry);
                }
            }

            if (resolvedCount > 0) {
                // Save the updated entries
                registerDataService.writeAdminLocalWithSyncAndBackup(username, userId, updatedEntries, year, month);

                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully resolved %d ADMIN_CHECK conflicts for %s - %d/%d",
                        resolvedCount, username, year, month));
            } else {
                LoggerUtil.info(this.getClass(), String.format(
                        "No ADMIN_CHECK conflicts found to resolve for %s - %d/%d", username, year, month));
            }

            return resolvedCount;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error resolving admin conflicts for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            throw new RuntimeException("Failed to resolve admin conflicts", e);
        }
    }

    // ========================================================================
    // UNCHANGED METHODS - Business logic remains the same
    // ========================================================================

    public Optional<BonusEntry> loadBonusEntry(Integer userId, Integer year, Integer month) {
        try {
            List<BonusEntry> bonusEntries = registerDataService.readAdminBonus(year, month);
            return bonusEntries.stream()
                    .filter(entry -> entry.getEmployeeId().equals(userId))
                    .findFirst();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading bonus entry for user %d: %s", userId, e.getMessage()));
            return Optional.empty();
        }
    }

    public BonusCalculationResultDTO calculateBonusFromRequest(Map<String, Object> request) {
        try {
            // Convert and validate entries
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entriesData = (List<Map<String, Object>>) request.get("entries");
            List<RegisterEntry> entries = convertToRegisterEntries(entriesData);

            Integer userId = (Integer) request.get("userId");
            Integer year = (Integer) request.get("year");
            Integer month = (Integer) request.get("month");

            // Convert and validate bonus configuration
            @SuppressWarnings("unchecked")
            Map<String, Object> configValues = (Map<String, Object>) request.get("bonusConfig");
            BonusConfiguration config = convertToBonusConfiguration(configValues);

            if (config.notValid()) {
                throw new IllegalArgumentException("Invalid bonus configuration");
            }

            // Call the original calculateBonus method
            return calculateBonus(entries, userId, year, month, config);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in bonus calculation: " + e.getMessage());
            throw new RuntimeException("Failed to calculate bonus", e);
        }
    }

    public BonusCalculationResultDTO calculateBonus(List<RegisterEntry> entries, Integer userId, Integer year, Integer month, BonusConfiguration config) {
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
        return BonusCalculationResultDTO.builder()
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
    }

    public void saveBonusResult(Integer userId, Integer year, Integer month, BonusCalculationResultDTO result, String username) {
        try {
            // Get user's employeeId
            Integer employeeId = userService.getUserById(userId).map(User::getEmployeeId).orElseThrow(() -> new RuntimeException("User not found: " + userId));

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
                existingEntries = new ArrayList<>();
            }

            // Find and replace or add the entry for this employee
            existingEntries.removeIf(entry -> entry.getEmployeeId().equals(employeeId));
            existingEntries.add(bonusEntry);

            // Save all entries
            registerDataService.writeAdminBonus(existingEntries, year, month);

            LoggerUtil.info(this.getClass(), String.format("Successfully saved bonus calculation for user %s (Employee ID: %d) for %d/%d", username, employeeId, year, month));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error processing bonus calculation for user %d: %s", userId, e.getMessage()));
            throw new RuntimeException("Failed to process bonus calculation", e);
        }
    }

    public BonusCalculationResultDTO loadSavedBonusResult(Integer userId, Integer year, Integer month) {
        try {
            // Get user's employeeId
            Integer employeeId = userService.getUserById(userId).map(User::getEmployeeId).orElseThrow(() -> new RuntimeException("User not found: " + userId));

            Optional<BonusEntry> bonusEntryOpt = loadBonusEntry(employeeId, year, month);

            if (bonusEntryOpt.isEmpty()) {
                return null;
            }

            BonusEntry entry = bonusEntryOpt.get();

            return BonusCalculationResultDTO.builder()
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
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading bonus result for user %d: %s", userId, e.getMessage()));
            throw new RuntimeException("Failed to load bonus result", e);
        }
    }

    public RegisterSummaryDTO calculateRegisterSummary(List<RegisterEntry> entries) {
        // Filter valid entries
        List<RegisterEntry> validEntries = filterValidEntriesForBonus(entries);

        return RegisterSummaryDTO.builder()
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
    }

    // ========================================================================
    // PRIVATE HELPER METHODS - Unchanged
    // ========================================================================

    private List<RegisterEntry> filterValidEntriesForBonus(List<RegisterEntry> entries) {
        List<String> bonusEligibleTypes = ActionType.getBonusEligibleValues();
        return entries.stream().filter(entry -> bonusEligibleTypes.contains(entry.getActionType())).collect(Collectors.toList());
    }

    private List<RegisterEntry> convertToRegisterEntries(List<Map<String, Object>> entriesData) {
        if (entriesData == null) return new ArrayList<>();
        return entriesData.stream().map(this::convertToRegisterEntry).collect(Collectors.toList());
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

    private BonusConfiguration convertToBonusConfiguration(Map<String, Object> configValues) {
        return BonusConfiguration.builder()
                .entriesPercentage(convertToDouble(configValues.get("entriesPercentage")))
                .articlesPercentage(convertToDouble(configValues.get("articlesPercentage")))
                .complexityPercentage(convertToDouble(configValues.get("complexityPercentage")))
                .miscPercentage(convertToDouble(configValues.get("miscPercentage")))
                .normValue(convertToDouble(configValues.get("normValue")))
                .sumValue(convertToDouble(configValues.get("sumValue")))
                .miscValue(convertToDouble(configValues.get("miscValue")))
                .build();
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
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        return timeValues.getCurrentDate();
    }
}