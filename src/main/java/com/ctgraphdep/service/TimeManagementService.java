package com.ctgraphdep.service;

import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeSummaryDTO;
import com.ctgraphdep.service.cache.TimeOffCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * COMPLETE Unified Time Management Service - Facade for worktime and timeoff operations
 * Enhanced with comprehensive field transformation logic
 * Routes time editing to WorktimeManagementService and time off to TimeOffManagementService
 */
@Service
public class TimeManagementService {

    private final WorktimeManagementService worktimeManagementService;
    private final WorktimeDisplayService worktimeDisplayService;
    private final TimeOffCacheService timeOffCacheService;
    private final TimeOffManagementService timeOffManagementService;
    private final TimeValidationService timeValidationService;
    private final UserService userService;
    private final HolidayManagementService holidayManagementService;

    public TimeManagementService(
            WorktimeManagementService worktimeManagementService,
            WorktimeDisplayService worktimeDisplayService,
            TimeOffCacheService timeOffCacheService,
            TimeOffManagementService timeOffManagementService,
            TimeValidationService timeValidationService,
            UserService userService,
            HolidayManagementService holidayManagementService) {
        this.worktimeManagementService = worktimeManagementService;
        this.worktimeDisplayService = worktimeDisplayService;
        this.timeOffCacheService = timeOffCacheService;
        this.timeOffManagementService = timeOffManagementService;
        this.timeValidationService = timeValidationService;
        this.userService = userService;
        this.holidayManagementService = holidayManagementService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // UNIFIED PAGE DATA OPERATIONS
    // ========================================================================

    /**
     * Load combined time management page data
     */
    public TimeManagementPageData loadPageData(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Loading time management page data for %s - %d/%d", username, year, month));

            // Get user data
            User user = userService.getUserByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

            // Load worktime data for the month
            List<WorkTimeTable> worktimeData = worktimeManagementService.loadMonthWorktime(username, year, month);

            // Process worktime data for display
            Map<String, Object> displayData = worktimeDisplayService.prepareUserDisplayData(user, worktimeData, year, month);

            // Load timeoff summary for the year
            TimeOffSummaryDTO timeOffSummary = timeOffCacheService.getTimeOffSummary(username, userId, year);

            // Create combined data object
            TimeManagementPageData pageData = new TimeManagementPageData();
            pageData.setUser(user);
            pageData.setCurrentYear(year);
            pageData.setCurrentMonth(month);
            pageData.setWorktimeData((List<WorkTimeEntryDTO>) displayData.get("worktimeData"));
            pageData.setWorkTimeSummary((WorkTimeSummaryDTO) displayData.get("summary"));
            pageData.setTimeOffSummary(timeOffSummary);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully loaded page data for %s: %d worktime entries, %d available time off days",
                    username, pageData.getWorktimeData().size(), timeOffSummary.getAvailablePaidDays()));

            return pageData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading page data for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            throw new RuntimeException("Failed to load time management data", e);
        }
    }

    // ========================================================================
    // ENHANCED FIELD UPDATE OPERATIONS WITH COMPREHENSIVE TRANSFORMATION
    // ========================================================================

    /**
     * Main field update method with comprehensive transformation logic
     */
    public FieldUpdateResult updateField(String username, Integer userId, LocalDate date, String fieldType, String value) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Processing field update: %s for %s on %s to: %s", fieldType, username, date, value));

            // Step 1: Get current entry state
            WorkTimeTable currentEntry = getCurrentEntry(username, userId, date);

            // Step 2: Validate editability
            if (!isEntryEditable(currentEntry, date)) {
                String reason = currentEntry == null ?
                        "Cannot create entries for future dates" :
                        "Entry cannot be edited (status: " + currentEntry.getAdminSync() + ")";
                return new FieldUpdateResult(false, reason, null);
            }

            // Step 3: Determine transformation type
            TransformationType transformation = determineTransformation(currentEntry, fieldType, value);

            // Step 4: Execute transformation based on type
            FieldUpdateResult result = executeTransformation(username, userId, date, currentEntry, transformation, fieldType, value);

            if (result.isSuccess()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully updated %s for %s on %s: %s", fieldType, username, date, result.getMessage()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to update %s for %s on %s: %s", fieldType, username, date, result.getMessage()));
            }

            return result;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating field %s for %s on %s: %s", fieldType, username, date, e.getMessage()), e);
            return new FieldUpdateResult(false, "Internal error: " + e.getMessage(), null);
        }
    }

    /**
     * Check if a field can be edited - routes to appropriate service
     */
    public FieldEditValidationResult canEditField(String username, Integer userId, LocalDate date, String fieldType) {
        try {
            if ("timeOff".equals(fieldType.toLowerCase())) {
                // Basic time off validation - detailed validation in TimeOffManagementService
                return validateTimeOffEdit(date);
            } else {
                // Time field validation - use WorktimeManagementService
                WorktimeManagementService.FieldEditValidation validation =
                        worktimeManagementService.canEditField(username, userId, date, fieldType);

                return new FieldEditValidationResult(
                        validation.isCanEdit(),
                        validation.getReason(),
                        validation.getCurrentStatus() != null ? validation.getCurrentStatus().toString() : null
                );
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error checking edit permissions for %s on %s: %s", username, date, e.getMessage()));
            return new FieldEditValidationResult(false, "Error checking permissions", null);
        }
    }

    // ========================================================================
    // ENTRY STATE DETECTION AND VALIDATION
    // ========================================================================

    /**
     * Get current worktime entry state for validation and transformation
     */
    private WorkTimeTable getCurrentEntry(String username, Integer userId, LocalDate date) {
        try {
            // Load current month's worktime data
            int year = date.getYear();
            int month = date.getMonthValue();

            List<WorkTimeTable> monthData = worktimeManagementService.loadMonthWorktime(username, year, month);

            // Find entry for specific date
            return monthData.stream()
                    .filter(entry -> entry.getWorkDate().equals(date))
                    .filter(entry -> userId.equals(entry.getUserId()))
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting current entry for %s on %s: %s", username, date, e.getMessage()));
            return null;
        }
    }

    /**
     * Validate if entry can be edited (USER_INPUT status and valid state)
     */
    private boolean isEntryEditable(WorkTimeTable entry, LocalDate date) {
        if (entry == null) {
            // No entry exists - can create new one (for past dates only)
            LocalDate today = LocalDate.now();
            return date.isBefore(today);
        }

        // Check if entry has USER_INPUT status (editable)
        return SyncStatusMerge.USER_INPUT.equals(entry.getAdminSync());
    }

    /**
     * Determine what type of transformation is needed
     */
    private TransformationType determineTransformation(WorkTimeTable currentEntry, String fieldType, String value) {
        boolean hasCurrentTimeOff = currentEntry != null && currentEntry.getTimeOffType() != null;
        boolean hasCurrentWorkTime = currentEntry != null &&
                currentEntry.getDayStartTime() != null && currentEntry.getDayEndTime() != null;

        if ("timeOff".equals(fieldType)) {
            if (value == null || value.trim().isEmpty()) {
                // Clearing time off
                if (hasCurrentTimeOff) {
                    return TransformationType.TIME_OFF_TO_EMPTY;
                } else {
                    return TransformationType.NO_CHANGE;
                }
            } else {
                // Setting time off
                if (hasCurrentWorkTime) {
                    return TransformationType.WORK_TO_TIME_OFF;
                } else if (hasCurrentTimeOff) {
                    return TransformationType.TIME_OFF_TYPE_CHANGE;
                } else {
                    return TransformationType.EMPTY_TO_TIME_OFF;
                }
            }
        } else if ("startTime".equals(fieldType) || "endTime".equals(fieldType)) {
            if (hasCurrentTimeOff) {
                // Can't edit time fields when time off is set
                return TransformationType.INVALID_OPERATION;
            } else {
                return TransformationType.TIME_FIELD_UPDATE;
            }
        }

        return TransformationType.NO_CHANGE;
    }

    /**
     * Basic time off edit validation
     */
    private FieldEditValidationResult validateTimeOffEdit(LocalDate date) {
        // Check if current day
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return new FieldEditValidationResult(false, "Cannot edit current day", null);
        }

        // Check if future date
        if (date.isAfter(today)) {
            return new FieldEditValidationResult(false, "Cannot edit future dates", null);
        }

        // Check if weekend
        if (date.getDayOfWeek().getValue() >= 6) {
            return new FieldEditValidationResult(false, "Cannot add time off on weekends", null);
        }

        // Detailed validation will be done by TimeOffManagementService
        return new FieldEditValidationResult(true, "Can edit", null);
    }

    // ========================================================================
    // TRANSFORMATION EXECUTION
    // ========================================================================

    /**
     * Execute the specific transformation based on the determined type
     */
    private FieldUpdateResult executeTransformation(String username, Integer userId, LocalDate date,
                                                    WorkTimeTable currentEntry, TransformationType transformation,
                                                    String fieldType, String value) {

        return switch (transformation) {
            case TIME_FIELD_UPDATE -> updateTimeField(username, userId, date, currentEntry, fieldType, value);
            case WORK_TO_TIME_OFF -> transformWorkToTimeOff(username, userId, date, currentEntry, value);
            case TIME_OFF_TO_EMPTY -> transformTimeOffToEmpty(username, userId, date, currentEntry);
            case TIME_OFF_TYPE_CHANGE -> changeTimeOffType(username, userId, date, currentEntry, value);
            case EMPTY_TO_TIME_OFF -> createTimeOffEntry(username, userId, date, value);
            case INVALID_OPERATION -> new FieldUpdateResult(false,
                    "Cannot edit time fields when time off is set. Remove time off first.", null);
            case NO_CHANGE -> new FieldUpdateResult(true, "No changes needed", null);
            default -> new FieldUpdateResult(false, "Unknown transformation type", null);
        };
    }

    /**
     * Transform work entry to time off (atomic operation)
     */
    private FieldUpdateResult transformWorkToTimeOff(String username, Integer userId, LocalDate date,
                                                     WorkTimeTable currentEntry, String timeOffType) {
        try {
            // Step 1: Validate holiday balance for CO requests
            ValidationResult balanceCheck = validateHolidayBalance(username, userId, timeOffType, date.getYear());
            if (!balanceCheck.isValid()) {
                return new FieldUpdateResult(false, balanceCheck.getMessage(), null);
            }

            // Step 2: Create transformed entry (clear work fields, set time off)
            WorkTimeTable transformedEntry = createTransformedEntry(currentEntry, null, null, timeOffType);

            // Step 3: Update worktime file (atomic)
            boolean worktimeUpdated = updateWorktimeEntry(username, userId, date, transformedEntry);
            if (!worktimeUpdated) {
                return new FieldUpdateResult(false, "Failed to update worktime entry", null);
            }

            // Step 4: Update holiday balance (CO only)
            if ("CO".equals(timeOffType)) {
                boolean balanceUpdated = updateHolidayBalance(username, userId, -1); // Use 1 day
                if (!balanceUpdated) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Worktime updated but holiday balance update failed for %s", username));
                    // Don't fail the operation - worktime is already updated
                }
            }

            // Step 5: Refresh tracker for this specific entry
            refreshTrackerEntry(username, userId, date, timeOffType);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully transformed work to %s for %s on %s", timeOffType, username, date));

            return new FieldUpdateResult(true, String.format("Converted to %s", timeOffType), transformedEntry);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error transforming work to time off for %s: %s", username, e.getMessage()), e);
            return new FieldUpdateResult(false, "Transformation failed: " + e.getMessage(), null);
        }
    }

    /**
     * Transform time off back to empty (requiring subsequent start/end time input)
     */
    private FieldUpdateResult transformTimeOffToEmpty(String username, Integer userId, LocalDate date,
                                                      WorkTimeTable currentEntry) {
        try {
            String oldTimeOffType = currentEntry.getTimeOffType();

            // Step 1: Create cleared entry
            WorkTimeTable transformedEntry = createTransformedEntry(currentEntry, null, null, null);

            // Step 2: Update worktime file
            boolean worktimeUpdated = updateWorktimeEntry(username, userId, date, transformedEntry);
            if (!worktimeUpdated) {
                return new FieldUpdateResult(false, "Failed to clear time off entry", null);
            }

            // Step 3: Restore holiday balance (CO only)
            if ("CO".equals(oldTimeOffType)) {
                boolean balanceUpdated = updateHolidayBalance(username, userId, 1); // Restore 1 day
                if (!balanceUpdated) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Time off cleared but holiday balance restore failed for %s", username));
                }
            }

            // Step 4: Refresh tracker
            refreshTrackerEntry(username, userId, date, null);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully cleared %s time off for %s on %s", oldTimeOffType, username, date));

            return new FieldUpdateResult(true, String.format("Cleared %s - now add start/end times", oldTimeOffType),
                    transformedEntry);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error clearing time off for %s: %s", username, e.getMessage()), e);
            return new FieldUpdateResult(false, "Failed to clear time off: " + e.getMessage(), null);
        }
    }

    /**
     * Change time off type (CO ↔ CM)
     */
    private FieldUpdateResult changeTimeOffType(String username, Integer userId, LocalDate date,
                                                WorkTimeTable currentEntry, String newTimeOffType) {
        try {
            String oldTimeOffType = currentEntry.getTimeOffType();

            // Step 1: Validate holiday balance if changing to CO
            if ("CO".equals(newTimeOffType)) {
                ValidationResult balanceCheck = validateHolidayBalance(username, userId, newTimeOffType, date.getYear());
                if (!balanceCheck.isValid()) {
                    return new FieldUpdateResult(false, balanceCheck.getMessage(), null);
                }
            }

            // Step 2: Create updated entry
            WorkTimeTable transformedEntry = createTransformedEntry(currentEntry, null, null, newTimeOffType);

            // Step 3: Update worktime file
            boolean worktimeUpdated = updateWorktimeEntry(username, userId, date, transformedEntry);
            if (!worktimeUpdated) {
                return new FieldUpdateResult(false, "Failed to change time off type", null);
            }

            // Step 4: Update holiday balance based on change
            if ("CO".equals(oldTimeOffType) && !"CO".equals(newTimeOffType)) {
                // CO → CM: restore holiday day
                updateHolidayBalance(username, userId, 1);
            } else if (!"CO".equals(oldTimeOffType) && "CO".equals(newTimeOffType)) {
                // CM → CO: use holiday day
                updateHolidayBalance(username, userId, -1);
            }
            // CO → CO or CM → CM: no balance change needed

            // Step 5: Refresh tracker
            refreshTrackerEntry(username, userId, date, newTimeOffType);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully changed time off from %s to %s for %s on %s",
                    oldTimeOffType, newTimeOffType, username, date));

            return new FieldUpdateResult(true, String.format("Changed from %s to %s", oldTimeOffType, newTimeOffType),
                    transformedEntry);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error changing time off type for %s: %s", username, e.getMessage()), e);
            return new FieldUpdateResult(false, "Failed to change time off type: " + e.getMessage(), null);
        }
    }

    /**
     * Update individual time field (start or end time)
     */
    private FieldUpdateResult updateTimeField(String username, Integer userId, LocalDate date,
                                              WorkTimeTable currentEntry, String fieldType, String value) {
        try {
            LocalDateTime timeValue = parseTimeValue(date, value);

            // Update the specific field
            if ("startTime".equals(fieldType)) {
                boolean success = worktimeManagementService.updateStartTime(username, userId, date, timeValue);
                return new FieldUpdateResult(success,
                        success ? "Start time updated" : "Failed to update start time", null);
            } else if ("endTime".equals(fieldType)) {
                boolean success = worktimeManagementService.updateEndTime(username, userId, date, timeValue);
                return new FieldUpdateResult(success,
                        success ? "End time updated" : "Failed to update end time", null);
            }

            return new FieldUpdateResult(false, "Unknown time field: " + fieldType, null);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating time field %s: %s", fieldType, e.getMessage()));
            return new FieldUpdateResult(false, "Invalid time format: " + e.getMessage(), null);
        }
    }

    /**
     * Create new time off entry (from empty state)
     */
    private FieldUpdateResult createTimeOffEntry(String username, Integer userId, LocalDate date, String timeOffType) {
        try {
            // Step 1: Validate holiday balance for CO requests
            ValidationResult balanceCheck = validateHolidayBalance(username, userId, timeOffType, date.getYear());
            if (!balanceCheck.isValid()) {
                return new FieldUpdateResult(false, balanceCheck.getMessage(), null);
            }

            // Step 2: Create new time off entry
            WorkTimeTable newEntry = createNewTimeOffEntry(userId, date, timeOffType);

            // Step 3: Update worktime file
            boolean worktimeUpdated = updateWorktimeEntry(username, userId, date, newEntry);
            if (!worktimeUpdated) {
                return new FieldUpdateResult(false, "Failed to create time off entry", null);
            }

            // Step 4: Update holiday balance (CO only)
            if ("CO".equals(timeOffType)) {
                updateHolidayBalance(username, userId, -1);
            }

            // Step 5: Refresh tracker
            refreshTrackerEntry(username, userId, date, timeOffType);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully created %s time off for %s on %s", timeOffType, username, date));

            return new FieldUpdateResult(true, String.format("Created %s time off", timeOffType), newEntry);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error creating time off entry for %s: %s", username, e.getMessage()), e);
            return new FieldUpdateResult(false, "Failed to create time off: " + e.getMessage(), null);
        }
    }

    // ========================================================================
    // HELPER METHODS FOR ENTRY TRANSFORMATION
    // ========================================================================

    /**
     * Create transformed entry with specified values
     */
    private WorkTimeTable createTransformedEntry(WorkTimeTable originalEntry, LocalDateTime startTime,
                                                 LocalDateTime endTime, String timeOffType) {
        WorkTimeTable transformed = new WorkTimeTable();

        // Copy base fields
        transformed.setUserId(originalEntry.getUserId());
        transformed.setWorkDate(originalEntry.getWorkDate());
        transformed.setAdminSync(SyncStatusMerge.USER_INPUT); // Always USER_INPUT for user changes

        if (timeOffType != null) {
            // Creating time off entry - clear work fields
            transformed.setDayStartTime(null);
            transformed.setDayEndTime(null);
            transformed.setTemporaryStopCount(0);
            transformed.setLunchBreakDeducted(false);
            transformed.setTimeOffType(timeOffType);
            transformed.setTotalWorkedMinutes(0);
            transformed.setTotalTemporaryStopMinutes(0);
            transformed.setTotalOvertimeMinutes(0);
        } else {
            // Creating work entry or clearing time off - preserve/set work fields
            transformed.setDayStartTime(startTime != null ? startTime : originalEntry.getDayStartTime());
            transformed.setDayEndTime(endTime != null ? endTime : originalEntry.getDayEndTime());
            transformed.setTemporaryStopCount(originalEntry.getTemporaryStopCount());
            transformed.setLunchBreakDeducted(originalEntry.isLunchBreakDeducted());
            transformed.setTimeOffType(null);

            // Work time calculations will be handled by the worktime service
            // Just copy existing values for now
            transformed.setTotalWorkedMinutes(originalEntry.getTotalWorkedMinutes());
            transformed.setTotalTemporaryStopMinutes(originalEntry.getTotalTemporaryStopMinutes());
            transformed.setTotalOvertimeMinutes(originalEntry.getTotalOvertimeMinutes());
        }

        return transformed;
    }

    /**
     * Create new time off entry from scratch
     */
    private WorkTimeTable createNewTimeOffEntry(Integer userId, LocalDate date, String timeOffType) {
        WorkTimeTable newEntry = new WorkTimeTable();

        newEntry.setUserId(userId);
        newEntry.setWorkDate(date);
        newEntry.setDayStartTime(null);
        newEntry.setDayEndTime(null);
        newEntry.setTemporaryStopCount(0);
        newEntry.setLunchBreakDeducted(false);
        newEntry.setTimeOffType(timeOffType);
        newEntry.setTotalWorkedMinutes(0);
        newEntry.setTotalTemporaryStopMinutes(0);
        newEntry.setTotalOvertimeMinutes(0);
        newEntry.setAdminSync(SyncStatusMerge.USER_INPUT);

        return newEntry;
    }

    /**
     * Update worktime entry in file system
     */
    private boolean updateWorktimeEntry(String username, Integer userId, LocalDate date, WorkTimeTable entry) {
        try {
            int year = date.getYear();
            int month = date.getMonthValue();

            // Get current month data
            List<WorkTimeTable> monthData = worktimeManagementService.loadMonthWorktime(username, year, month);

            // Update or add the entry
            boolean entryFound = false;
            for (int i = 0; i < monthData.size(); i++) {
                WorkTimeTable existing = monthData.get(i);
                if (existing.getWorkDate().equals(date) && userId.equals(existing.getUserId())) {
                    monthData.set(i, entry);
                    entryFound = true;
                    break;
                }
            }

            if (!entryFound) {
                monthData.add(entry);
            }

            // Use individual field updates for consistency with existing system
            if (entry.getTimeOffType() != null) {
                // Setting time off - use time off management service
                List<LocalDate> dates = Collections.singletonList(date);
                return timeOffManagementService.addTimeOffRequest(username, userId, dates, entry.getTimeOffType());
            } else {
                // Setting work time - update individual fields
                boolean startUpdated = true;
                boolean endUpdated = true;

                if (entry.getDayStartTime() != null) {
                    startUpdated = worktimeManagementService.updateStartTime(username, userId, date, entry.getDayStartTime());
                }

                if (entry.getDayEndTime() != null) {
                    endUpdated = worktimeManagementService.updateEndTime(username, userId, date, entry.getDayEndTime());
                }

                // Clear time off if it was set
                if (entry.getTimeOffType() == null) {
                    worktimeManagementService.clearTimeOff(username, userId, date);
                }

                return startUpdated && endUpdated;
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating worktime entry for %s on %s: %s", username, date, e.getMessage()));
            return false;
        }
    }

    // ========================================================================
    // VALIDATION AND BALANCE MANAGEMENT
    // ========================================================================

    /**
     * Check available holiday balance for CO requests
     */
    private ValidationResult validateHolidayBalance(String username, Integer userId, String timeOffType, int year) {
        if (!"CO".equals(timeOffType)) {
            return new ValidationResult(true, "Medical leave doesn't require balance check");
        }

        try {
            // Get current balance from time off cache
            TimeOffSummaryDTO summary = timeOffCacheService.getTimeOffSummary(username, userId, year);
            int availableBalance = summary.getAvailablePaidDays();

            if (availableBalance <= 0) {
                return new ValidationResult(false, String.format(
                        "Insufficient vacation balance. Available: %d days", availableBalance));
            }

            return new ValidationResult(true, String.format("Balance check passed. Available: %d days", availableBalance));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error checking holiday balance for %s: %s", username, e.getMessage()));
            return new ValidationResult(false, "Unable to verify vacation balance. Please try again.");
        }
    }

    /**
     * Update holiday balance
     */
    private boolean updateHolidayBalance(String username, Integer userId, int dayChange) {
        try {
            // Get current user data
            User user = userService.getUserByUsername(username).orElse(null);
            if (user == null) {
                LoggerUtil.error(this.getClass(), "User not found for holiday balance update: " + username);
                return false;
            }

            Integer currentBalance = user.getPaidHolidayDays();
            if (currentBalance == null) {
                currentBalance = 0;
            }

            int newBalance = currentBalance + dayChange;

            // Prevent negative balance
            if (newBalance < 0) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Holiday balance would go negative for %s: %d + %d = %d",
                        username, currentBalance, dayChange, newBalance));
                return false;
            }

            // Update holiday balance
            holidayManagementService.updateUserHolidayDays(userId, newBalance);

            LoggerUtil.info(this.getClass(), String.format(
                    "Updated holiday balance for %s: %d → %d (%+d)",
                    username, currentBalance, newBalance, dayChange));

            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating holiday balance for %s: %s", username, e.getMessage()));
            return false;
        }
    }

    /**
     * Refresh specific tracker entry and available days
     */
    private void refreshTrackerEntry(String username, Integer userId, LocalDate date, String timeOffType) {
        try {
            int year = date.getYear();

            // Clear the cache for this year to force refresh
            timeOffCacheService.clearYear(username, year);

            // Trigger rebuild of tracker from current worktime files
            timeOffCacheService.refreshTrackerFromWorktime(username, userId, year);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Refreshed tracker for %s - %d after %s change on %s",
                    username, year, timeOffType != null ? timeOffType : "cleared", date));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error refreshing tracker for %s: %s", username, e.getMessage()));
            // Don't fail the operation - tracker will refresh on next access
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Parse time value from string (HH:mm format) to LocalDateTime
     */
    private LocalDateTime parseTimeValue(LocalDate date, String timeValue) throws DateTimeParseException {
        if (timeValue == null || timeValue.trim().isEmpty()) {
            return null;
        }

        // Clean the input
        String cleanTime = timeValue.trim();

        // Try to parse as HH:mm
        try {
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            java.time.LocalTime time = java.time.LocalTime.parse(cleanTime, timeFormatter);
            return date.atTime(time);
        } catch (DateTimeParseException e) {
            // Try alternative formats if needed
            LoggerUtil.warn(this.getClass(), String.format(
                    "Failed to parse time: %s", cleanTime));
            throw e;
        }
    }

    // ========================================================================
    // ENUMS AND INNER CLASSES
    // ========================================================================

    /**
     * Enum for transformation types
     */
    private enum TransformationType {
        NO_CHANGE,
        TIME_FIELD_UPDATE,
        WORK_TO_TIME_OFF,
        TIME_OFF_TO_EMPTY,
        TIME_OFF_TYPE_CHANGE,
        EMPTY_TO_TIME_OFF,
        INVALID_OPERATION
    }

    /**
     * Simple validation result class
     */
    @Getter
    private static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

    }

    // ========================================================================
    // RESULT CLASSES (Keep existing from original)
    // ========================================================================

    /**
     * Result of field update operation
     */
    @Getter
    public static class FieldUpdateResult {
        private final boolean success;
        private final String message;
        private final Object updatedData;

        public FieldUpdateResult(boolean success, String message, Object updatedData) {
            this.success = success;
            this.message = message;
            this.updatedData = updatedData;
        }
    }

    /**
     * Result of field edit validation
     */
    @Getter
    public static class FieldEditValidationResult {
        private final boolean canEdit;
        private final String reason;
        private final String currentStatus;

        public FieldEditValidationResult(boolean canEdit, String reason, String currentStatus) {
            this.canEdit = canEdit;
            this.reason = reason;
            this.currentStatus = currentStatus;
        }
    }

    /**
     * Combined page data structure
     */
    @Setter
    @Getter
    public static class TimeManagementPageData {
        private User user;
        private int currentYear;
        private int currentMonth;
        private List<WorkTimeEntryDTO> worktimeData;
        private WorkTimeSummaryDTO workTimeSummary;
        private TimeOffSummaryDTO timeOffSummary;
    }
}