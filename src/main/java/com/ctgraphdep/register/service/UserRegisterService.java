package com.ctgraphdep.register.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.service.cache.RegisterCacheService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.service.result.ValidationServiceResult;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REFACTORED UserRegisterService with ServiceResult pattern.
 * Key Changes:
 * - All methods now return ServiceResult<T> instead of throwing exceptions
 * - Uses ValidationServiceResult for comprehensive entry validation
 * - Proper error categorization and graceful error handling
 * - Preserved existing cache system with better error management
 * - Enhanced logging and warning support
 */
@Service
public class UserRegisterService {

    private final RegisterDataService registerDataService;
    private final RegisterCacheService registerCacheService;
    private final RegisterMergeService registerMergeService;

    @Autowired
    public UserRegisterService(RegisterDataService registerDataService, RegisterCacheService registerCacheService, RegisterMergeService registerMergeService) {
        this.registerDataService = registerDataService;
        this.registerCacheService = registerCacheService;
        this.registerMergeService = registerMergeService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Load month entries with intelligent merging and caching
     *
     * @param username Username
     * @param userId   User ID
     * @param year     Year
     * @param month    Month
     * @return ServiceResult with register entries
     */
    public ServiceResult<List<RegisterEntry>> loadMonthEntries(String username, Integer userId, int year, int month) {
        try {
            // Validate input parameters
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotEmpty(username, "Username", "missing_username")
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .validate(() -> userId > WorkCode.DEFAULT_ZERO, "User ID must be positive", "invalid_user_id")
                    .validate(() -> year >= 2000 && year <= 2100, "Year must be between 2000 and 2100", "invalid_year")
                    .validate(() -> month >= 1 && month <= 12, "Month must be between 1 and 12", "invalid_month");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            List<String> warnings = new ArrayList<>();

            if (currentUsername.equals(username)) {
                // Check if this is NOT the current month
                LocalDate currentDate = LocalDate.now();
                boolean isCurrentMonth = (year == currentDate.getYear() && month == currentDate.getMonthValue());

                if (!isCurrentMonth) {
                    // For non-current months, perform merge first to get admin changes
                    LoggerUtil.info(this.getClass(), String.format("Performing on-demand merge for %s - %d/%d (non-current month)", username, year, month));

                    ServiceResult<String> mergeResult = registerMergeService.performUserLoginMerge(username, userId, year, month);
                    if (mergeResult.isFailure()) {
                        warnings.add("Failed to merge admin changes: " + mergeResult.getErrorMessage());
                        LoggerUtil.warn(this.getClass(), String.format("Merge failed for %s - %d/%d: %s", username, year, month, mergeResult.getErrorMessage()));
                    } else if (mergeResult.hasWarnings()) {
                        warnings.addAll(mergeResult.getWarnings());
                    }
                }

                // Use cache - will load fresh merged data
                List<RegisterEntry> entries = registerCacheService.getMonthEntries(username, userId, year, month);

                LoggerUtil.info(this.getClass(), String.format("Loaded %d entries for %s - %d/%d from cache", entries.size(), username, year, month));

                if (!warnings.isEmpty()) {
                    return ServiceResult.successWithWarnings(entries, warnings);
                }

                return ServiceResult.success(entries);
            } else {
                // Admin accessing other users - direct network read
                try {
                    List<RegisterEntry> entries = registerDataService.readUserFromNetworkOnly(username, userId, year, month);
                    LoggerUtil.info(this.getClass(), String.format("Loaded %d entries for %s - %d/%d from network (admin access)", entries.size(), username, year, month));
                    return ServiceResult.success(entries);
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format("Error reading network entries for %s: %s", username, e.getMessage()), e);
                    return ServiceResult.systemError("Failed to load entries from network", "load_network_entries_failed");
                }
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error loading month entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error loading month entries", "load_entries_system_error");
        }
    }

    /**
     * Save register entry with comprehensive validation and cache management
     *
     * @param username Username
     * @param userId   User ID
     * @param entry    Register entry to save
     * @return ServiceResult with saved entry
     */
    public ServiceResult<RegisterEntry> saveEntry(String username, Integer userId, RegisterEntry entry) {
        try {
            // Validate input parameters
            ValidationServiceResult paramValidation = ValidationServiceResult.create()
                    .requireNotEmpty(username, "Username", "missing_username")
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .requireNotNull(entry, "Entry", "missing_entry")
                    .validate(() -> userId > 0, "User ID must be positive", "invalid_user_id");

            if (paramValidation.hasErrors()) {
                return ServiceResult.validationError(paramValidation.getFirstError(), paramValidation.getFirstErrorCode());
            }

            // Validate entry content
            ServiceResult<Void> entryValidationResult = validateEntry(entry);
            if (entryValidationResult.isFailure()) {
                return ServiceResult.validationError(entryValidationResult.getErrorMessage(), entryValidationResult.getErrorCode());
            }

            // Set initial state
            entry.setUserId(userId);

            int year = entry.getDate().getYear();
            int month = entry.getDate().getMonthValue();

            List<String> warnings = new ArrayList<>();

            // Ensure cache is loaded for this month (loads from file if cache empty)
            LoggerUtil.debug(this.getClass(), String.format("Ensuring cache is loaded for %s - %d/%d", username, month, year));
            registerCacheService.getMonthEntries(username, userId, year, month);

            // Generate entry ID if needed (new entry)
            if (entry.getEntryId() == null) {
                // Get current entries from cache to determine next ID
                List<RegisterEntry> currentEntries = registerCacheService.getMonthEntries(username, userId, year, month);
                entry.setEntryId(generateNextEntryId(currentEntries));

                // NEW ENTRY: Always USER_INPUT
                entry.setAdminSync(MergingStatusConstants.USER_INPUT);

                LoggerUtil.info(this.getClass(), String.format("New entry %d created with USER_INPUT status", entry.getEntryId()));
            } else {
                // EXISTING ENTRY: Check current status to determine new status
                RegisterEntry existingEntry = registerCacheService.getEntry(username, userId, entry.getEntryId(), year, month);

                if (existingEntry != null) {
                    String currentStatus = existingEntry.getAdminSync();

                    // Check if this is an edit (not a new entry)
                    // When user edits an existing entry, create timestamped USER_EDITED status
                    if (currentStatus != null && !currentStatus.isEmpty()) {
                        entry.setAdminSync(MergingStatusConstants.createUserEditedStatus());
                        LoggerUtil.info(this.getClass(), String.format(
                                "Entry %d status changed: %s → %s (user edited existing entry)",
                                entry.getEntryId(), currentStatus, entry.getAdminSync()));
                    } else {
                        // Keep existing status for other cases
                        entry.setAdminSync(currentStatus);
                        LoggerUtil.debug(this.getClass(), String.format(
                                "Entry %d keeping existing status: %s", entry.getEntryId(), currentStatus));
                    }
                } else {
                    // Entry not found in cache, treat as new
                    entry.setAdminSync(MergingStatusConstants.USER_INPUT);
                    warnings.add("Entry " + entry.getEntryId() + " not found in cache, treating as new");
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Entry %d not found in cache, treating as new with USER_INPUT status", entry.getEntryId()));
                }
            }

            // Add/update entry using cache (which will write-through to file)
            boolean success;

            // Check if entry exists in cache
            RegisterEntry existingEntry = registerCacheService.getEntry(username, userId, entry.getEntryId(), year, month);

            if (existingEntry != null) {
                LoggerUtil.debug(this.getClass(), String.format("Updating existing entry %d in cache", entry.getEntryId()));
                success = registerCacheService.updateEntry(username, userId, entry);
            } else {
                LoggerUtil.debug(this.getClass(), String.format("Adding new entry %d to cache", entry.getEntryId()));
                success = registerCacheService.addEntry(username, userId, entry);
            }

            if (!success) {
                return ServiceResult.systemError("Failed to save entry to cache", "cache_save_failed");
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully saved entry %d for user %s with status %s", entry.getEntryId(), username, entry.getAdminSync()));

            if (!warnings.isEmpty()) {
                return ServiceResult.successWithWarnings(entry, warnings);
            }

            return ServiceResult.success(entry);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error saving entry for user %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error saving entry", "save_entry_system_error");
        }
    }

    /**
     * Delete register entry with validation and cache management
     *
     * @param username Username
     * @param userId   User ID
     * @param entryId  Entry ID to delete
     * @param year     Year
     * @param month    Month
     * @return ServiceResult indicating success or failure
     */
    public ServiceResult<Void> deleteEntry(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            // Validate input parameters
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotEmpty(username, "Username", "missing_username")
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .requireNotNull(entryId, "Entry ID", "missing_entry_id")
                    .validate(() -> userId > 0, "User ID must be positive", "invalid_user_id")
                    .validate(() -> entryId > 0, "Entry ID must be positive", "invalid_entry_id")
                    .validate(() -> year >= 2000 && year <= 2100, "Year must be between 2000 and 2100", "invalid_year")
                    .validate(() -> month >= 1 && month <= 12, "Month must be between 1 and 12", "invalid_month");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            // Verify entry exists in cache (this will load from file if needed)
            RegisterEntry existingEntry = registerCacheService.getEntry(username, userId, entryId, year, month);

            if (existingEntry == null) {
                return ServiceResult.notFound("Entry not found: " + entryId, "entry_not_found");
            }

            // Verify entry belongs to user
            if (!existingEntry.getUserId().equals(userId)) {
                return ServiceResult.unauthorized("Access denied - entry belongs to different user", "entry_access_denied");
            }

            // Delete entry using cache (which will write-through to file)
            boolean success = registerCacheService.deleteEntry(username, userId, entryId, year, month);

            if (!success) {
                return ServiceResult.systemError("Failed to delete entry from cache", "cache_delete_failed");
            }

            LoggerUtil.info(this.getClass(), String.format("Deleted register entry %d for user %s", entryId, username));
            return ServiceResult.success();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error deleting entry %d for user %s: %s", entryId, username, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error deleting entry", "delete_entry_system_error");
        }
    }

    /**
     * Perform full register search across multiple months
     *
     * @param username Username
     * @param userId   User ID
     * @param query    Search query
     * @return ServiceResult with search results
     */
    public ServiceResult<List<RegisterEntry>> performFullRegisterSearch(String username, Integer userId, String query) {
        try {
            // Validate input parameters
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotEmpty(username, "Username", "missing_username")
                    .requireNotNull(userId, "User ID", "missing_user_id")
                    .validate(() -> userId > 0, "User ID must be positive", "invalid_user_id");

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            LoggerUtil.info(this.getClass(), "Performing full register search across multiple months (file-based)");

            // First, retrieve all entries
            List<RegisterEntry> allEntries;
            try {
                allEntries = registerDataService.findRegisterFiles(username, userId);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error retrieving register files for search for %s: %s", username, e.getMessage()), e);
                return ServiceResult.systemError("Failed to retrieve register files for search", "search_retrieve_failed");
            }

            // If no query, return all entries sorted by date
            if (query == null || query.trim().isEmpty()) {
                List<RegisterEntry> sortedEntries = allEntries.stream()
                        .sorted(Comparator.comparing(RegisterEntry::getDate).reversed())
                        .collect(Collectors.toList());

                LoggerUtil.info(this.getClass(), String.format("Search completed for %s: returned %d entries (no query filter)", username, sortedEntries.size()));
                return ServiceResult.success(sortedEntries);
            }

            // Split query into search terms
            String[] searchTerms = query.toLowerCase().split("\\s+");

            // Filter entries
            List<RegisterEntry> filteredEntries = allEntries.stream()
                    .filter(entry ->
                            Arrays.stream(searchTerms).allMatch(term ->
                                    (entry.getOrderId() != null && entry.getOrderId().toLowerCase().contains(term)) ||
                                            (entry.getProductionId() != null && entry.getProductionId().toLowerCase().contains(term)) ||
                                            (entry.getOmsId() != null && entry.getOmsId().toLowerCase().contains(term)) ||
                                            (entry.getClientName() != null && entry.getClientName().toLowerCase().contains(term)) ||
                                            (entry.getActionType() != null && entry.getActionType().toLowerCase().contains(term)) ||
                                            (entry.getPrintPrepTypes() != null &&
                                                    entry.getPrintPrepTypes().stream().anyMatch(type ->
                                                            type.toLowerCase().contains(term))) ||
                                            (entry.getObservations() != null && entry.getObservations().toLowerCase().contains(term))
                            )
                    )
                    .sorted(Comparator.comparing(RegisterEntry::getDate).reversed())
                    .collect(Collectors.toList());

            LoggerUtil.info(this.getClass(), String.format("Search completed for %s: query='%s', found %d entries", username, query, filteredEntries.size()));
            return ServiceResult.success(filteredEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error performing search for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error performing search", "search_system_error");
        }
    }

    // ========================================================================
    // PRIVATE VALIDATION AND HELPER METHODS - ENHANCED
    // ========================================================================

    /**
     * Comprehensive entry validation using ValidationServiceResult
     * @param entry Entry to validate
     * @return ServiceResult with validation results
     */
    private ServiceResult<Void> validateEntry(RegisterEntry entry) {
        try {
            ValidationServiceResult validation = ValidationServiceResult.create()
                    .requireNotNull(entry.getDate(), "Date", "missing_date")
                    .requireNotEmpty(entry.getOrderId(), "Order ID", "missing_order_id")
                    .requireNotEmpty(entry.getOmsId(), "OMS ID", "missing_oms_id")
                    .requireNotEmpty(entry.getClientName(), "Client name", "missing_client_name")
                    .requireNotEmpty(entry.getActionType(), "Action type", "missing_action_type")
                    .requireNotNull(entry.getPrintPrepTypes(), "Print prep types", "missing_print_prep_types")
                    .validate(() -> entry.getPrintPrepTypes() != null && !entry.getPrintPrepTypes().isEmpty(),
                            "At least one print prep type is required", "empty_print_prep_types")
                    .requirePositive(entry.getArticleNumbers(), "Article numbers", "missing_article_numbers")
                    .validate(() -> entry.getDate().isBefore(LocalDate.now().plusDays(1)),
                            "Entry date cannot be in the future", "future_date")
                    .validate(() -> entry.getDate().isAfter(LocalDate.of(2000, 1, 1)),
                            "Entry date must be after year 2000", "invalid_historical_date");

            // Additional business validations
            if (entry.getGraphicComplexity() != null) {
                validation.validate(() -> entry.getGraphicComplexity() >= 0.0 && entry.getGraphicComplexity() <= 10.0,
                        "Graphic complexity must be between 0.0 and 10.0", "invalid_graphic_complexity");
            }

            // Validate print prep types are valid enum values if needed
            if (entry.getPrintPrepTypes() != null) {
                List<String> invalidTypes = entry.getPrintPrepTypes().stream()
                        .filter(type -> type == null || type.trim().isEmpty())
                        .toList();

                if (!invalidTypes.isEmpty()) {
                    validation.validate(() -> false, "Print prep types cannot contain empty values", "invalid_print_prep_type");
                }
            }

            if (validation.hasErrors()) {
                return ServiceResult.validationError(validation.getFirstError(), validation.getFirstErrorCode());
            }

            return ServiceResult.success();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during entry validation: " + e.getMessage(), e);
            return ServiceResult.systemError("Validation system error", "validation_system_error");
        }
    }

    /**
     * Generate the next entry ID for the given list of entries
     * @param entries Current entries
     * @return Next available entry ID
     */
    private int generateNextEntryId(List<RegisterEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 1;
        }

        try {
            return entries.stream()
                    .mapToInt(entry -> entry.getEntryId() != null ? entry.getEntryId() : 0)
                    .max()
                    .orElse(0) + 1;
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error generating next entry ID, using fallback: " + e.getMessage());
            // Fallback: use current timestamp's last 6 digits as ID
            return (int) (System.currentTimeMillis() % 1000000);
        }
    }

    /**
     * Additional helper method to check if entry belongs to user
     * @param entry Entry to check
     * @param userId Expected user ID
     * @return true if entry belongs to user
     */
    private boolean entryBelongsToUser(RegisterEntry entry, Integer userId) {
        return entry != null && entry.getUserId() != null && entry.getUserId().equals(userId);
    }

    /**
     * Helper method to sanitize and normalize entry data
     * @param entry Entry to sanitize
     * @return Sanitized entry
     */
    private RegisterEntry sanitizeEntry(RegisterEntry entry) {
        if (entry == null) return null;

        try {
            // Trim string fields
            if (entry.getOrderId() != null) {
                entry.setOrderId(entry.getOrderId().trim());
            }
            if (entry.getProductionId() != null) {
                entry.setProductionId(entry.getProductionId().trim());
            }
            if (entry.getOmsId() != null) {
                entry.setOmsId(entry.getOmsId().trim());
            }
            if (entry.getClientName() != null) {
                entry.setClientName(entry.getClientName().trim());
            }
            if (entry.getObservations() != null) {
                entry.setObservations(entry.getObservations().trim());
            }
            if (entry.getColorsProfile() != null) {
                entry.setColorsProfile(entry.getColorsProfile().trim().toUpperCase());
            }

            // Sanitize print prep types
            if (entry.getPrintPrepTypes() != null) {
                List<String> sanitizedTypes = entry.getPrintPrepTypes().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(type -> !type.isEmpty())
                        .distinct()
                        .collect(Collectors.toList());
                entry.setPrintPrepTypes(sanitizedTypes);
            }

            return entry;

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error sanitizing entry: " + e.getMessage());
            return entry; // Return original if sanitization fails
        }
    }

    /**
     * Helper method to check cache health and recover if needed
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return true if cache is healthy or was successfully recovered
     */
    private boolean ensureCacheHealth(String username, Integer userId, int year, int month) {
        try {
            // Check if cache has entries for this month
            List<RegisterEntry> cachedEntries = registerCacheService.getMonthEntries(username, userId, year, month);

            // If cache is empty but file exists, it might be a cache issue
            if (cachedEntries.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format("Cache empty for %s - %d/%d, checking file system", username, year, month));

                // Try to read directly from file to see if data exists
                List<RegisterEntry> fileEntries = registerDataService.readUserLocalReadOnly(username, userId, username, year, month);

                if (fileEntries != null && !fileEntries.isEmpty()) {
                    LoggerUtil.info(this.getClass(), String.format("Found %d entries in file but cache was empty, cache will be refreshed on next access", fileEntries.size()));
                    // Clear cache to force reload
                    registerCacheService.clearMonth(username, year, month);
                }
            }

            return true;

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error checking cache health for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return false;
        }
    }

    /**
     * Helper method to validate date ranges for entries
     * @param date Date to validate
     * @param year Expected year
     * @param month Expected month
     * @return ServiceResult with validation result
     */
    private ServiceResult<Void> validateDateConsistency(LocalDate date, int year, int month) {
        if (date == null) {
            return ServiceResult.validationError("Date cannot be null", "null_date");
        }

        if (date.getYear() != year) {
            return ServiceResult.validationError(
                    String.format("Entry date year (%d) does not match target year (%d)", date.getYear(), year),
                    "date_year_mismatch");
        }

        if (date.getMonthValue() != month) {
            return ServiceResult.validationError(
                    String.format("Entry date month (%d) does not match target month (%d)", date.getMonthValue(), month),
                    "date_month_mismatch");
        }

        return ServiceResult.success();
    }
}