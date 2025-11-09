package com.ctgraphdep.register.service;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.bonus.BonusCalculationResultDTO;
import com.ctgraphdep.model.dto.RegisterSummaryDTO;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.service.result.ValidationServiceResult;
import com.ctgraphdep.utils.BonusCalculatorUtil;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FIXED AdminRegisterService with working bonus calculation and compilation fixes.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminRegisterService {

    private final BonusCalculatorUtil bonusCalculator;
    private final WorktimeOperationService worktimeOperationService;
    private final UserService userService;
    private final RegisterDataService registerDataService;
    private final RegisterMergeService registerMergeService;

    @Autowired
    public AdminRegisterService(BonusCalculatorUtil bonusCalculator, WorktimeOperationService worktimeOperationService, UserService userService,
                                RegisterDataService registerDataService, RegisterMergeService registerMergeService) {
        this.bonusCalculator = bonusCalculator;
        this.worktimeOperationService = worktimeOperationService;
        this.userService = userService;
        this.registerDataService = registerDataService;
        this.registerMergeService = registerMergeService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // FIXED: UNIFIED REQUEST HANDLING METHODS
    // ========================================================================

    /**
     * FIXED: Save entries from HTTP request (consolidates controller logic)
     */
    public ServiceResult<Void> saveEntriesFromRequest(Map<String, Object> request) {
        try {
            LoggerUtil.info(this.getClass(), "Processing admin register save request");

            // Parse and validate request
            ServiceResult<SaveRequestData> requestParseResult = parseAndValidateSaveRequest(request);
            if (requestParseResult.isFailure()) {
                return ServiceResult.validationError("Invalid request: " + requestParseResult.getErrorMessage(),
                        requestParseResult.getErrorCode());
            }

            SaveRequestData requestData = requestParseResult.getData();

            // Convert entries data to RegisterEntry objects using the GENERIC method (for saving)
            ServiceResult<List<RegisterEntry>> entriesConversionResult = convertRequestEntriesToRegisterEntries(requestData.entriesData());
            if (entriesConversionResult.isFailure()) {
                return ServiceResult.validationError("Invalid entries data: " + entriesConversionResult.getErrorMessage(),
                        entriesConversionResult.getErrorCode());
            }

            List<RegisterEntry> entries = entriesConversionResult.getData();

            LoggerUtil.info(this.getClass(), String.format(
                    "Saving %d entries for user %s (year: %d, month: %d)",
                    entries.size(), requestData.username(), requestData.year(), requestData.month()));

            // Save entries through existing method
            ServiceResult<Void> saveResult = saveAdminRegisterEntries(
                    requestData.username(), requestData.userId(), requestData.year(), requestData.month(), entries);

            if (saveResult.isSuccess()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully saved %d entries for user %s", entries.size(), requestData.username()));
            }

            return saveResult;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error processing save request: " + e.getMessage(), e);
            return ServiceResult.systemError("Unexpected error occurred while saving entries", "save_request_system_error");
        }
    }

    /**
     * FIXED: Calculate bonus from HTTP request - now uses BONUS-SPECIFIC conversion
     */
    public ServiceResult<BonusCalculationResultDTO> calculateBonusFromRequest(Map<String, Object> request) {
        try {
            LoggerUtil.info(this.getClass(), "Processing bonus calculation request");

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

            // Convert and validate entries using BONUS-SPECIFIC method
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entriesData = (List<Map<String, Object>>) request.get("entries");

            // FIXED: Use bonus-specific conversion that handles minimal data
            List<RegisterEntry> entries = convertToRegisterEntriesForBonus(entriesData);

            Integer userId = (Integer) request.get("userId");
            Integer year = (Integer) request.get("year");
            Integer month = (Integer) request.get("month");

            // Convert and validate bonus configuration
            @SuppressWarnings("unchecked")
            Map<String, Object> configValues = (Map<String, Object>) request.get("bonusConfig");

            ServiceResult<BonusConfiguration> configResult = convertRequestToBonusConfiguration(configValues);
            if (configResult.isFailure()) {
                return ServiceResult.validationError("Invalid bonus configuration: " + configResult.getErrorMessage(), "invalid_bonus_config");
            }

            BonusConfiguration config = configResult.getData();

            if (config.notValid()) {
                return ServiceResult.validationError("Bonus configuration is not valid", "invalid_bonus_config");
            }

            // Calculate bonus
            ServiceResult<BonusCalculationResultDTO> calculationResult = calculateBonus(entries, userId, year, month, config);
            if (calculationResult.isFailure()) {
                return calculationResult;
            }

            BonusCalculationResultDTO result = calculationResult.getData();

            // Get username for saving
            ServiceResult<User> userResult = getUserById(userId);
            if (userResult.isFailure()) {
                LoggerUtil.warn(this.getClass(), "Bonus calculated but cannot save - user not found: " + userResult.getErrorMessage());
                return ServiceResult.successWithWarnings(result, List.of("Bonus calculated but could not be saved: user not found"));
            }

            String username = userResult.getData().getUsername();

            // Save bonus result
            ServiceResult<Void> saveResult = saveBonusResult(userId, year, month, result, username);
            if (saveResult.isFailure()) {
                LoggerUtil.warn(this.getClass(), "Bonus calculated successfully but failed to save: " + saveResult.getErrorMessage());
                return ServiceResult.successWithWarnings(result, List.of("Bonus calculated but could not be saved: " + saveResult.getErrorMessage()));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully calculated and saved bonus for user %s: amount=%.2f", username, result.getBonusAmount()));

            return ServiceResult.success(result);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error in bonus calculation request: " + e.getMessage(), e);
            return ServiceResult.systemError("Unexpected error in bonus calculation", "bonus_calculation_system_error");
        }
    }

    // ========================================================================
    // FIXED: BONUS-SPECIFIC CONVERSION (NULL-SAFE)
    // ========================================================================

    /**
     * FIXED: Convert minimal bonus calculation data to RegisterEntry objects
     * This method handles the minimal data sent by the frontend for bonus calculation
     */
    private List<RegisterEntry> convertToRegisterEntriesForBonus(List<Map<String, Object>> entriesData) {
        if (entriesData == null) return new ArrayList<>();

        return entriesData.stream()
                .map(this::convertToRegisterEntryForBonus)
                .collect(Collectors.toList());
    }

    /**
     * FIXED: Convert individual bonus entry data to RegisterEntry (null-safe)
     * Only uses the fields needed for bonus calculation: articleNumbers, graphicComplexity, actionType
     */
    private RegisterEntry convertToRegisterEntryForBonus(Map<String, Object> data) {
        // Use safe defaults for all missing fields since we only need 3 fields for bonus calculation
        return RegisterEntry.builder()
                .entryId(0) // Not needed for bonus calculation
                .userId(0) // Will be set from request level
                .date(LocalDate.now()) // Not needed for bonus calculation
                .orderId("BONUS_CALC") // Not needed for bonus calculation
                .productionId("BONUS_CALC") // Not needed for bonus calculation
                .omsId("BONUS_CALC") // Not needed for bonus calculation
                .clientName("BONUS_CALC") // Not needed for bonus calculation
                .actionType(convertToString(data.get("actionType"))) // NEEDED for bonus calculation
                .printPrepTypes(List.of("DIGITAL")) // Not needed for bonus calculation
                .colorsProfile("BONUS_CALC") // Not needed for bonus calculation
                .articleNumbers(convertToInteger(data.get("articleNumbers"))) // NEEDED for bonus calculation
                .graphicComplexity(convertToDouble(data.get("graphicComplexity"))) // NEEDED for bonus calculation
                .observations("") // Not needed for bonus calculation
                .adminSync("BONUS_CALC") // Not needed for bonus calculation
                .build();
    }

    // ========================================================================
    // FIXED: DATA CONVERSION METHODS (MOVED FROM CONTROLLER)
    // ========================================================================

    /**
     * FIXED: Parse and validate save request data
     */
    private ServiceResult<SaveRequestData> parseAndValidateSaveRequest(Map<String, Object> request) {
        try {
            if (request == null) {
                return ServiceResult.validationError("Request cannot be null", "null_request");
            }

            // Validate required fields
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotNull(request.get("username"), "Username", "missing_username")
                    .requireNotNull(request.get("userId"), "User ID", "missing_user_id")
                    .requireNotNull(request.get("year"), "Year", "missing_year")
                    .requireNotNull(request.get("month"), "Month", "missing_month")
                    .requireNotNull(request.get("entries"), "Entries", "missing_entries");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            String username = request.get("username").toString().trim();
            if (username.isEmpty()) {
                return ServiceResult.validationError("Username cannot be empty", "empty_username");
            }

            Integer userId = convertToInteger(request.get("userId"));
            Integer year = convertToInteger(request.get("year"));
            Integer month = convertToInteger(request.get("month"));

            // Additional validation
            if (userId <= 0) {
                return ServiceResult.validationError("User ID must be positive", "invalid_user_id");
            }
            if (year < 2000 || year > 2100) {
                return ServiceResult.validationError("Year must be between 2000 and 2100", "invalid_year");
            }
            if (month < 1 || month > 12) {
                return ServiceResult.validationError("Month must be between 1 and 12", "invalid_month");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entriesData = (List<Map<String, Object>>) request.get("entries");

            SaveRequestData data = new SaveRequestData(username, userId, year, month, entriesData);
            return ServiceResult.success(data);

        } catch (NumberFormatException e) {
            return ServiceResult.validationError("Invalid number format in request", "invalid_number_format");
        } catch (ClassCastException e) {
            return ServiceResult.validationError("Invalid data format in request", "invalid_data_format");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error parsing save request: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to parse request data", "request_parse_failed");
        }
    }

    /**
     * FIXED: Convert request entries data to RegisterEntry objects (for SAVING - expects complete data)
     */
    private ServiceResult<List<RegisterEntry>> convertRequestEntriesToRegisterEntries(List<Map<String, Object>> entriesData) {
        try {
            if (entriesData == null) {
                return ServiceResult.success(new ArrayList<>());
            }

            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
            List<RegisterEntry> entries = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (int i = 0; i < entriesData.size(); i++) {
                try {
                    Map<String, Object> data = entriesData.get(i);

                    LoggerUtil.debug(this.getClass(), "Processing entry ID: " + data.get("entryId") +
                            ", printPrepTypes: " + data.get("printPrepTypes") +
                            ", adminSync: " + data.get("adminSync"));

                    // Handle printPrepTypes conversion with comprehensive logic
                    List<String> printPrepTypes = convertPrintPrepTypes(data.get("printPrepTypes"));

                    // IMPORTANT: Preserve the adminSync status from the client for now
                    // Status determination will happen later in saveAdminRegisterEntries() with change detection
                    String adminSync = data.get("adminSync") != null ?
                            data.get("adminSync").toString() : MergingStatusConstants.USER_INPUT;

                    RegisterEntry entry = RegisterEntry.builder()
                            .entryId(convertToInteger(data.get("entryId")))
                            .userId(convertToInteger(data.get("userId")))
                            .date(LocalDate.parse(data.get("date").toString(), formatter))
                            .orderId(data.get("orderId").toString())
                            .productionId(data.get("productionId").toString())
                            .omsId(data.get("omsId").toString())
                            .clientName(data.get("clientName").toString())
                            .actionType(data.get("actionType").toString())
                            .printPrepTypes(printPrepTypes)
                            .colorsProfile(data.get("colorsProfile").toString())
                            .articleNumbers(convertToInteger(data.get("articleNumbers")))
                            .graphicComplexity(convertToDouble(data.get("graphicComplexity")))
                            .observations(data.get("observations") != null ? data.get("observations").toString() : "")
                            .adminSync(adminSync) // Preserve for now, will be determined in save method
                            .build();

                    entries.add(entry);

                } catch (Exception e) {
                    warnings.add("Failed to convert entry at index " + i + ": " + e.getMessage());
                    LoggerUtil.warn(this.getClass(), String.format("Error converting entry at index %d: %s", i, e.getMessage()));
                }
            }

            if (entries.isEmpty() && !entriesData.isEmpty()) {
                return ServiceResult.validationError("No valid entries could be converted", "no_valid_entries");
            }

            if (!warnings.isEmpty()) {
                return ServiceResult.successWithWarnings(entries, warnings);
            }

            return ServiceResult.success(entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error converting entries data: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to convert entries data", "entries_conversion_failed");
        }
    }

    /**
     * ENHANCED: Convert print prep types with comprehensive logic
     */
    private List<String> convertPrintPrepTypes(Object printPrepTypesObj) {
        List<String> printPrepTypes = new ArrayList<>();

        if (printPrepTypesObj instanceof List<?> typesList) {
            // Handle list case
            typesList.forEach(type -> {
                if (type != null && !type.toString().equalsIgnoreCase("null") && !type.toString().trim().isEmpty()) {
                    printPrepTypes.add(type.toString().trim());
                }
            });
        } else if (printPrepTypesObj instanceof String typesStr && !typesStr.trim().isEmpty()) {
            // Handle string case
            Arrays.stream(typesStr.split("\\s*,\\s*"))
                    .filter(type -> !type.equalsIgnoreCase("null") && !type.trim().isEmpty())
                    .forEach(type -> printPrepTypes.add(type.trim()));
        }

        // If no valid types were found, add default
        if (printPrepTypes.isEmpty()) {
            printPrepTypes.add("DIGITAL");
        }

        return printPrepTypes;
    }

    /**
     * ENHANCED: Convert bonus configuration from request
     */
    private ServiceResult<BonusConfiguration> convertRequestToBonusConfiguration(Map<String, Object> configValues) {
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

    // ========================================================================
    // EXISTING METHODS (DELEGATED TO MERGE SERVICE)
    // ========================================================================

    /**
     * Get user by ID with proper error handling (centralized from controller)
     */
    public ServiceResult<User> getUserById(Integer userId) {
        try {
            if (userId == null) {
                return ServiceResult.validationError("User ID cannot be null", "null_user_id");
            }

            Optional<User> userOpt = userService.getUserById(userId);
            return userOpt.map(ServiceResult::success).orElseGet(() -> ServiceResult.notFound("User not found with ID: " + userId, "user_not_found"));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting user by ID %d: %s", userId, e.getMessage()), e);
            return ServiceResult.systemError("Failed to retrieve user information", "user_retrieval_failed");
        }
    }

    /**
     * Load user register entries using the new merge service.
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

            // Delegate to the merge service
            ServiceResult<List<RegisterEntry>> mergeResult = registerMergeService.performAdminLoadMerge(username, userId, year, month);

            if (mergeResult.isFailure()) {
                LoggerUtil.error(this.getClass(), String.format("Failed to load register entries for %s - %d/%d: %s", username, year, month, mergeResult.getErrorMessage()));
                return ServiceResult.businessError("Failed to load register entries: " + mergeResult.getErrorMessage(), "load_entries_failed");
            }

            List<RegisterEntry> mergedEntries = mergeResult.getData();

            LoggerUtil.info(this.getClass(), String.format("Successfully loaded %d register entries for %s - %d/%d", mergedEntries.size(), username, year, month));

            if (mergeResult.hasWarnings()) {
                return ServiceResult.successWithWarnings(mergedEntries, mergeResult.getWarnings());
            }

            return ServiceResult.success(mergedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error loading register entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error loading register entries", "load_entries_system_error");
        }
    }

    /**
     * Save admin register entries using the new merge service.
     * IMPORTANT: Compares with existing entries to determine which were actually edited
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

            LoggerUtil.info(this.getClass(), String.format("Saving %d entries for user %s - %d/%d with change detection", entries.size(), username, year, month));

            // Load existing admin entries to compare for changes
            List<RegisterEntry> existingEntries;
            try {
                existingEntries = registerDataService.readAdminLocalReadOnly(username, userId, year, month);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("No existing admin entries found for %s - %d/%d, treating all as new", username, year, month));
                existingEntries = new ArrayList<>();
            }

            // Create map of existing entries by entryId for quick lookup
            Map<Integer, RegisterEntry> existingEntriesMap = existingEntries.stream()
                    .collect(Collectors.toMap(RegisterEntry::getEntryId, entry -> entry));

            // Process entries: only mark as ADMIN_EDITED if actually changed
            List<RegisterEntry> processedEntries = new ArrayList<>();
            int editedCount = 0;
            int unchangedCount = 0;

            for (RegisterEntry entry : entries) {
                RegisterEntry existingEntry = existingEntriesMap.get(entry.getEntryId());

                if (existingEntry != null && hasEntryChanged(existingEntry, entry)) {
                    // Entry was changed - apply ADMIN_EDITED status logic
                    String currentStatus = entry.getAdminSync();
                    String newStatus = determineAdminSyncStatusForSave(currentStatus);
                    entry.setAdminSync(newStatus);
                    editedCount++;
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Entry %d changed - status: %s → %s", entry.getEntryId(), currentStatus, newStatus));
                } else if (existingEntry == null) {
                    // New entry - use ADMIN_INPUT
                    entry.setAdminSync(MergingStatusConstants.ADMIN_INPUT);
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Entry %d is new - status: ADMIN_INPUT", entry.getEntryId()));
                } else {
                    // Entry unchanged - preserve existing status
                    entry.setAdminSync(existingEntry.getAdminSync());
                    unchangedCount++;
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Entry %d unchanged - preserving status: %s", entry.getEntryId(), existingEntry.getAdminSync()));
                }

                processedEntries.add(entry);
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Change detection results: %d edited, %d unchanged, %d total",
                    editedCount, unchangedCount, processedEntries.size()));

            // Process entries using the merge service (for filtering, etc.)
            ServiceResult<List<RegisterEntry>> processResult = registerMergeService.performAdminSaveProcessing(processedEntries);
            if (processResult.isFailure()) {
                return ServiceResult.businessError("Failed to process entries: " + processResult.getErrorMessage(), "process_entries_failed");
            }

            List<RegisterEntry> finalEntries = processResult.getData();

            // Save processed entries to admin file
            try {
                registerDataService.writeAdminLocalWithSyncAndBackup(username, userId, finalEntries, year, month);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error saving processed entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
                return ServiceResult.systemError("Failed to save processed entries", "save_entries_failed");
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully saved %d processed entries for user %s - %d/%d", finalEntries.size(), username, year, month));

            if (processResult.hasWarnings()) {
                return ServiceResult.successWithWarnings(null, processResult.getWarnings());
            }

            return ServiceResult.success();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error saving admin register entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error saving admin register entries", "save_entries_system_error");
        }
    }

    // ========================================================================
    // BONUS METHODS
    // ========================================================================

    /**
     * Calculate bonus with validation and error handling
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
            int workedDays = worktimeOperationService.getWorkedDays(userId, year, month);

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
     * Calculate register summary with error handling
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

    /**
     * Get register summary from HTTP request
     */
    public ServiceResult<RegisterSummaryDTO> getRegisterSummaryForUser(String username, Integer userId, Integer year, Integer month) {
        try {
            if (username == null || username.trim().isEmpty()) {
                return ServiceResult.validationError("Username is required", "missing_username");
            }

            LoggerUtil.info(this.getClass(), String.format("Getting register summary for %s - %d/%d", username, year, month));

            // Load entries
            ServiceResult<List<RegisterEntry>> entriesResult = readMergedAdminEntries(username, userId, year, month);
            if (entriesResult.isFailure()) {
                LoggerUtil.error(this.getClass(), String.format("Failed to load entries for summary for %s: %s", username, entriesResult.getErrorMessage()));
                return ServiceResult.systemError("Failed to load entries for summary", "load_entries_for_summary_failed");
            }

            List<RegisterEntry> entries = entriesResult.getData();

            // Calculate summary
            ServiceResult<RegisterSummaryDTO> summaryResult = calculateRegisterSummary(entries);
            if (summaryResult.isSuccess()) {
                LoggerUtil.info(this.getClass(), String.format("Successfully calculated summary for %s", username));
            }

            return summaryResult;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error getting summary for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error calculating summary", "summary_calculation_system_error");
        }
    }

    /**
     * Reads already-merged admin register entries without triggering new merge operations.
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

            LoggerUtil.debug(this.getClass(), String.format("Successfully read %d merged admin entries for %s - %d/%d", entries.size(), username, year, month));

            return ServiceResult.success(entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading merged admin entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("Failed to read merged admin entries", "read_entries_failed");
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Check if an entry has been changed by comparing relevant fields.
     * Compares fields that admin can edit (ignoring adminSync status and userId).
     *
     * @param existingEntry Original entry from database
     * @param newEntry New entry from client
     * @return true if entry has changed, false otherwise
     */
    private boolean hasEntryChanged(RegisterEntry existingEntry, RegisterEntry newEntry) {
        if (existingEntry == null || newEntry == null) {
            return true; // Treat as changed if either is null
        }

        // Compare fields that admin can edit
        // Note: We deliberately exclude adminSync from comparison
        return !Objects.equals(existingEntry.getDate(), newEntry.getDate()) ||
                !Objects.equals(existingEntry.getOrderId(), newEntry.getOrderId()) ||
                !Objects.equals(existingEntry.getProductionId(), newEntry.getProductionId()) ||
                !Objects.equals(existingEntry.getOmsId(), newEntry.getOmsId()) ||
                !Objects.equals(existingEntry.getClientName(), newEntry.getClientName()) ||
                !Objects.equals(existingEntry.getActionType(), newEntry.getActionType()) ||
                !Objects.equals(existingEntry.getPrintPrepTypes(), newEntry.getPrintPrepTypes()) ||
                !Objects.equals(existingEntry.getColorsProfile(), newEntry.getColorsProfile()) ||
                !Objects.equals(existingEntry.getArticleNumbers(), newEntry.getArticleNumbers()) ||
                !Objects.equals(existingEntry.getGraphicComplexity(), newEntry.getGraphicComplexity()) ||
                !Objects.equals(existingEntry.getObservations(), newEntry.getObservations());
    }

    /**
     * Determine the adminSync status when admin saves an entry.
     * Rules:
     * - If status is ADMIN_FINAL or TEAM_FINAL: preserve it (immutable)
     * - If status is USER_IN_PROCESS: preserve it (user actively working)
     * - Otherwise: create ADMIN_EDITED_[timestamp] (admin is making changes)
     *
     * @param currentStatus Current adminSync status from the client
     * @return New adminSync status to use when saving
     */
    private String determineAdminSyncStatusForSave(String currentStatus) {
        // Preserve final statuses (immutable)
        if (MergingStatusConstants.ADMIN_FINAL.equals(currentStatus)) {
            LoggerUtil.debug(this.getClass(), "Preserving ADMIN_FINAL status (immutable)");
            return currentStatus;
        }

        if (MergingStatusConstants.TEAM_FINAL.equals(currentStatus)) {
            // Admin can override TEAM_FINAL, so create ADMIN_EDITED
            LoggerUtil.debug(this.getClass(), "Admin overriding TEAM_FINAL with ADMIN_EDITED");
            return MergingStatusConstants.createAdminEditedStatus();
        }

        // Preserve USER_IN_PROCESS (user actively working)
        if (MergingStatusConstants.USER_IN_PROCESS.equals(currentStatus)) {
            LoggerUtil.debug(this.getClass(), "Preserving USER_IN_PROCESS status (user actively working)");
            return currentStatus;
        }

        // For all other statuses (USER_INPUT, USER_EDITED_*, TEAM_EDITED_*, ADMIN_EDITED_*, etc.)
        // Admin is making changes, so create new ADMIN_EDITED timestamp
        LoggerUtil.debug(this.getClass(), String.format(
                "Admin editing entry with status %s, creating ADMIN_EDITED timestamp", currentStatus));
        return MergingStatusConstants.createAdminEditedStatus();
    }

    private List<RegisterEntry> filterValidEntriesForBonus(List<RegisterEntry> entries) {
        if (entries == null) return new ArrayList<>();

        List<String> bonusEligibleTypes = ActionType.getBonusEligibleValues();
        return entries.stream().filter(entry -> bonusEligibleTypes.contains(entry.getActionType())).collect(Collectors.toList());
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

            BonusEntry bonus1 = loadMonthBonusEntry(employeeId, month1);
            BonusEntry bonus2 = loadMonthBonusEntry(employeeId, month2);
            BonusEntry bonus3 = loadMonthBonusEntry(employeeId, month3);

            LoggerUtil.info(this.getClass(), String.format("Previous months bonuses for employee %d: %s, %s, %s",
                employeeId,
                bonus1 != null ? bonus1.getBonusAmount() : "null",
                bonus2 != null ? bonus2.getBonusAmount() : "null",
                bonus3 != null ? bonus3.getBonusAmount() : "null"));

            return PreviousMonthsBonuses.builder()
                    .month1(bonus1)
                    .month2(bonus2)
                    .month3(bonus3)
                    .build();

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error loading previous bonuses for user %d, returning nulls: %s", userId, e.getMessage()));
            return PreviousMonthsBonuses.builder()
                    .month1(null)
                    .month2(null)
                    .month3(null)
                    .build();
        }
    }

    private BonusEntry loadMonthBonusEntry(Integer employeeId, YearMonth month) {
        try {
            List<BonusEntry> entries = registerDataService.readAdminBonus(month.getYear(), month.getMonthValue());

            return entries.stream()
                    .filter(entry -> entry.getEmployeeId().equals(employeeId))
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            LoggerUtil.info(this.getClass(), String.format("No bonus entry found for employee %d in %s: %s", employeeId, month, e.getMessage()));
            return null;
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

    private String convertToString(Object value) {
        if (value == null) return "";
        return value.toString();
    }

    /**
     * Load saved bonus result with error handling
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
     * Load bonus entry with error handling
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
     * Force admin overwrite: Sets all register entries to ADMIN_FINAL status
     * This is a nuclear option for resolving any conflicts - admin decision becomes absolute
     * Use case: When there are synchronization issues or conflicts that need immediate resolution,
     * admin can force their current version to become the final version, overriding any user/team changes.
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return ServiceResult with count of entries marked as ADMIN_FINAL
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

            LoggerUtil.info(this.getClass(), String.format(
                    "Admin force overwrite initiated for %s - %d/%d (marking all entries as ADMIN_FINAL)",
                    username, year, month));

            // Read current admin entries
            List<RegisterEntry> currentEntries;
            try {
                currentEntries = registerDataService.readAdminLocalReadOnly(username, userId, year, month);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error reading admin entries for %s - %d/%d: %s",
                        username, year, month, e.getMessage()), e);
                return ServiceResult.systemError("Failed to read current admin entries", "read_entries_failed");
            }

            if (currentEntries == null || currentEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("No entries found to mark as ADMIN_FINAL for %s - %d/%d",
                        username, year, month));
                return ServiceResult.success(0);
            }

            // Mark all entries as ADMIN_FINAL (highest priority - cannot be overridden)
            int markedCount = 0;
            List<RegisterEntry> updatedEntries = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (RegisterEntry entry : currentEntries) {
                try {
                    // Create updated entry with ADMIN_FINAL status
                    RegisterEntry updatedEntry = RegisterEntry.builder()
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
                            .adminSync(MergingStatusConstants.ADMIN_FINAL) // Force to ADMIN_FINAL
                            .build();

                    updatedEntries.add(updatedEntry);
                    markedCount++;

                    LoggerUtil.debug(this.getClass(), String.format(
                            "Marked entry %d as ADMIN_FINAL (was: %s)", entry.getEntryId(), entry.getAdminSync()));
                } catch (Exception e) {
                    warnings.add("Failed to mark entry " + entry.getEntryId() + " as ADMIN_FINAL: " + e.getMessage());
                    updatedEntries.add(entry); // Keep original if marking fails
                    LoggerUtil.warn(this.getClass(), String.format("Error marking entry %d as ADMIN_FINAL: %s",
                            entry.getEntryId(), e.getMessage()));
                }
            }

            if (markedCount > 0) {
                // Save the updated entries
                try {
                    registerDataService.writeAdminLocalWithSyncAndBackup(username, userId, updatedEntries, year, month);
                    LoggerUtil.info(this.getClass(), String.format(
                            "Successfully marked %d entries as ADMIN_FINAL for %s - %d/%d (admin force overwrite complete)",
                            markedCount, username, year, month));
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format(
                            "Error saving entries marked as ADMIN_FINAL for %s - %d/%d: %s",
                            username, year, month, e.getMessage()), e);
                    return ServiceResult.systemError("Failed to save entries with ADMIN_FINAL status", "save_failed");
                }
            } else {
                LoggerUtil.info(this.getClass(), String.format(
                        "No entries were successfully marked as ADMIN_FINAL for %s - %d/%d",
                        username, year, month));
            }

            if (!warnings.isEmpty()) {
                return ServiceResult.successWithWarnings(markedCount, warnings);
            }

            return ServiceResult.success(markedCount);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Unexpected error in admin force overwrite for %s - %d/%d: %s",
                    username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error during admin force overwrite", "force_overwrite_system_error");
        }
    }

    // ========================================================================
    // FIXED: DATA RECORD FOR SAVE REQUEST PARSING
    // ========================================================================

    /**
     * FIXED: Data record for save request parsing
     */
    private record SaveRequestData(String username, Integer userId, Integer year, Integer month, List<Map<String, Object>> entriesData) {
    }
}