package com.ctgraphdep.controller;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeSummary;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.service.AdminWorkTimeDisplayService;
import com.ctgraphdep.service.UserManagementService;
import com.ctgraphdep.service.WorkTimeConsolidationService;
import com.ctgraphdep.service.WorkTimeManagementService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.WorkTimeExcelExporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/worktime")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminWorkTimeController {
    private final WorkTimeManagementService workTimeManagementService;
    private final WorkTimeConsolidationService workTimeConsolidationService;
    private final UserManagementService userManagementService;
    private final AdminWorkTimeDisplayService displayService;
    private final WorkTimeExcelExporter excelExporter;

    public AdminWorkTimeController(
            WorkTimeManagementService workTimeManagementService,
            WorkTimeConsolidationService workTimeConsolidationService,
            UserManagementService userManagementService,
            AdminWorkTimeDisplayService displayService,
            WorkTimeExcelExporter excelExporter) {
        this.workTimeManagementService = workTimeManagementService;
        this.workTimeConsolidationService = workTimeConsolidationService;
        this.userManagementService = userManagementService;
        this.displayService = displayService;
        this.excelExporter = excelExporter;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String getWorktimePage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer selectedUserId,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            // Set default year and month
            LocalDate now = LocalDate.now();
            year = Optional.ofNullable(year).orElse(now.getYear());
            month = Optional.ofNullable(month).orElse(now.getMonthValue());

            // Validate period
            validatePeriod(year, month);

            // Get non-admin users
            List<User> nonAdminUsers = userManagementService.getNonAdminUsers();
            if (nonAdminUsers.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "No users found");
                return "redirect:/admin";
            }

            // Consolidate worktime entries and organize by user and date
            workTimeConsolidationService.consolidateWorkTimeEntries(year, month);
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap =
                    convertToUserEntriesMap(workTimeConsolidationService.getViewableEntries(year, month));

            // Prepare model data
            prepareWorkTimeModel(model, year, month, selectedUserId, nonAdminUsers, userEntriesMap);

            return "admin/worktime";

        } catch (IllegalArgumentException e) {
            LoggerUtil.error(this.getClass(), "Validation error: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/worktime";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error preparing worktime page: " + e.getMessage());
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
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap =
                    convertToUserEntriesMap(workTimeConsolidationService.getViewableEntries(year, month));

            byte[] excelData = excelExporter.exportToExcel(
                    nonAdminUsers,
                    userEntriesMap,
                    year,
                    month
            );

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            String.format("attachment; filename=\"worktime_%d_%02d.xlsx\"", year, month))
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
            workTimeManagementService.processWorktimeUpdate(userId, date, value);
            // Trigger consolidation after update
            workTimeConsolidationService.consolidateWorkTimeEntries(year, month);
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
            validateHolidayDate(holidayDate);
            workTimeManagementService.addNationalHoliday(holidayDate);

            // Trigger consolidation after adding holiday
            workTimeConsolidationService.consolidateWorkTimeEntries(year, month);

            redirectAttributes.addFlashAttribute("successMessage", String.format("National holiday added for %s", holidayDate));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error adding holiday: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to add holiday: " + e.getMessage());
        }

        return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);
    }

    private void prepareWorkTimeModel(Model model, int year, int month, Integer selectedUserId,
                                      List<User> nonAdminUsers, Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap) {

        // Calculate summaries
        Map<Integer, WorkTimeSummary> summaries = displayService.calculateUserSummaries(userEntriesMap, nonAdminUsers, year, month);

        // Add model attributes
        model.addAttribute("currentYear", year);
        model.addAttribute("currentMonth", month);
        model.addAttribute("users", nonAdminUsers);
        model.addAttribute("userSummaries", summaries);
        model.addAttribute("daysInMonth", YearMonth.of(year, month).lengthOfMonth());
        model.addAttribute("dayHeaders", displayService.prepareDayHeaders(YearMonth.of(year, month)));
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
                        Collectors.toMap(
                                WorkTimeTable::getWorkDate,
                                entry -> entry,
                                (existing, replacement) -> replacement,
                                TreeMap::new
                        )
                ));
    }

    private void validatePeriod(int year, int month) {
        YearMonth requested = YearMonth.of(year, month);
        YearMonth current = YearMonth.now();
        YearMonth maxAllowed = current.plusMonths(4);

        if (requested.isAfter(maxAllowed)) {
            throw new IllegalArgumentException("Cannot view future periods beyond next month");
        }
    }

    private void validateHolidayDate(LocalDate date) {
        LocalDate now = LocalDate.now();

        if (date.isBefore(now.withDayOfMonth(1))) {
            throw new IllegalArgumentException("Cannot add holidays for past months");
        }

        if (date.getDayOfWeek() == DayOfWeek.SATURDAY ||
                date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException("Cannot add holidays on weekends");
        }
    }

    private Map<String, Long> calculateEntryCounts(Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap) {
        Map<String, Long> counts = new HashMap<>();

        List<WorkTimeTable> allEntries = userEntriesMap.values().stream()
                .flatMap(map -> map.values().stream())
                .toList();

        // Count time off types
        counts.put("snCount", allEntries.stream().filter(e -> WorkCode.NATIONAL_HOLIDAY_CODE.equals(e.getTimeOffType())).count());
        counts.put("coCount", allEntries.stream().filter(e -> WorkCode.TIME_OFF_CODE.equals(e.getTimeOffType())).count());
        counts.put("cmCount", allEntries.stream().filter(e -> WorkCode.MEDICAL_LEAVE_CODE.equals(e.getTimeOffType())).count());

        // Count by status
        counts.put("adminEditedCount", allEntries.stream().filter(e -> SyncStatus.ADMIN_EDITED.equals(e.getAdminSync())).count());
        counts.put("userInputCount", allEntries.stream().filter(e -> SyncStatus.USER_INPUT.equals(e.getAdminSync())).count());
        counts.put("syncedCount", allEntries.stream().filter(e -> SyncStatus.USER_DONE.equals(e.getAdminSync())).count());

        return counts;
    }
}