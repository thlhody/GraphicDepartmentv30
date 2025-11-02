package com.ctgraphdep.worktime.display;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.User;
import com.ctgraphdep.worktime.display.calculators.OvertimeDeductionCalculator;
import com.ctgraphdep.worktime.display.calculators.WorkTimeSummaryCalculator;
import com.ctgraphdep.worktime.display.counters.TimeOffDayCounter;
import com.ctgraphdep.worktime.display.counters.WorkDayCounter;
import com.ctgraphdep.worktime.display.mappers.TimeOffLabelMapper;
import com.ctgraphdep.worktime.display.preparation.DisplayDataPreparationService;
import com.ctgraphdep.worktime.display.response.EntryDetailResponseBuilder;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkTimeSummary;
import com.ctgraphdep.model.dto.status.GeneralDataStatusDTO;
import com.ctgraphdep.model.dto.worktime.*;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.service.cache.TimeOffCacheService;
import com.ctgraphdep.service.cache.WorktimeCacheService;
import com.ctgraphdep.service.dto.WorkTimeDisplayDTOFactory;
import com.ctgraphdep.service.dto.WorkTimeEntryDTOFactory;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.util.WorkTimeEntryUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorktimeDisplayService {

    private final WorktimeOperationService worktimeOperationService;
    private final TimeOffCacheService timeOffCacheService;
    private final WorktimeCacheService worktimeCacheService;
    private final AllUsersCacheService allUsersCacheService;
    private final WorkTimeDisplayDTOFactory displayDTOFactory;
    private final WorkTimeEntryDTOFactory entryDTOFactory;

    // Phase 1 Refactoring: Specialized counters and calculators
    private final TimeOffDayCounter timeOffDayCounter;
    private final WorkDayCounter workDayCounter;
    private final OvertimeDeductionCalculator overtimeDeductionCalculator;
    private final WorkTimeSummaryCalculator workTimeSummaryCalculator;

    // Phase 2 Refactoring: Mappers and response builders
    private final TimeOffLabelMapper labelMapper;
    private final EntryDetailResponseBuilder responseBuilder;

    // Phase 3 Refactoring: Display data preparation
    private final DisplayDataPreparationService displayDataPreparationService;

    @Autowired
    private StatusDTOConverter statusDTOConverter;

    @Autowired
    public WorktimeDisplayService(WorktimeOperationService worktimeOperationService,
                                 TimeOffCacheService timeOffCacheService,
                                 WorktimeCacheService worktimeCacheService,
                                 AllUsersCacheService allUsersCacheService,
                                 WorkTimeDisplayDTOFactory displayDTOFactory,
                                 WorkTimeEntryDTOFactory entryDTOFactory,
                                 TimeOffDayCounter timeOffDayCounter,
                                 WorkDayCounter workDayCounter,
                                 OvertimeDeductionCalculator overtimeDeductionCalculator,
                                 WorkTimeSummaryCalculator workTimeSummaryCalculator,
                                 TimeOffLabelMapper labelMapper,
                                 EntryDetailResponseBuilder responseBuilder,
                                 DisplayDataPreparationService displayDataPreparationService) {
        this.worktimeOperationService = worktimeOperationService;
        this.timeOffCacheService = timeOffCacheService;
        this.worktimeCacheService = worktimeCacheService;
        this.allUsersCacheService = allUsersCacheService;
        this.displayDTOFactory = displayDTOFactory;
        this.entryDTOFactory = entryDTOFactory;
        this.timeOffDayCounter = timeOffDayCounter;
        this.workDayCounter = workDayCounter;
        this.overtimeDeductionCalculator = overtimeDeductionCalculator;
        this.workTimeSummaryCalculator = workTimeSummaryCalculator;
        this.labelMapper = labelMapper;
        this.responseBuilder = responseBuilder;
        this.displayDataPreparationService = displayDataPreparationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // COMBINED DISPLAY DATA PREPARATION
    // ========================================================================

    // Prepare combined worktime + timeoff display data for unified page
    @PreAuthorize("#user.username == authentication.name or hasRole('ADMIN')")
    public Map<String, Object> prepareCombinedDisplayData(User user, int year, int month) {
        validateInput(user, year, month);

        LoggerUtil.info(this.getClass(), String.format("Preparing combined display data for user %s, %d/%d", user.getUsername(), month, year));

        try {
            // Get month summary with SN overtime support
            WorkTimeSummaryDTO monthSummary = prepareMonthSummary(user, year, month);

            // Get recent time off history
            List<WorkTimeTable> recentTimeOff = getRecentTimeOffHistory(user.getUsername(), user.getUserId(), year);

            // Prepare display data map
            Map<String, Object> displayData = new HashMap<>();
            displayData.put("user", user);
            displayData.put("currentYear", year);
            displayData.put("currentMonth", month);
            displayData.put("monthSummary", monthSummary);
            displayData.put("recentTimeOff", recentTimeOff);

            LoggerUtil.info(this.getClass(), String.format("Combined display data prepared successfully: %d entries, %d overtime minutes total",
                    monthSummary.getEntries().size(), monthSummary.getTotalOvertimeMinutes()));

            return displayData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error preparing combined display data", e);
            throw e;
        }
    }

    // Process worktime data for display (enhanced)
    @PreAuthorize("#user.username == authentication.name or hasRole('ADMIN')")
    public Map<String, Object> prepareWorktimeDisplayData(User user, List<WorkTimeTable> worktimeData, int year, int month) {
        validateInput(user, worktimeData, year, month);

        LoggerUtil.info(this.getClass(), String.format("Preparing worktime display data for user %s, %d/%d", user.getUsername(), month, year));

        try {
            Map<String, Object> displayData = new HashMap<>();

            // Filter entries for display
            List<WorkTimeTable> displayableEntries = filterEntriesForDisplay(worktimeData);

            // Calculate summary
            WorkTimeSummary summary = calculateMonthSummary(displayableEntries, year, month, user);

            // Convert to DTOs with pre-calculated values AND status information
            int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8; // Default to 8 hours
            List<WorkTimeEntryDTO> entryDTOs = displayableEntries.stream()
                    .map(entry -> {
                        // CREATE status information for each entry
                        GeneralDataStatusDTO statusInfo = createStatusInfo(entry, user.getUserId(), entry.getUserId());
                        // PASS status info to factory method
                        return entryDTOFactory.fromWorkTimeTable(entry, userSchedule, statusInfo);
                    })
                    .collect(Collectors.toList());

            WorkTimeSummaryDTO summaryDTO = WorkTimeSummaryDTO.fromWorkTimeSummary(summary);

            // Prepare display data
            displayData.put("worktimeData", entryDTOs);
            displayData.put("currentYear", year);
            displayData.put("currentMonth", month);
            displayData.put("user", sanitizeUserData(user));
            displayData.put("summary", summaryDTO);

            LoggerUtil.info(this.getClass(), String.format("Prepared worktime display data with %d entries for user %s", entryDTOs.size(), user.getUsername()));

            return displayData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error preparing worktime display data for user %s: %s", user.getUsername(), e.getMessage()), e);
            throw new RuntimeException("Failed to prepare worktime display data", e);
        }
    }

    // ========================================================================
    // ADMIN DISPLAY METHODS
    // ========================================================================

    // ✅ REFACTORED: Prepare day headers for admin display
    // Delegates to DisplayDataPreparationService
    public List<Map<String, String>> prepareDayHeaders(YearMonth yearMonth) {
        return displayDataPreparationService.prepareDayHeaders(yearMonth);
    }

    // ✅ REFACTORED: Prepare worktime display data as DTOs for consistent frontend display
    // Delegates structure to DisplayDataPreparationService, provides DTO creation logic
    public Map<Integer, Map<LocalDate, WorkTimeDisplayDTO>> prepareDisplayDTOs(List<User> users, Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap, int year, int month) {
        // Delegate to preparation service with DTO creation logic injected
        return displayDataPreparationService.prepareDisplayDTOs(
            users,
            userEntriesMap,
            year,
            month,
            this::createDisplayDTO  // Method reference to DTO creation logic
        );
    }

    // Create appropriate display DTO based on entry type
    private WorkTimeDisplayDTO createDisplayDTO(WorkTimeTable entry, User user, LocalDate date, boolean isWeekend) {

        GeneralDataStatusDTO statusInfo = createStatusInfo(entry, user.getUserId(), user.getUserId());

        // No entry exists
        if (entry == null) {
            return displayDTOFactory.createEmpty(user.getUserId(), date, isWeekend, statusInfo);
        }

        // Skip entries that shouldn't be displayed (like USER_IN_PROCESS)
        if (!WorkTimeEntryUtil.isEntryDisplayable(entry)) {
            return displayDTOFactory.createEmpty(user.getUserId(), date, isWeekend, statusInfo);
        }

        int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8; // Default to 8 hours

        // Handle CR (Recovery Leave) - special case, works like a full day paid from overtime
        if (WorkCode.RECOVERY_LEAVE_CODE.equals(entry.getTimeOffType())) {
            return displayDTOFactory.createFromCREntry(entry, userSchedule, isWeekend, statusInfo);
        }

        // Handle CN (Unpaid Leave)
        if (WorkCode.UNPAID_LEAVE_CODE.equals(entry.getTimeOffType())) {
            return displayDTOFactory.createFromCNEntry(entry, isWeekend, statusInfo);
        }

        // Handle ZS (Short Day) - timeOffType is stored as "ZS-2", "ZS-4", etc.
        if (entry.getTimeOffType() != null && entry.getTimeOffType().startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
            return displayDTOFactory.createFromZSEntry(entry, userSchedule, isWeekend, statusInfo);
        }

        // Handle special day work entries (TYPE with overtime)
        if (entry.getTimeOffType() != null && entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {

            switch (entry.getTimeOffType()) {
                case WorkCode.NATIONAL_HOLIDAY_CODE:
                    return displayDTOFactory.createFromSNWorkEntry(entry, isWeekend, statusInfo);
                case WorkCode.TIME_OFF_CODE:
                    return displayDTOFactory.createFromCOWorkEntry(entry, isWeekend, statusInfo);
                case WorkCode.MEDICAL_LEAVE_CODE:
                    return displayDTOFactory.createFromCMWorkEntry(entry, isWeekend, statusInfo);
                case WorkCode.WEEKEND_CODE:
                    return displayDTOFactory.createFromWWorkEntry(entry, isWeekend, statusInfo);
                case WorkCode.SPECIAL_EVENT_CODE:
                    return displayDTOFactory.createFromCEWorkEntry(entry, isWeekend, statusInfo);
            }
        }

        // Regular time off (SN without work, CO, CM)
        if (entry.getTimeOffType() != null) {
            return displayDTOFactory.createFromTimeOffEntry(entry, isWeekend, statusInfo);
        }

        // Regular work entry
        if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            return displayDTOFactory.createFromWorkEntry(entry, userSchedule, isWeekend, statusInfo);
        }

        // Default to empty
        return displayDTOFactory.createEmpty(user.getUserId(), date, isWeekend, statusInfo);
    }

    // Calculate summary with verification against display DTOs
    public Map<Integer, WorkTimeSummary> calculateUserSummariesFromDTOs(Map<Integer, Map<LocalDate, WorkTimeDisplayDTO>> displayDTOs, List<User> users, Integer year, Integer month) {

        LoggerUtil.info(this.getClass(), String.format("Calculating summaries from display DTOs for %d users", users.size()));

        Map<Integer, WorkTimeSummary> summaries = new HashMap<>();
        int totalWorkDays = CalculateWorkHoursUtil.calculateWorkDays(year, month);

        for (User user : users) {
            Map<LocalDate, WorkTimeDisplayDTO> userDTOs = displayDTOs.get(user.getUserId());
            if (userDTOs == null) continue;

            // Calculate from DTOs to ensure consistency (pass user for schedule info)
            WorkTimeSummary summary = calculateSummaryFromDTOs(userDTOs, totalWorkDays, user);
            summaries.put(user.getUserId(), summary);

            LoggerUtil.debug(this.getClass(), String.format("User %s summary: %d work days, %d regular minutes, %d overtime minutes",
                    user.getName(), summary.getDaysWorked(), summary.getTotalRegularMinutes(), summary.getTotalOvertimeMinutes()));
        }

        return summaries;
    }

    // ✅ REFACTORED: Calculate summary from display DTOs to ensure consistency
    // Delegates to WorkTimeSummaryCalculator for all calculation logic
    private WorkTimeSummary calculateSummaryFromDTOs(Map<LocalDate, WorkTimeDisplayDTO> userDTOs, int totalWorkDays, User user) {
        return workTimeSummaryCalculator.calculateSummaryFromDTOs(userDTOs, totalWorkDays, user);
    }

    // Enhanced method for preparing model with display DTOs
    public void prepareWorkTimeModelWithDTOs(Model model, int year, int month, Integer selectedUserId, List<User> nonAdminUsers, Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap) {

        LoggerUtil.info(this.getClass(), String.format("Preparing worktime model with DTOs for %d/%d", month, year));

        // Create display DTOs from raw entries
        Map<Integer, Map<LocalDate, WorkTimeDisplayDTO>> displayDTOs = prepareDisplayDTOs(nonAdminUsers, userEntriesMap, year, month);

        // Calculate summaries from DTOs (ensures consistency)
        Map<Integer, WorkTimeSummary> summaries = calculateUserSummariesFromDTOs(displayDTOs, nonAdminUsers, year, month);

        // Add model attributes
        model.addAttribute("currentYear", year);
        model.addAttribute("currentMonth", month);
        model.addAttribute("users", nonAdminUsers);
        model.addAttribute("userSummaries", summaries);
        model.addAttribute("daysInMonth", YearMonth.of(year, month).lengthOfMonth());
        model.addAttribute("dayHeaders", prepareDayHeaders(YearMonth.of(year, month)));

        // NEW: Add display DTOs instead of raw entries
        model.addAttribute("userDisplayDTOs", displayDTOs);

        // Keep raw entries for editing functionality
        model.addAttribute("userEntriesMap", userEntriesMap);

        // Calculate entry counts from raw data (for statistics)
        Map<String, Long> entryCounts = calculateEntryCounts(userEntriesMap);
        model.addAttribute("entryCounts", entryCounts);

        // Handle selected user
        if (selectedUserId != null) {
            nonAdminUsers.stream().filter(user -> user.getUserId().equals(selectedUserId))
                    .findFirst().ifPresent(user -> {
                        model.addAttribute("selectedUserId", selectedUserId);
                        model.addAttribute("selectedUserName", user.getName());
                        model.addAttribute("selectedUserWorktime", userEntriesMap.get(selectedUserId));
                        model.addAttribute("selectedUserDisplayDTOs", displayDTOs.get(selectedUserId));
                    });
        }

        LoggerUtil.info(this.getClass(), "Successfully prepared worktime model with display DTOs");
    }

    // Calculate entry counts for statistics (moved from AdminWorkTimeController)
    // ✅ REFACTORED: Now uses TimeOffDayCounter for time-off counting
    private Map<String, Long> calculateEntryCounts(Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap) {
        Map<String, Long> counts = new HashMap<>();

        // Phase 1: Use TimeOffDayCounter instead of manual counting
        TimeOffDayCounter.TimeOffDayCounts timeOffCounts = timeOffDayCounter.countFromUserEntriesMap(userEntriesMap);
        counts.put("snCount", (long) timeOffCounts.getSnDays());
        counts.put("coCount", (long) timeOffCounts.getCoDays());  // Already includes CE
        counts.put("cmCount", (long) timeOffCounts.getCmDays());

        // Count by status (not yet extracted)
        List<WorkTimeTable> allEntries = userEntriesMap.values().stream().flatMap(map -> map.values().stream()).toList();
        counts.put("adminEditedCount", allEntries.stream().filter(e -> e.getAdminSync() != null && "ADMIN_EDITED".equals(e.getAdminSync())).count());
        counts.put("userInputCount", allEntries.stream().filter(e -> e.getAdminSync() != null && "USER_INPUT".equals(e.getAdminSync())).count());
        counts.put("syncedCount", allEntries.stream().filter(e -> e.getAdminSync() != null && "USER_DONE".equals(e.getAdminSync())).count());

        return counts;
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    // Prepare month summary with proper Special Day overtime inclusion
    public WorkTimeSummaryDTO prepareMonthSummary(User user, int year, int month) {
        validateInput(user, year, month);

        LoggerUtil.info(this.getClass(), String.format("Preparing month summary for user %s, %d/%d with SN overtime support", user.getUsername(), month, year));

        try {
            // Load worktime data using new service
            List<WorkTimeTable> worktimeData = worktimeCacheService.getMonthEntriesWithFallback(user.getUsername(), user.getUserId(), year, month);

            LoggerUtil.debug(this.getClass(), String.format("Loaded %d worktime entries for processing", worktimeData.size()));

            // Calculate work time counts (includes SN overtime)
            WorkTimeCountsDTO counts = calculateWorkTimeCounts(worktimeData, user);

            // Get holiday balance
            Integer holidayBalance = worktimeOperationService.getHolidayBalance(user.getUsername());

            // Convert to display entries with proper SN handling
            List<WorkTimeEntryDTO> displayEntries = convertToDisplayEntries(worktimeData, user);

            LoggerUtil.info(this.getClass(), String.format("Month summary completed: %d entries, %d regular minutes, %d overtime minutes (includes SN)",
                    displayEntries.size(), counts.getRegularMinutes(), counts.getOvertimeMinutes()));

            return WorkTimeSummaryDTO.createWithEntries(displayEntries, counts, holidayBalance);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error preparing month summary", e);
            throw e;
        }
    }

    // Convert WorkTimeTable entries to display DTOs with proper SN overtime handling
    private List<WorkTimeEntryDTO> convertToDisplayEntries(List<WorkTimeTable> worktimeData, User user) {
        LoggerUtil.debug(this.getClass(), "Converting worktime entries to display DTOs");

        return worktimeData.stream()
                .map(entry -> convertToDisplayEntry(entry, user))  // PASS USER
                .collect(Collectors.toList());
    }

    // Convert single WorkTimeTable to WorkTimeEntryDTO with all special day support
    private WorkTimeEntryDTO convertToDisplayEntry(WorkTimeTable entry, User user) {
        // Get actual user schedule (default to 8 if not available)
        int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8;
        GeneralDataStatusDTO statusInfo = createStatusInfo(entry, null, entry.getUserId());

        // Use the enhanced DTO conversion method
        WorkTimeEntryDTO dto = entryDTOFactory.fromWorkTimeTable(entry, userSchedule, statusInfo);

        LoggerUtil.debug(this.getClass(), String.format("Converted entry for %s: timeOff=%s, workedMinutes=%d, overtimeMinutes=%d, isSpecialDayWork=%b, specialDayType=%s, Status: %s",
                entry.getWorkDate(), entry.getTimeOffType(), entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0, dto.isSpecialDayWithWork(),
                dto.getSpecialDayType(),statusInfo.getFullDescription()));

        return dto;
    }

    // Filter entries for display (hide certain statuses)
    private List<WorkTimeTable> filterEntriesForDisplay(List<WorkTimeTable> entries) {
        return entries.stream().filter(WorkTimeEntryUtil::isEntryDisplayable).map(this::prepareEntryForDisplay)
                .sorted(Comparator.comparing(WorkTimeTable::getWorkDate)).toList();
    }

    // Prepare individual entry for display
    private WorkTimeTable prepareEntryForDisplay(WorkTimeTable entry) {
        WorkTimeTable displayEntry = WorkTimeEntryUtil.copyWorkTimeEntry(entry);

        // For USER_IN_PROCESS entries, show limited information
        if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
            // Keep available information but hide incomplete data
            if (displayEntry.getTotalWorkedMinutes() == null || displayEntry.getTotalWorkedMinutes() == 0) {
                displayEntry.setTotalWorkedMinutes(null);
            }

            if (displayEntry.getTotalOvertimeMinutes() == null || displayEntry.getTotalOvertimeMinutes() == 0) {
                displayEntry.setTotalOvertimeMinutes(null);
            }

            // Always hide end time for in-process entries
            displayEntry.setDayEndTime(null);

            // Don't apply lunch break unless explicitly set
            if (!displayEntry.isLunchBreakDeducted()) {
                displayEntry.setLunchBreakDeducted(false);
            }
        }

        return displayEntry;
    }

    // ✅ REFACTORED: Calculate month summary with proper special day overtime inclusion
    // Delegates to WorkTimeSummaryCalculator for all calculation logic
    private WorkTimeSummary calculateMonthSummary(List<WorkTimeTable> displayableEntries, int year, int month, User user) {
        return workTimeSummaryCalculator.calculateMonthSummary(displayableEntries, year, month, user);
    }

    // ✅ REFACTORED: Calculate work time counts with proper special day overtime inclusion for ALL types
    // Uses Phase 1 specialized counters and calculators
    private WorkTimeCountsDTO calculateWorkTimeCounts(List<WorkTimeTable> worktimeData, User user) {
        WorkTimeCountsDTO counts = new WorkTimeCountsDTO();
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalDiscardedMinutes = 0;
        int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8;

        LoggerUtil.debug(this.getClass(), String.format("Calculating work time counts for %d entries", worktimeData.size()));

        // Phase 1: Use specialized counters for day counting
        TimeOffDayCounter.TimeOffDayCounts timeOffCounts = timeOffDayCounter.countFromEntries(worktimeData);
        counts.setSnDays(timeOffCounts.getSnDays());
        counts.setCoDays(timeOffCounts.getCoDays());  // Already includes CE
        counts.setCmDays(timeOffCounts.getCmDays());

        int daysWorked = workDayCounter.countFromEntries(worktimeData);
        counts.setDaysWorked(daysWorked);

        // Calculate time totals (this is specific logic for raw time calculation)
        int scheduleMinutes = userSchedule * 60;
        for (WorkTimeTable entry : worktimeData) {
            // Skip in-process entries
            if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                LoggerUtil.debug(this.getClass(), String.format("Skipping in-process entry for %s", entry.getWorkDate()));
                continue;
            }

            // Regular work entry (no time off type)
            if (entry.getTimeOffType() == null && entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                // Use calculation utility for consistency
                WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(entry.getTotalWorkedMinutes(), userSchedule);
                int discardedForEntry = CalculateWorkHoursUtil.calculateDiscardedMinutes(entry.getTotalWorkedMinutes(), userSchedule);
                totalRegularMinutes += result.getProcessedMinutes();
                totalOvertimeMinutes += result.getOvertimeMinutes();
                totalDiscardedMinutes += discardedForEntry;
                LoggerUtil.debug(this.getClass(), String.format("Regular work entry %s: %d minutes processed, %d overtime", entry.getWorkDate(), result.getProcessedMinutes(), result.getOvertimeMinutes()));
            }

            // Handle ZS (Short Day) entries: contribute the FULL SCHEDULE to regular
            // ZS represents a complete work day filled from overtime, so it counts as full schedule
            // The deduction calculator will subtract the ZS value from overtime
            // NOTE: We add FULL schedule here, NOT the worked portion, to match admin DTO logic
            if (entry.getTimeOffType() != null && entry.getTimeOffType().startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
                // ZS entries: Add FULL schedule (matching DTO logic)
                totalRegularMinutes += scheduleMinutes;
                LoggerUtil.debug(this.getClass(), String.format("ZS entry %s: added %d minutes (full schedule) to regular", entry.getWorkDate(), scheduleMinutes));
            }

            // NOTE: CR is handled entirely by the deduction calculator (adds full schedule)
            // NOTE: CO/CM/SN without work contribute 0 (they're time off, not work days)
            // NOTE: CO/CM/SN WITH work contribute to overtime only (special day overtime)

            // Handle ALL special day types with overtime work (SN/CO/CM/W/CE)
            if (isSpecialDayType(entry.getTimeOffType()) && entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
                // Special day overtime goes directly to overtime totals (no regular minutes)
                totalOvertimeMinutes += entry.getTotalOvertimeMinutes();
                LoggerUtil.info(this.getClass(), String.format("Added %s overtime: %d minutes for date %s (total overtime: %d)", entry.getTimeOffType(), entry.getTotalOvertimeMinutes(), entry.getWorkDate(), totalOvertimeMinutes));
            }
        }

        // Phase 1: Use OvertimeDeductionCalculator for CR/ZS deductions
        OvertimeDeductionCalculator.DeductionResult deductions = overtimeDeductionCalculator.calculateForUser(worktimeData, user);

        // Calculate adjusted overtime and regular time
        // IMPORTANT: Only add CR deductions to regular (ZS already added full schedule in loop above)
        // Both CR and ZS deductions subtract from overtime
        int adjustedRegularMinutes = totalRegularMinutes + deductions.getCrDeductions();  // Only CR!
        int adjustedOvertimeMinutes = totalOvertimeMinutes - deductions.getTotalDeductions();  // CR + ZS

        // Set calculated totals
        counts.setRegularMinutes(adjustedRegularMinutes);
        counts.setOvertimeMinutes(adjustedOvertimeMinutes);
        counts.setDiscardedMinutes(totalDiscardedMinutes);

        LoggerUtil.info(this.getClass(), String.format("Work time counts calculated: totalRegular=%d (adjusted: %d), totalOvertime=%d (adjusted: %d), deductions=%s",
                totalRegularMinutes, adjustedRegularMinutes, totalOvertimeMinutes, adjustedOvertimeMinutes, deductions.getDescription()));

        return counts;
    }

    // Get recent time off history
    private List<WorkTimeTable> getRecentTimeOffHistory(String username, Integer userId, int year) {
        try {
            // Get current year data from cache
            List<WorkTimeTable> currentYearTimeOff = timeOffCacheService.getUpcomingTimeOff(username, userId, year);

            return currentYearTimeOff.stream()
                    .filter(entry -> !entry.getWorkDate().isAfter(LocalDate.now()))
                    .sorted(Comparator.comparing(WorkTimeTable::getWorkDate).reversed())
                    .limit(10) // Last 10 entries
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error getting recent time off history for %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Validate input parameters
    private void validateInput(User user, int year, int month) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }
    }

    // Validate input with worktime data
    private void validateInput(User user, List<WorkTimeTable> worktimeData, int year, int month) {
        validateInput(user, year, month);

        if (worktimeData == null) {
            throw new IllegalArgumentException("Worktime data cannot be null");
        }

        if (worktimeData.stream().anyMatch(entry -> !entry.getUserId().equals(user.getUserId()))) {
            throw new SecurityException("Worktime data contains entries for other users");
        }
    }

    // Sanitize user data for display
    private User sanitizeUserData(User user) {
        User sanitized = new User();
        sanitized.setUserId(user.getUserId());
        sanitized.setName(user.getName());
        sanitized.setUsername(user.getUsername());
        sanitized.setEmployeeId(user.getEmployeeId());
        sanitized.setSchedule(user.getSchedule());
        sanitized.setPaidHolidayDays(user.getPaidHolidayDays());
        return sanitized;
    }

    // ✅ REFACTORED: Get detailed entry information for a specific user and date
    // Used by admin worktime API to provide rich entry details
    // Delegates response building to EntryDetailResponseBuilder
    public Map<String, Object> getEntryDetails(Integer userId, Integer year, Integer month, Integer day) {
        // Validate date parameters
        LocalDate date;
        try {
            date = LocalDate.of(year, month, day);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date parameters: " + e.getMessage());
        }

        // Validate user exists
        Optional<User> userOpt = allUsersCacheService.getUserByIdAsUserObject(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }

        User user = userOpt.get();

        // Use the SAME admin worktime file loading as the admin view
        List<WorkTimeTable> viewableEntries = worktimeOperationService.getViewableEntries(year, month);
        Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap = convertToUserEntriesMap(viewableEntries);

        // Get the specific user's entries from the admin worktime data
        Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.get(userId);
        WorkTimeTable entry = userEntries != null ? userEntries.get(date) : null;

        // Phase 2: Delegate response building to EntryDetailResponseBuilder
        Map<String, Object> response = responseBuilder.buildDetailedResponse(user, date, entry);

        if (entry == null) {
            LoggerUtil.debug(this.getClass(), String.format("No entry found for user %d (%s) on %s in admin worktime file", userId, user.getName(), date));
        } else {
            LoggerUtil.debug(this.getClass(), String.format("Retrieved entry details for user %d (%s) on %s from admin worktime file: %s",
                    userId, user.getName(), date, entry.getTimeOffType() != null ? entry.getTimeOffType() : "work"));
        }

        return response;
    }

    private Map<Integer, Map<LocalDate, WorkTimeTable>> convertToUserEntriesMap(List<WorkTimeTable> viewableEntries) {
        Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap = new HashMap<>();

        for (WorkTimeTable entry : viewableEntries) {
            Integer userId = entry.getUserId();
            LocalDate workDate = entry.getWorkDate();

            // Get or create user's entry map
            Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.computeIfAbsent(userId, k -> new HashMap<>());

            // Add entry to user's map
            userEntries.put(workDate, entry);
        }

        LoggerUtil.debug(this.getClass(), String.format("Converted %d admin worktime entries to user entries map for %d users", viewableEntries.size(), userEntriesMap.size()));

        return userEntriesMap;
    }

    // Build response for cases where no entry exists
    // ✅ REFACTORED: All response building and label mapping methods extracted to:
    // - EntryDetailResponseBuilder (buildNoEntryResponse, buildDetailedEntryResponse, addXXX methods)
    // - TimeOffLabelMapper (getTimeOffLabel, getStatusLabel, getStatusClass, isSpecialDayType)

    // Helper method to check if a time off type is a special day type
    // Special day types can have overtime work (e.g., SN:5, CO:5, CE:5)
    // Delegates to TimeOffLabelMapper for consistency
    private boolean isSpecialDayType(String timeOffType) {
        return labelMapper.isSpecialDayType(timeOffType);
    }

    // Create status information using StatusDTOConverter
    private GeneralDataStatusDTO createStatusInfo(WorkTimeTable entry, Integer currentUserId, Integer entryUserId) {
        if (entry == null) {
            return GeneralDataStatusDTO.createUnknown();
        }

        String rawStatus = entry.getAdminSync();
        return statusDTOConverter.convertToDTO(rawStatus, currentUserId, entryUserId);
    }
}