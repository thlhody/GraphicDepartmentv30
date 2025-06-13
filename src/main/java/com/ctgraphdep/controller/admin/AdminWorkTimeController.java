package com.ctgraphdep.controller.admin;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeSummary;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.service.*;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.WorkTimeExcelExporter;
import com.ctgraphdep.validation.TimeValidationFactory;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.ValidateHolidayDateCommand;
import com.ctgraphdep.validation.commands.ValidatePeriodCommand;
import com.ctgraphdep.worktime.display.WorktimeDisplayService;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import com.ctgraphdep.worktime.model.OperationResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REFACTORED AdminWorkTimeController using the new Command System.
 * Key Changes:
 * - Replaced WorktimeManagementService with WorktimeOperationService
 * - Uses OperationResult for standardized error handling
 * - Simplified business logic through command pattern
 * - Better separation of concerns
 */
@Controller
@RequestMapping("/admin/worktime")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminWorkTimeController extends BaseController {

    private final WorktimeOperationService worktimeOperationService;
    private final UserManagementService userManagementService;
    private final WorktimeDisplayService worktimeDisplayService;
    private final WorkTimeExcelExporter excelExporter;

    protected AdminWorkTimeController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            WorktimeOperationService worktimeOperationService,
            UserManagementService userManagementService,
            WorktimeDisplayService worktimeDisplayService,
            WorkTimeExcelExporter excelExporter) {
        super(userService, folderStatus, timeValidationService);
        this.worktimeOperationService = worktimeOperationService;
        this.userManagementService = userManagementService;
        this.worktimeDisplayService = worktimeDisplayService;
        this.excelExporter = excelExporter;
    }

    @GetMapping
    public String getWorktimePage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer selectedUserId,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            try {
                // Create and execute validation command directly
                TimeValidationFactory validationFactory = getTimeValidationService().getValidationFactory();
                ValidatePeriodCommand validateCommand = validationFactory.createValidatePeriodCommand(
                        selectedYear, selectedMonth, 24); // 24 months ahead max
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                // Handle validation failure gracefully
                String userMessage = "The selected period is not valid. You can only view periods up to 24 months in the future.";
                redirectAttributes.addFlashAttribute("periodError", userMessage);

                // Reset to current period
                LocalDate currentDate = getStandardCurrentDate();
                return "redirect:/admin/worktime?year=" + currentDate.getYear() + "&month=" + currentDate.getMonthValue();
            }

            // Get non-admin users
            List<User> nonAdminUsers = userManagementService.getNonAdminUsers();
            if (nonAdminUsers.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "No users found");
                return "redirect:/admin";
            }

            // REFACTORED: Use command system for consolidation
            LoggerUtil.info(this.getClass(), String.format(
                    "Starting worktime consolidation for %d/%d", selectedMonth, selectedYear));

            OperationResult consolidationResult = worktimeOperationService.consolidateWorkTime(selectedYear, selectedMonth);
            if (!consolidationResult.isSuccess()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Consolidation warning for %d/%d: %s", selectedMonth, selectedYear, consolidationResult.getMessage()));
                // Continue anyway - not a fatal error
            } else {
                LoggerUtil.info(this.getClass(), String.format(
                        "Consolidation completed successfully: %s", consolidationResult.getMessage()));
            }

            // REFACTORED: Use command system for viewable entries
            List<WorkTimeTable> viewableEntries = worktimeOperationService.getViewableEntries(selectedYear, selectedMonth);
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap = convertToUserEntriesMap(viewableEntries);

            // Prepare model data
            prepareWorkTimeModel(model, selectedYear, selectedMonth, selectedUserId, nonAdminUsers, userEntriesMap);

            return "admin/worktime";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/worktime";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading worktime data: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading worktime data");
            return "redirect:/admin/worktime";
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(@RequestParam int year, @RequestParam int month) {
        try {
            List<User> nonAdminUsers = userManagementService.getNonAdminUsers();

            // REFACTORED: Use command system for data retrieval
            List<WorkTimeTable> viewableEntries = worktimeOperationService.getViewableEntries(year, month);
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap = convertToUserEntriesMap(viewableEntries);

            byte[] excelData = excelExporter.exportToExcel(nonAdminUsers, userEntriesMap, year, month);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"worktime_%d_%02d.xlsx\"", year, month))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting to Excel: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/update")
    @ResponseBody
    public ResponseEntity<?> updateWorktime(
            @RequestParam Integer userId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam int day,
            @RequestParam String value) {
        try {
            LocalDate date = LocalDate.of(year, month, day);

            // REFACTORED: Use command system for admin updates
            LoggerUtil.info(this.getClass(), String.format(
                    "Processing admin worktime update: userId=%d, date=%s, value=%s", userId, date, value));

            OperationResult updateResult = worktimeOperationService.processAdminUpdate(userId, date, value);

            if (updateResult.isSuccess()) {
                // Log side effects if any
                if (updateResult.hasSideEffects()) {
                    LoggerUtil.info(this.getClass(), String.format(
                            "Admin update completed with side effects: %s", updateResult.getMessage()));

                    if (updateResult.getSideEffects().isHolidayBalanceChanged()) {
                        LoggerUtil.info(this.getClass(), String.format(
                                "Holiday balance updated: %d → %d",
                                updateResult.getSideEffects().getOldHolidayBalance(),
                                updateResult.getSideEffects().getNewHolidayBalance()));
                    }
                }

                // Trigger consolidation after update
                OperationResult consolidationResult = worktimeOperationService.consolidateWorkTime(year, month);
                if (!consolidationResult.isSuccess()) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Post-update consolidation warning: %s", consolidationResult.getMessage()));
                }

                return ResponseEntity.ok().build();
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Admin update failed: %s", updateResult.getMessage()));
                return ResponseEntity.badRequest().body(updateResult.getMessage());
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating worktime: " + e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error updating worktime: " + e.getMessage());
        }
    }

    @PostMapping("/holiday/add")
    public String addNationalHoliday(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam int day,
            RedirectAttributes redirectAttributes) {

        try {
            LocalDate holidayDate = LocalDate.of(year, month, day);

            // Validate period using the validation service from BaseController
            TimeValidationFactory validationFactory = getTimeValidationService().getValidationFactory();
            ValidatePeriodCommand periodCommand = validationFactory.createValidatePeriodCommand(year, month, 4);
            getTimeValidationService().execute(periodCommand);

            // Validate holiday date (this will check for weekends)
            ValidateHolidayDateCommand holidayCommand = validationFactory.createValidateHolidayDateCommand(holidayDate);
            try {
                getTimeValidationService().execute(holidayCommand);
            } catch (IllegalArgumentException e) {
                LoggerUtil.info(this.getClass(), "Holiday validation: " + e.getMessage());

                // Check for specific validation errors and redirect with appropriate params
                if (e.getMessage().contains("weekends")) {
                    return String.format("redirect:/admin/worktime?year=%d&month=%d&error=invalid_weekend", year, month);
                } else if (e.getMessage().contains("past months")) {
                    return String.format("redirect:/admin/worktime?year=%d&month=%d&error=invalid_past_date", year, month);
                } else {
                    // For other validation errors
                    return String.format("redirect:/admin/worktime?year=%d&month=%d&error=date_required", year, month);
                }
            }

            // REFACTORED: Use command system for adding national holidays
            LoggerUtil.info(this.getClass(), String.format(
                    "Adding national holiday for %s", holidayDate));

            OperationResult holidayResult = worktimeOperationService.addNationalHoliday(holidayDate);

            if (holidayResult.isSuccess()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "National holiday added successfully: %s", holidayResult.getMessage()));

                // Trigger consolidation after adding holiday
                OperationResult consolidationResult = worktimeOperationService.consolidateWorkTime(year, month);
                if (!consolidationResult.isSuccess()) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Post-holiday consolidation warning: %s", consolidationResult.getMessage()));
                }

                redirectAttributes.addFlashAttribute("successMessage",
                        String.format("National holiday added for %s", holidayDate));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to add national holiday: %s", holidayResult.getMessage()));
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Failed to add holiday: " + holidayResult.getMessage());
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error adding holiday: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to add holiday: " + e.getMessage());
        }

        return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);
    }

    // ========================================================================
    // PRIVATE HELPER METHODS (UNCHANGED - Still needed for display logic)
    // ========================================================================

    private void prepareWorkTimeModel(
            Model model, int year, int month, Integer selectedUserId, List<User> nonAdminUsers,
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap) {

        // Calculate summaries using display service (not deprecated)
        Map<Integer, WorkTimeSummary> summaries = worktimeDisplayService.calculateUserSummaries(
                userEntriesMap, nonAdminUsers, year, month);

        // Add model attributes
        model.addAttribute("currentYear", year);
        model.addAttribute("currentMonth", month);
        model.addAttribute("users", nonAdminUsers);
        model.addAttribute("userSummaries", summaries);
        model.addAttribute("daysInMonth", YearMonth.of(year, month).lengthOfMonth());
        model.addAttribute("dayHeaders", worktimeDisplayService.prepareDayHeaders(YearMonth.of(year, month)));
        model.addAttribute("userEntriesMap", userEntriesMap);

        Map<String, Long> entryCounts = calculateEntryCounts(userEntriesMap);
        model.addAttribute("entryCounts", entryCounts);

        // Handle selected user
        if (selectedUserId != null) {
            nonAdminUsers.stream()
                    .filter(user -> user.getUserId().equals(selectedUserId))
                    .findFirst()
                    .ifPresent(user -> {
                        model.addAttribute("selectedUserId", selectedUserId);
                        model.addAttribute("selectedUserName", user.getName());
                        model.addAttribute("selectedUserWorktime", userEntriesMap.get(selectedUserId));
                    });
        }
    }

    private Map<Integer, Map<LocalDate, WorkTimeTable>> convertToUserEntriesMap(List<WorkTimeTable> entries) {
        return entries.stream().collect(Collectors.groupingBy(
                WorkTimeTable::getUserId,
                Collectors.toMap(WorkTimeTable::getWorkDate, entry -> entry,
                        (existing, replacement) -> replacement, TreeMap::new)
        ));
    }

    private Map<String, Long> calculateEntryCounts(Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap) {
        Map<String, Long> counts = new HashMap<>();

        List<WorkTimeTable> allEntries = userEntriesMap.values().stream()
                .flatMap(map -> map.values().stream()).toList();

        // Count time off types
        counts.put("snCount", allEntries.stream()
                .filter(e -> WorkCode.NATIONAL_HOLIDAY_CODE.equals(e.getTimeOffType())).count());
        counts.put("coCount", allEntries.stream()
                .filter(e -> WorkCode.TIME_OFF_CODE.equals(e.getTimeOffType())).count());
        counts.put("cmCount", allEntries.stream()
                .filter(e -> WorkCode.MEDICAL_LEAVE_CODE.equals(e.getTimeOffType())).count());

        // Count by status
        counts.put("adminEditedCount", allEntries.stream()
                .filter(e -> SyncStatusMerge.ADMIN_EDITED.equals(e.getAdminSync())).count());
        counts.put("userInputCount", allEntries.stream()
                .filter(e -> SyncStatusMerge.USER_INPUT.equals(e.getAdminSync())).count());
        counts.put("syncedCount", allEntries.stream()
                .filter(e -> SyncStatusMerge.USER_DONE.equals(e.getAdminSync())).count());

        return counts;
    }
}