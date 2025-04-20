package com.ctgraphdep.controller.admin;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.SyncStatusWorktime;
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

@Controller
@RequestMapping("/admin/worktime")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminWorkTimeController extends BaseController {
    private final WorktimeManagementService worktimeManagementService;
    private final UserManagementService userManagementService;
    private final WorktimeDisplayService worktimeDisplayService;
    private final WorkTimeExcelExporter excelExporter;

    protected AdminWorkTimeController(UserService userService,
                                      FolderStatus folderStatus,
                                      TimeValidationService timeValidationService,
                                      WorktimeManagementService worktimeManagementService,
                                      UserManagementService userManagementService,
                                      WorktimeDisplayService worktimeDisplayService,
                                      WorkTimeExcelExporter excelExporter) {
        super(userService, folderStatus, timeValidationService);
        this.worktimeManagementService = worktimeManagementService;
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

            // Validate period using the validation service from BaseController
            TimeValidationFactory validationFactory = getTimeValidationService().getValidationFactory();
            ValidatePeriodCommand periodCommand = validationFactory.createValidatePeriodCommand(
                    selectedYear, selectedMonth, 4);
            getTimeValidationService().execute(periodCommand);

            // Get non-admin users
            List<User> nonAdminUsers = userManagementService.getNonAdminUsers();
            if (nonAdminUsers.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "No users found");
                return "redirect:/admin";
            }

            // Consolidate worktime entries and organize by user and date
            worktimeManagementService.consolidateWorkTimeEntries(selectedYear, selectedMonth);
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap = convertToUserEntriesMap(worktimeManagementService.getViewableEntries(selectedYear, selectedMonth));

            // Prepare model data
            prepareWorkTimeModel(model, selectedYear, selectedMonth, selectedUserId, nonAdminUsers, userEntriesMap);

            return "admin/worktime";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/worktime";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading worktime data");
            return "redirect:/admin/worktime";
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(
            @RequestParam int year,
            @RequestParam int month) {
        try {
            List<User> nonAdminUsers = userManagementService.getNonAdminUsers();
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap = convertToUserEntriesMap(worktimeManagementService.getViewableEntries(year, month));

            byte[] excelData = excelExporter.exportToExcel(
                    nonAdminUsers,
                    userEntriesMap,
                    year,
                    month
            );

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
            worktimeManagementService.processWorktimeUpdate(userId, date, value);
            // Trigger consolidation after update
            worktimeManagementService.consolidateWorkTimeEntries(year, month);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating worktime: " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
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

            worktimeManagementService.addNationalHoliday(holidayDate);

            // Trigger consolidation after adding holiday
            worktimeManagementService.consolidateWorkTimeEntries(year, month);

            redirectAttributes.addFlashAttribute("successMessage", String.format("National holiday added for %s", holidayDate));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error adding holiday: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to add holiday: " + e.getMessage());
        }

        return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);
    }

    private void prepareWorkTimeModel(Model model, int year, int month, Integer selectedUserId,
                                      List<User> nonAdminUsers,
                                      Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap) {

        // Calculate summaries
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

    private Map<Integer, Map<LocalDate, WorkTimeTable>> convertToUserEntriesMap(
            List<WorkTimeTable> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(
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
                .filter(e -> SyncStatusWorktime.ADMIN_EDITED.equals(e.getAdminSync())).count());
        counts.put("userInputCount", allEntries.stream()
                .filter(e -> SyncStatusWorktime.USER_INPUT.equals(e.getAdminSync())).count());
        counts.put("syncedCount", allEntries.stream()
                .filter(e -> SyncStatusWorktime.USER_DONE.equals(e.getAdminSync())).count());

        return counts;
    }
}