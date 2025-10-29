package com.ctgraphdep.worktime.display;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.User;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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

    @Autowired
    private StatusDTOConverter statusDTOConverter;

    @Autowired
    public WorktimeDisplayService(WorktimeOperationService worktimeOperationService,
                                 TimeOffCacheService timeOffCacheService,
                                 WorktimeCacheService worktimeCacheService,
                                 AllUsersCacheService allUsersCacheService,
                                 WorkTimeDisplayDTOFactory displayDTOFactory,
                                 WorkTimeEntryDTOFactory entryDTOFactory) {
        this.worktimeOperationService = worktimeOperationService;
        this.timeOffCacheService = timeOffCacheService;
        this.worktimeCacheService = worktimeCacheService;
        this.allUsersCacheService = allUsersCacheService;
        this.displayDTOFactory = displayDTOFactory;
        this.entryDTOFactory = entryDTOFactory;
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

    // Prepare day headers for admin display
    public List<Map<String, String>> prepareDayHeaders(YearMonth yearMonth) {
        WorkTimeEntryUtil.validateYearMonth(yearMonth);

        List<Map<String, String>> dayHeaders = new ArrayList<>();

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            Map<String, String> headerInfo = new HashMap<>();

            headerInfo.put("day", String.valueOf(day));
            headerInfo.put("initial", WorkCode.ROMANIAN_DAY_INITIALS.get(date.getDayOfWeek()));
            headerInfo.put("isWeekend", WorkTimeEntryUtil.isDateWeekend(date) ? "true" : "false");
            dayHeaders.add(headerInfo);
        }

        LoggerUtil.debug(this.getClass(), String.format(
                "Prepared %d day headers for %s", dayHeaders.size(), yearMonth));

        return dayHeaders;
    }

    // NEW: Prepare worktime display data as DTOs for consistent frontend display
    public Map<Integer, Map<LocalDate, WorkTimeDisplayDTO>> prepareDisplayDTOs(List<User> users, Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap, int year, int month) {

        LoggerUtil.info(this.getClass(), String.format("Preparing display DTOs for %d users in %d/%d", users.size(), month, year));

        Map<Integer, Map<LocalDate, WorkTimeDisplayDTO>> displayDTOs = new HashMap<>();
        YearMonth yearMonth = YearMonth.of(year, month);

        for (User user : users) {
            Map<LocalDate, WorkTimeDisplayDTO> userDisplayMap = new TreeMap<>();
            Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.getOrDefault(user.getUserId(), new HashMap<>());

            // Process each day of the month
            for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
                LocalDate date = yearMonth.atDay(day);
                WorkTimeTable entry = userEntries.get(date);
                boolean isWeekend = WorkTimeEntryUtil.isDateWeekend(date);

                WorkTimeDisplayDTO displayDTO = createDisplayDTO(entry, user, date, isWeekend);
                userDisplayMap.put(date, displayDTO);
            }

            displayDTOs.put(user.getUserId(), userDisplayMap);

            LoggerUtil.debug(this.getClass(), String.format("Created %d display DTOs for user %s", userDisplayMap.size(), user.getName()));
        }

        LoggerUtil.info(this.getClass(), String.format("Successfully prepared display DTOs for %d users", users.size()));

        return displayDTOs;
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
            return displayDTOFactory.createFromCREntry(entry, isWeekend, statusInfo);
        }

        // Handle CN (Unpaid Leave)
        if (WorkCode.UNPAID_LEAVE_CODE.equals(entry.getTimeOffType())) {
            return displayDTOFactory.createFromCNEntry(entry, isWeekend, statusInfo);
        }

        // Handle ZS (Short Day)
        if (WorkCode.SHORT_DAY_CODE.equals(entry.getTimeOffType())) {
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

    // Calculate summary from display DTOs to ensure consistency
    private WorkTimeSummary calculateSummaryFromDTOs(Map<LocalDate, WorkTimeDisplayDTO> userDTOs, int totalWorkDays, User user) {
        int daysWorked = 0;
        int snDays = 0;
        int coDays = 0;
        int cmDays = 0;
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;

        for (WorkTimeDisplayDTO dto : userDTOs.values()) {
            if (!dto.isHasEntry()) continue;

            // Count time off days (SN, CO, CM, CE)
            if (dto.isTimeOff()) {
                if (dto.getRawEntry() != null) {
                    switch (dto.getRawEntry().getTimeOffType()) {
                        case WorkCode.NATIONAL_HOLIDAY_CODE -> snDays++;
                        case WorkCode.TIME_OFF_CODE -> coDays++;
                        case WorkCode.MEDICAL_LEAVE_CODE -> cmDays++;
                        case WorkCode.SPECIAL_EVENT_CODE -> coDays++;  // CE counts as CO for display totals
                    }
                }
            }

            // Count work days (including regular work, ZS, CR, D) - same logic as user time management
            boolean isWorkDay = false;
            WorkTimeTable rawEntry = dto.getRawEntry();

            if (rawEntry != null) {
                // Regular work entry (no time off type)
                if (rawEntry.getTimeOffType() == null && rawEntry.getTotalWorkedMinutes() != null && rawEntry.getTotalWorkedMinutes() > 0) {
                    isWorkDay = true;
                }

                // ZS (Short Day) - work day paid partially from overtime
                if (rawEntry.getTimeOffType() != null && rawEntry.getTimeOffType().startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
                    isWorkDay = true;
                }

                // CR (Recovery Leave) - work day paid from overtime
                if (WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(rawEntry.getTimeOffType())) {
                    isWorkDay = true;
                }

                // D (Delegation) - normal work day with special form
                if ("D".equalsIgnoreCase(rawEntry.getTimeOffType())) {
                    isWorkDay = true;
                }
            }

            if (isWorkDay) {
                daysWorked++;
            }

            // Add contributed minutes (pre-calculated by DTO)
            if (dto.getContributedRegularMinutes() != null) {
                totalRegularMinutes += dto.getContributedRegularMinutes();
            }
            if (dto.getContributedOvertimeMinutes() != null) {
                totalOvertimeMinutes += dto.getContributedOvertimeMinutes();
            }
        }

        // Calculate CR/ZS deductions (same logic as user time management)
        int pendingCRDeductions = 0;
        int pendingZSDeductions = 0;
        int crCount = 0;
        int zsCount = 0;

        for (WorkTimeDisplayDTO dto : userDTOs.values()) {
            if (!dto.isHasEntry()) continue;

            WorkTimeTable entry = dto.getRawEntry();
            if (entry == null) continue;

            // Skip in-process entries
            if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                continue;
            }

            // Calculate CR deductions: each CR deducts full schedule hours
            if (WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(entry.getTimeOffType())) {
                int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8;
                int scheduleMinutes = userSchedule * 60;
                pendingCRDeductions += scheduleMinutes;
                crCount++;
            }

            // Calculate ZS deductions: parse hours from "ZS-X" format
            if (entry.getTimeOffType() != null && entry.getTimeOffType().startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
                try {
                    // Parse "ZS-5" → extract "5"
                    String[] parts = entry.getTimeOffType().split("-");
                    if (parts.length == 2) {
                        int missingHours = Integer.parseInt(parts[1]);
                        int deductionMinutes = missingHours * 60;
                        pendingZSDeductions += deductionMinutes;
                        zsCount++;
                    }
                } catch (NumberFormatException e) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Failed to parse ZS hours from %s: %s",
                            entry.getTimeOffType(), e.getMessage()));
                }
            }
        }

        // Calculate adjusted overtime and regular time (move overtime → regular for CR/ZS)
        int totalPendingDeductions = pendingCRDeductions + pendingZSDeductions;
        int adjustedRegularMinutes = totalRegularMinutes + totalPendingDeductions;  // Add deductions to regular
        int adjustedOvertimeMinutes = totalOvertimeMinutes - totalPendingDeductions;  // Subtract from overtime

        int remainingWorkDays = totalWorkDays - (daysWorked + snDays + coDays + cmDays);

        LoggerUtil.debug(this.getClass(), String.format(
                "Admin summary for user %s: daysWorked=%d (includes CR/ZS/D), regular=%d (adjusted: %d), overtime=%d (adjusted: %d), pendingCR=%d (%d entries), pendingZS=%d (%d entries)",
                user.getName(), daysWorked, totalRegularMinutes, adjustedRegularMinutes, totalOvertimeMinutes, adjustedOvertimeMinutes,
                pendingCRDeductions, crCount, pendingZSDeductions, zsCount));

        return WorkTimeSummary.builder()
                .totalWorkDays(totalWorkDays)
                .daysWorked(daysWorked)
                .remainingWorkDays(remainingWorkDays)
                .snDays(snDays)
                .coDays(coDays)
                .cmDays(cmDays)
                .totalRegularMinutes(adjustedRegularMinutes)  // Use adjusted regular time (includes CR/ZS from overtime)
                .totalOvertimeMinutes(adjustedOvertimeMinutes)  // Use adjusted overtime with deductions
                .build();
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
    private Map<String, Long> calculateEntryCounts(Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap) {
        Map<String, Long> counts = new HashMap<>();

        List<WorkTimeTable> allEntries = userEntriesMap.values().stream().flatMap(map -> map.values().stream()).toList();

        // Count time off types (CE counts with CO for display totals)
        counts.put("snCount", allEntries.stream().filter(e -> WorkCode.NATIONAL_HOLIDAY_CODE.equals(e.getTimeOffType())).count());
        counts.put("coCount", allEntries.stream().filter(e ->
            WorkCode.TIME_OFF_CODE.equals(e.getTimeOffType()) ||
            WorkCode.SPECIAL_EVENT_CODE.equals(e.getTimeOffType())).count());  // CO + CE
        counts.put("cmCount", allEntries.stream().filter(e -> WorkCode.MEDICAL_LEAVE_CODE.equals(e.getTimeOffType())).count());

        // Count by status
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

    // Calculate month summary with proper special day overtime inclusion
    private WorkTimeSummary calculateMonthSummary(List<WorkTimeTable> displayableEntries, int year, int month, User user) {
        // Get total work days in month (excluding weekends)
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        int totalWorkDays = (int) firstDay.datesUntil(lastDay.plusDays(1))
                .filter(date -> date.getDayOfWeek().getValue() < 6) // Exclude weekends
                .count();

        // Initialize counters
        int daysWorked = 0;
        int snDays = 0;
        int coDays = 0;
        int cmDays = 0;
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalDiscardedMinutes = 0;

        LoggerUtil.debug(this.getClass(), String.format(
                "Calculating month summary for %d/%d: %d total work days", month, year, totalWorkDays));

        for (WorkTimeTable entry : displayableEntries) {
            // Skip in-process entries
            if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                continue;
            }

            // Count time off days (CE counts with CO)
            if (entry.getTimeOffType() != null) {
                switch (entry.getTimeOffType()) {
                    case WorkCode.NATIONAL_HOLIDAY_CODE -> snDays++;
                    case WorkCode.TIME_OFF_CODE -> coDays++;
                    case WorkCode.MEDICAL_LEAVE_CODE -> cmDays++;
                    case WorkCode.SPECIAL_EVENT_CODE -> coDays++;  // CE counts as CO for display totals
                }
            }

            // Count work days (including regular work, ZS, CR, D)
            boolean isWorkDay = false;

            // Regular work entry (no time off type)
            if (entry.getTimeOffType() == null && entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                isWorkDay = true;

                // Use default schedule
                int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8;
                int discardedForEntry = CalculateWorkHoursUtil.calculateDiscardedMinutes(entry.getTotalWorkedMinutes(), userSchedule);

                WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(entry.getTotalWorkedMinutes(), userSchedule);
                totalRegularMinutes += result.getProcessedMinutes();
                totalOvertimeMinutes += result.getOvertimeMinutes();
                totalDiscardedMinutes += discardedForEntry;

                LoggerUtil.debug(this.getClass(), String.format(
                        "Regular work entry %s: %d raw minutes, %d processed, %d overtime, %d discarded",
                        entry.getWorkDate(), entry.getTotalWorkedMinutes(),
                        result.getProcessedMinutes(), result.getOvertimeMinutes(), discardedForEntry));
            }

            // ZS (Short Day) - work day paid partially from overtime
            if (entry.getTimeOffType() != null && entry.getTimeOffType().startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
                isWorkDay = true;
                LoggerUtil.debug(this.getClass(), String.format("ZS work day counted: %s", entry.getWorkDate()));
            }

            // CR (Recovery Leave) - work day paid from overtime
            if (WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(entry.getTimeOffType())) {
                isWorkDay = true;
                LoggerUtil.debug(this.getClass(), String.format("CR work day counted: %s", entry.getWorkDate()));
            }

            // D (Delegation) - normal work day with special form
            if ("D".equalsIgnoreCase(entry.getTimeOffType())) {
                isWorkDay = true;
                LoggerUtil.debug(this.getClass(), String.format("D work day counted: %s", entry.getWorkDate()));
            }

            if (isWorkDay) {
                daysWorked++;
            }

            // ENHANCED: Handle ALL special day types with overtime work
            if (isSpecialDayType(entry.getTimeOffType()) && entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {

                totalOvertimeMinutes += entry.getTotalOvertimeMinutes();

                LoggerUtil.debug(this.getClass(), String.format("Added %s overtime: %d minutes for %s", entry.getTimeOffType(), entry.getTotalOvertimeMinutes(), entry.getWorkDate()));
            }
        }

        // Calculate remaining work days
        int remainingWorkDays = totalWorkDays - (daysWorked + snDays + coDays + cmDays);

        // Real-time calculation of pending CR/ZS deductions (per spec)
        int pendingCRDeductions = 0;
        int pendingZSDeductions = 0;
        int crCount = 0;
        int zsCount = 0;

        for (WorkTimeTable entry : displayableEntries) {
            // Skip in-process entries
            if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                continue;
            }

            // Calculate CR deductions: each CR deducts full schedule hours
            if (WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(entry.getTimeOffType())) {
                int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8;
                int scheduleMinutes = userSchedule * 60;
                pendingCRDeductions += scheduleMinutes;
                crCount++;
                LoggerUtil.debug(this.getClass(), String.format(
                        "CR entry on %s: deducting %d minutes from overtime",
                        entry.getWorkDate(), scheduleMinutes));
            }

            // Calculate ZS deductions: parse hours from "ZS-X" format
            if (entry.getTimeOffType() != null && entry.getTimeOffType().startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
                try {
                    // Parse "ZS-5" → extract "5"
                    String[] parts = entry.getTimeOffType().split("-");
                    if (parts.length == 2) {
                        int missingHours = Integer.parseInt(parts[1]);
                        int deductionMinutes = missingHours * 60;

                        pendingZSDeductions += deductionMinutes;
                        zsCount++;

                        LoggerUtil.debug(this.getClass(), String.format(
                                "ZS entry on %s: %s → deducting %d hours (%d min)",
                                entry.getWorkDate(), entry.getTimeOffType(), missingHours, deductionMinutes));
                    } else {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Invalid ZS format on %s: %s (expected ZS-X)",
                                entry.getWorkDate(), entry.getTimeOffType()));
                    }
                } catch (NumberFormatException e) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Failed to parse ZS hours from %s: %s",
                            entry.getTimeOffType(), e.getMessage()));
                }
            }
        }

        // Calculate adjusted overtime and regular time (real-time display with pending deductions)
        // CR/ZS deductions are moved from overtime → regular time (reclassification, not loss)
        int totalPendingDeductions = pendingCRDeductions + pendingZSDeductions;
        int adjustedRegularMinutes = totalRegularMinutes + totalPendingDeductions;  // Add deductions to regular
        int adjustedOvertimeMinutes = totalOvertimeMinutes - totalPendingDeductions;  // Subtract from overtime

        LoggerUtil.info(this.getClass(), String.format(
                "Month summary calculated: totalWorkDays=%d, daysWorked=%d, snDays=%d, coDays=%d, cmDays=%d, remainingWorkDays=%d, totalRegular=%d (adjusted: %d), totalOvertime=%d (adjusted: %d), pendingCR=%d (%d entries), pendingZS=%d (%d entries)",
                totalWorkDays, daysWorked, snDays, coDays, cmDays, remainingWorkDays, totalRegularMinutes, adjustedRegularMinutes, totalOvertimeMinutes, adjustedOvertimeMinutes,
                pendingCRDeductions, crCount, pendingZSDeductions, zsCount));

        return WorkTimeSummary.builder()
                .totalWorkDays(totalWorkDays)
                .daysWorked(daysWorked)
                .remainingWorkDays(remainingWorkDays)
                .snDays(snDays)
                .coDays(coDays)
                .cmDays(cmDays)
                .totalRegularMinutes(adjustedRegularMinutes)  // Use adjusted regular time (includes CR/ZS from overtime)
                .totalOvertimeMinutes(adjustedOvertimeMinutes)  // Use adjusted overtime with real-time deductions
                .totalMinutes(adjustedRegularMinutes + adjustedOvertimeMinutes)  // Total remains consistent
                .discardedMinutes(totalDiscardedMinutes)
                .build();
    }

    // Calculate work time counts with proper special day overtime inclusion for ALL types
    private WorkTimeCountsDTO calculateWorkTimeCounts(List<WorkTimeTable> worktimeData, User user) {
        WorkTimeCountsDTO counts = new WorkTimeCountsDTO();
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalDiscardedMinutes = 0;

        LoggerUtil.debug(this.getClass(), String.format("Calculating work time counts for %d entries", worktimeData.size()));

        for (WorkTimeTable entry : worktimeData) {
            // Skip in-process entries
            if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                LoggerUtil.debug(this.getClass(), String.format("Skipping in-process entry for %s", entry.getWorkDate()));
                continue;
            }

            // Handle time off types (for day counting - CE counts with CO)
            if (entry.getTimeOffType() != null) {
                switch (entry.getTimeOffType()) {
                    case WorkCode.NATIONAL_HOLIDAY_CODE -> {
                        counts.incrementSnDays();
                        LoggerUtil.debug(this.getClass(), String.format("Found SN day: %s", entry.getWorkDate()));
                    }
                    case WorkCode.TIME_OFF_CODE -> {
                        counts.incrementCoDays();
                        LoggerUtil.debug(this.getClass(), String.format("Found CO day: %s", entry.getWorkDate()));
                    }
                    case WorkCode.MEDICAL_LEAVE_CODE -> {
                        counts.incrementCmDays();
                        LoggerUtil.debug(this.getClass(), String.format("Found CM day: %s", entry.getWorkDate()));
                    }
                    case WorkCode.SPECIAL_EVENT_CODE -> {
                        counts.incrementCoDays();  // CE counts as CO for display totals
                        LoggerUtil.debug(this.getClass(), String.format("Found CE day (counted as CO): %s", entry.getWorkDate()));
                    }
                    case WorkCode.WEEKEND_CODE -> {
                        // Count weekend work days if they have overtime
                        if (entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
                            LoggerUtil.debug(this.getClass(), String.format("Found W work day: %s", entry.getWorkDate()));
                        }
                    }
                }
            }

            // Handle regular work entries and work days (including ZS, CR, D)
            boolean isWorkDay = false;

            // Regular work entry (no time off type)
            if (entry.getTimeOffType() == null && entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                isWorkDay = true;

                // Use default schedule if not available
                int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8;

                // Use calculation utility for consistency
                WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(entry.getTotalWorkedMinutes(), userSchedule);
                int discardedForEntry = CalculateWorkHoursUtil.calculateDiscardedMinutes(entry.getTotalWorkedMinutes(), userSchedule);
                totalRegularMinutes += result.getProcessedMinutes();
                totalOvertimeMinutes += result.getOvertimeMinutes();
                totalDiscardedMinutes += discardedForEntry;
                LoggerUtil.debug(this.getClass(), String.format("Regular work entry %s: %d minutes processed, %d overtime", entry.getWorkDate(), result.getProcessedMinutes(), result.getOvertimeMinutes()));
            }

            // ZS (Short Day) - work day paid partially from overtime
            if (entry.getTimeOffType() != null && entry.getTimeOffType().startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
                isWorkDay = true;
                LoggerUtil.debug(this.getClass(), String.format("ZS work day counted: %s", entry.getWorkDate()));
            }

            // CR (Recovery Leave) - work day paid from overtime
            if (WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(entry.getTimeOffType())) {
                isWorkDay = true;
                LoggerUtil.debug(this.getClass(), String.format("CR work day counted: %s", entry.getWorkDate()));
            }

            // D (Delegation) - normal work day with special form
            if ("D".equalsIgnoreCase(entry.getTimeOffType())) {
                isWorkDay = true;
                LoggerUtil.debug(this.getClass(), String.format("D work day counted: %s", entry.getWorkDate()));
            }

            if (isWorkDay) {
                counts.incrementDaysWorked();
            }

            // ENHANCED: Handle ALL special day types with overtime work (SN/CO/CM/W)
            if (isSpecialDayType(entry.getTimeOffType()) && entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {

                // Special day overtime goes directly to overtime totals (no regular minutes)
                totalOvertimeMinutes += entry.getTotalOvertimeMinutes();

                LoggerUtil.info(this.getClass(), String.format("Added %s overtime: %d minutes for date %s (total overtime: %d)", entry.getTimeOffType(), entry.getTotalOvertimeMinutes(), entry.getWorkDate(), totalOvertimeMinutes));
            }
        }

        // Real-time calculation of pending CR/ZS deductions (same as calculateMonthSummary)
        int pendingCRDeductions = 0;
        int pendingZSDeductions = 0;
        int crCount = 0;
        int zsCount = 0;

        for (WorkTimeTable entry : worktimeData) {
            // Skip in-process entries
            if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                continue;
            }

            // Calculate CR deductions: each CR deducts full schedule hours
            if (WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(entry.getTimeOffType())) {
                int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8;
                int scheduleMinutes = userSchedule * 60;
                pendingCRDeductions += scheduleMinutes;
                crCount++;
                LoggerUtil.debug(this.getClass(), String.format(
                        "CR entry on %s: deducting %d minutes from overtime",
                        entry.getWorkDate(), scheduleMinutes));
            }

            // Calculate ZS deductions: parse hours from "ZS-X" format
            if (entry.getTimeOffType() != null && entry.getTimeOffType().startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
                try {
                    // Parse "ZS-5" → extract "5"
                    String[] parts = entry.getTimeOffType().split("-");
                    if (parts.length == 2) {
                        int missingHours = Integer.parseInt(parts[1]);
                        int deductionMinutes = missingHours * 60;

                        pendingZSDeductions += deductionMinutes;
                        zsCount++;

                        LoggerUtil.debug(this.getClass(), String.format(
                                "ZS entry on %s: %s → deducting %d hours (%d min)",
                                entry.getWorkDate(), entry.getTimeOffType(), missingHours, deductionMinutes));
                    } else {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Invalid ZS format on %s: %s (expected ZS-X)",
                                entry.getWorkDate(), entry.getTimeOffType()));
                    }
                } catch (NumberFormatException e) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Failed to parse ZS hours from %s: %s",
                            entry.getTimeOffType(), e.getMessage()));
                }
            }
        }

        // Calculate adjusted overtime and regular time (real-time display with pending deductions)
        // CR/ZS deductions are moved from overtime → regular time (reclassification, not loss)
        int totalPendingDeductions = pendingCRDeductions + pendingZSDeductions;
        int adjustedRegularMinutes = totalRegularMinutes + totalPendingDeductions;  // Add deductions to regular
        int adjustedOvertimeMinutes = totalOvertimeMinutes - totalPendingDeductions;  // Subtract from overtime

        // Set calculated totals
        counts.setRegularMinutes(adjustedRegularMinutes);  // Use adjusted regular time (includes CR/ZS from overtime)
        counts.setOvertimeMinutes(adjustedOvertimeMinutes);  // Use adjusted overtime with deductions
        counts.setDiscardedMinutes(totalDiscardedMinutes);

        LoggerUtil.info(this.getClass(), String.format("Work time counts calculated: totalRegular=%d (adjusted: %d), totalOvertime=%d (adjusted: %d), pendingCR=%d (%d entries), pendingZS=%d (%d entries)",
                totalRegularMinutes, adjustedRegularMinutes, totalOvertimeMinutes, adjustedOvertimeMinutes, pendingCRDeductions, crCount, pendingZSDeductions, zsCount));

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

    // Get detailed entry information for a specific user and date
    // Used by admin worktime API to provide rich entry details
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

        // FIXED: Use the SAME admin worktime file loading as the admin view
        List<WorkTimeTable> viewableEntries = worktimeOperationService.getViewableEntries(year, month);
        Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap = convertToUserEntriesMap(viewableEntries);

        // Get the specific user's entries from the admin worktime data
        Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.get(userId);
        WorkTimeTable entry = null;

        if (userEntries != null) {
            entry = userEntries.get(date);
        }

        Map<String, Object> response = new HashMap<>();

        if (entry == null) {
            // No entry exists for this date
            buildNoEntryResponse(response, user, date);
            LoggerUtil.debug(this.getClass(), String.format("No entry found for user %d (%s) on %s in admin worktime file", userId, user.getName(), date));
        } else {
            // Entry exists - build detailed response
            buildDetailedEntryResponse(response, user, date, entry);
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
    private void buildNoEntryResponse(Map<String, Object> response, User user, LocalDate date) {
        response.put("hasEntry", false);
        response.put("date", date.toString());
        response.put("userId", user.getUserId());
        response.put("userName", user.getName());
        response.put("employeeId", user.getEmployeeId());
        response.put("displayFormat", "-");
        response.put("isTimeOff", false);
        response.put("isHolidayWithWork", false);
    }

    // Build detailed response for existing entries
    private void buildDetailedEntryResponse(Map<String, Object> response, User user, LocalDate date, WorkTimeTable entry) {
        // Basic information
        response.put("hasEntry", true);
        response.put("date", date.toString());
        response.put("userId", user.getUserId());
        response.put("userName", user.getName());
        response.put("employeeId", user.getEmployeeId());

        // Add all entry details
        addTimeInformation(response, entry);
        addWorkCalculations(response, entry);
        addBreakInformation(response, entry);
        addTimeOffInformation(response, entry);
        addStatusInformation(response, entry);
        addMetadata(response, entry);
    }

    // Add time-related information (start/end times, elapsed time)
    private void addTimeInformation(Map<String, Object> response, WorkTimeTable entry) {
        // Start time
        if (entry.getDayStartTime() != null) {
            response.put("dayStartTime", entry.getDayStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            response.put("startDateTime", entry.getDayStartTime().toString());
        }

        // End time
        if (entry.getDayEndTime() != null) {
            response.put("dayEndTime", entry.getDayEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            response.put("endDateTime", entry.getDayEndTime().toString());
        }

        // Calculate total elapsed time if both start and end exist
        if (entry.getDayStartTime() != null && entry.getDayEndTime() != null) {
            long elapsedMinutes = Duration.between(entry.getDayStartTime(), entry.getDayEndTime()).toMinutes();
            response.put("totalElapsedMinutes", elapsedMinutes);
            response.put("formattedElapsedTime", CalculateWorkHoursUtil.minutesToHH((int) elapsedMinutes));
        }
    }

    // Add work time calculations and formatting
    private void addWorkCalculations(Map<String, Object> response, WorkTimeTable entry) {
        // Regular work minutes
        response.put("totalWorkedMinutes", entry.getTotalWorkedMinutes());
        if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            response.put("formattedWorkTime", CalculateWorkHoursUtil.minutesToHH(entry.getTotalWorkedMinutes()));
        }

        // Overtime minutes
        response.put("totalOvertimeMinutes", entry.getTotalOvertimeMinutes());
        if (entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
            response.put("formattedOvertimeTime", CalculateWorkHoursUtil.minutesToHH(entry.getTotalOvertimeMinutes()));
        }
    }

    // Add break and temporary stop information
    private void addBreakInformation(Map<String, Object> response, WorkTimeTable entry) {
        // Temporary stops
        response.put("temporaryStopCount", entry.getTemporaryStopCount());
        response.put("totalTemporaryStopMinutes", entry.getTotalTemporaryStopMinutes());

        if (entry.getTotalTemporaryStopMinutes() != null && entry.getTotalTemporaryStopMinutes() > 0) {
            response.put("formattedTempStopTime", CalculateWorkHoursUtil.minutesToHH(entry.getTotalTemporaryStopMinutes()));
        }

        // NEW: Add temporary stops list for detailed breakdown
        if (entry.getTemporaryStops() != null && !entry.getTemporaryStops().isEmpty()) {
            response.put("temporaryStops", entry.getTemporaryStops());
        }

        // Lunch break
        response.put("lunchBreakDeducted", entry.isLunchBreakDeducted());
    }

    // Add time off type information and display formatting
    private void addTimeOffInformation(Map<String, Object> response, WorkTimeTable entry) {
        response.put("timeOffType", entry.getTimeOffType());

        if (entry.getTimeOffType() != null) {
            response.put("timeOffLabel", getTimeOffLabel(entry.getTimeOffType()));
            response.put("isTimeOff", true);

            // Special handling for SN with work (SN/4h format)
            if (WorkCode.NATIONAL_HOLIDAY_CODE.equals(entry.getTimeOffType()) && entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
                response.put("isHolidayWithWork", true);
                response.put("displayFormat", "SN/" + CalculateWorkHoursUtil.minutesToHH(entry.getTotalOvertimeMinutes()));
            } else {
                response.put("isHolidayWithWork", false);
                response.put("displayFormat", entry.getTimeOffType());
            }
        } else {
            response.put("isTimeOff", false);
            response.put("isHolidayWithWork", false);

            // Regular work display
            if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                response.put("displayFormat", CalculateWorkHoursUtil.minutesToHH(entry.getTotalWorkedMinutes()));
            } else {
                response.put("displayFormat", "-");
            }
        }
    }

    // Add administrative status information
    private void addStatusInformation(Map<String, Object> response, WorkTimeTable entry) {
        response.put("adminSync", entry.getAdminSync() != null ? entry.getAdminSync() : null);

        if (entry.getAdminSync() != null) {
            response.put("statusLabel", getStatusLabel(entry.getAdminSync()));
            response.put("statusClass", getStatusClass(entry.getAdminSync()));
        }
    }

    // Add metadata flags for frontend convenience
    private void addMetadata(Map<String, Object> response, WorkTimeTable entry) {
        response.put("hasWorkTime", entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0);
        response.put("hasOvertime", entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0);
        response.put("hasTempStops", entry.getTotalTemporaryStopMinutes() != null && entry.getTotalTemporaryStopMinutes() > 0);
        response.put("isComplete", entry.getDayStartTime() != null && entry.getDayEndTime() != null);
        response.put("hasLunchBreak", entry.isLunchBreakDeducted());
    }

    // Get human-readable time off label
    private String getTimeOffLabel(String timeOffType) {
        if (timeOffType == null) return null;

        return switch (timeOffType) {
            case WorkCode.NATIONAL_HOLIDAY_CODE -> WorkCode.NATIONAL_HOLIDAY_CODE_LONG;
            case WorkCode.TIME_OFF_CODE -> WorkCode.TIME_OFF_CODE_LONG;
            case WorkCode.MEDICAL_LEAVE_CODE -> WorkCode.MEDICAL_LEAVE_CODE_LONG;
            case WorkCode.RECOVERY_LEAVE_CODE -> WorkCode.RECOVERY_LEAVE_CODE_LONG;
            case WorkCode.UNPAID_LEAVE_CODE -> WorkCode.UNPAID_LEAVE_CODE_LONG;
            case WorkCode.SPECIAL_EVENT_CODE -> WorkCode.SPECIAL_EVENT_CODE_LONG;
            case WorkCode.WEEKEND_CODE -> WorkCode.WEEKEND_CODE_LONG;
            case WorkCode.SHORT_DAY_CODE -> WorkCode.SHORT_DAY_CODE_LONG;
            default -> timeOffType;
        };
    }

    // Get human-readable status label
    private String getStatusLabel(String adminSync) {
        if (adminSync == null) return null;

        // Handle base statuses
        switch (adminSync) {
            case MergingStatusConstants.USER_INPUT -> {
                return "User Completed";
            }
            case MergingStatusConstants.USER_IN_PROCESS -> {
                return "In Progress";
            }
            case MergingStatusConstants.ADMIN_FINAL -> {
                return "Admin Final";
            }
            case MergingStatusConstants.TEAM_FINAL -> {
                return "Team Final";
            }
            case MergingStatusConstants.ADMIN_INPUT -> {
                return "Admin Input";
            }
            case MergingStatusConstants.TEAM_INPUT -> {
                return "Team Input";
            }
        }

        // Handle timestamped edit statuses
        if (MergingStatusConstants.isTimestampedEditStatus(adminSync)) {
            String editorType = MergingStatusConstants.getEditorType(adminSync);
            return editorType + " Modified";
        }

        // Fallback for unrecognized statuses
        return "Unknown Status";
    }

    // Get CSS class for status display (for frontend styling)
    private String getStatusClass(String adminSync) {
        if (adminSync == null) return "text-muted";

        // Handle base statuses
        switch (adminSync) {
            case MergingStatusConstants.USER_INPUT -> {
                return "text-success";
            }
            case MergingStatusConstants.USER_IN_PROCESS-> {
                return "text-info";
            }
            case MergingStatusConstants.ADMIN_FINAL -> {
                return "text-danger";
            }
            case MergingStatusConstants.TEAM_FINAL  -> {
                return "text-warning";
            }
            case MergingStatusConstants.ADMIN_INPUT, MergingStatusConstants.TEAM_INPUT -> {
                return "text-primary";
            }
        }

        // Handle timestamped edit statuses
        if (MergingStatusConstants.isTimestampedEditStatus(adminSync)) {
            String editorType = MergingStatusConstants.getEditorType(adminSync);
            return switch (editorType) {
                case SecurityConstants.ROLE_USER -> "text-primary";
                case SecurityConstants.ROLE_ADMIN -> "text-warning";
                case SecurityConstants.ROLE_TEAM_LEADER -> "text-info";
                default -> "text-muted";
            };
        }

        // Fallback for unrecognized statuses
        return "text-danger"; // Use danger color to highlight unknown/problematic statuses
    }

    // Helper method to check if a time off type is a special day type
    private boolean isSpecialDayType(String timeOffType) {
        return WorkCode.NATIONAL_HOLIDAY_CODE.equals(timeOffType) ||
                WorkCode.TIME_OFF_CODE.equals(timeOffType) ||
                WorkCode.MEDICAL_LEAVE_CODE.equals(timeOffType) ||
                WorkCode.WEEKEND_CODE.equals(timeOffType);
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