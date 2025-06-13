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
 * REFACTORED AdminRegisterService with consolidated request handling.
 * Key Changes:
 * - All data conversion logic moved from controller to service
 * - New unified request handling methods (saveEntriesFromRequest, etc.)
 * - Centralized user operations
 * - Controller becomes thin HTTP layer, service handles all business logic
 * - Eliminated code duplication between controller and service
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
    // NEW: UNIFIED REQUEST HANDLING METHODS
    // ========================================================================

    /**
     * NEW: Save entries from HTTP request (consolidates controller logic)
     * @param request Raw HTTP request data
     * @return ServiceResult indicating success or failure
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

            // Convert entries data to RegisterEntry objects
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
     * ENHANCED: Calculate bonus from HTTP request with automatic saving
     * @param request Raw HTTP request data
     * @return ServiceResult with bonus calculation result
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

            // Convert and validate entries
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entriesData = (List<Map<String, Object>>) request.get("entries");

            ServiceResult<List<RegisterEntry>> entriesResult = convertRequestEntriesToRegisterEntries(entriesData);
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
                // Return calculation result even if save will fail
                return ServiceResult.successWithWarnings(result, List.of("Bonus calculated but could not be saved: user not found"));
            }

            String username = userResult.getData().getUsername();

            // Save bonus result
            ServiceResult<Void> saveResult = saveBonusResult(userId, year, month, result, username);
            if (saveResult.isFailure()) {
                LoggerUtil.warn(this.getClass(), "Bonus calculated successfully but failed to save: " + saveResult.getErrorMessage());
                // Return calculation result with warning
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

    /**
     * NEW: Get user by ID with proper error handling (centralized from controller)
     * @param userId User ID
     * @return ServiceResult with user data
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
     * NEW: Get register summary from HTTP request
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return ServiceResult with register summary
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

    // ========================================================================
    // DATA CONVERSION METHODS (MOVED FROM CONTROLLER)
    // ========================================================================

    /**
     * MOVED FROM CONTROLLER: Parse and validate save request data
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
     * MOVED FROM CONTROLLER: Convert request entries data to RegisterEntry objects
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

                    // IMPORTANT: Preserve the original adminSync status from the client
                    String adminSync = data.get("adminSync") != null ?
                            data.get("adminSync").toString() : SyncStatusMerge.USER_DONE.name();

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
                            .adminSync(adminSync) // Use the status from the client
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
    // EXISTING METHODS (UNCHANGED)
    // ========================================================================

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
     * Save admin register entries using the new merge service.
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
    // BONUS METHODS (UNCHANGED)
    // ========================================================================

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

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

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

    /**
     * Data record for save request parsing
     */
    private record SaveRequestData(String username, Integer userId, Integer year, Integer month, List<Map<String, Object>> entriesData) {
    }
}