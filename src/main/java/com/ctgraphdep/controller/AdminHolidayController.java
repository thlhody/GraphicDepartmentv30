package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.PaidHolidayEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.service.AdminPaidHolidayService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.HolidayHistoryService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
@Controller
@RequestMapping("/admin/holidays")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminHolidayController extends BaseController {
    private final AdminPaidHolidayService holidayService;
    private final HolidayHistoryService holidayHistoryService;

    public AdminHolidayController(UserService userService,
                                  FolderStatus folderStatus,
                                  TimeValidationService timeValidationService,
                                  AdminPaidHolidayService holidayService,
                                  HolidayHistoryService holidayHistoryService) {
        super(userService, folderStatus, timeValidationService);
        this.holidayService = holidayService;
        this.holidayHistoryService = holidayHistoryService;
    }

    @GetMapping
    public String viewHolidays(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Loading holiday list");

        // Use checkUserAccess utility method from BaseController
        String accessCheck = checkUserAccess(userDetails, "ADMIN");
        if (accessCheck != null) {
            return accessCheck;
        }

        List<PaidHolidayEntry> entries = holidayService.loadHolidayList();
        LoggerUtil.debug(this.getClass(), "Found " + entries.size() + " entries");

        model.addAttribute("entries", entries);

        // Add debug information to the model
        if (entries.isEmpty()) {
            model.addAttribute("debugInfo", "No entries found in holiday list");
        }

        return "admin/holidays";
    }

    @PostMapping("/update")
    public String updateHolidays(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer userId,
            @RequestParam Integer days,
            RedirectAttributes redirectAttributes) {

        // Use checkUserAccess utility method
        String accessCheck = checkUserAccess(userDetails, "ADMIN");
        if (accessCheck != null) {
            return accessCheck;
        }

        LoggerUtil.info(this.getClass(), String.format("Updating holiday days - User: %d, Days: %d", userId, days));

        try {
            holidayService.updateUserHolidayDays(userId, days);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Holiday days updated successfully for user " + userId);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating holiday days: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error updating holiday days: " + e.getMessage());
        }

        return "redirect:/admin/holidays";
    }

    @GetMapping("/history/{userId}")
    public String viewUserHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer userId,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Use checkUserAccess utility method
        String accessCheck = checkUserAccess(userDetails, "ADMIN");
        if (accessCheck != null) {
            return accessCheck;
        }

        try {
            Optional<User> user = getUserService().getUserById(userId);
            if (user.isEmpty()) {
                return "redirect:/admin/holidays";
            }

            List<WorkTimeTable> timeOffs = holidayHistoryService.getUserTimeOffHistory(user.get().getUsername());

            model.addAttribute("user", user.get());
            model.addAttribute("timeOffs", timeOffs);

            return "admin/holiday-history";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error viewing holiday history: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading holiday history");
            return "redirect:/admin/holidays";
        }
    }
}