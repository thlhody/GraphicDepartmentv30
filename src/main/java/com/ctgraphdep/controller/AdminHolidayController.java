package com.ctgraphdep.controller;

import com.ctgraphdep.model.PaidHolidayEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.service.AdminPaidHolidayService;
import com.ctgraphdep.service.HolidayHistoryService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/holidays")
@PreAuthorize("hasRole('ADMIN')")
public class AdminHolidayController {
    private final AdminPaidHolidayService holidayService;
    private final HolidayHistoryService holidayHistoryService;
    private final UserService userService;

    public AdminHolidayController(AdminPaidHolidayService holidayService, HolidayHistoryService holidayHistoryService, UserService userService) {
        this.holidayService = holidayService;
        this.holidayHistoryService = holidayHistoryService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), "Initializing Paid Holiday Controller");
    }

    @GetMapping
    public String viewHolidays(Model model) {
        LoggerUtil.info(this.getClass(), "Loading holiday list");

        List<PaidHolidayEntry> entries = holidayService.loadHolidayList();
        LoggerUtil.info(this.getClass(), "Found " + entries.size() + " entries");

        model.addAttribute("entries", entries);

        // Add debug information to the model
        if (entries.isEmpty()) {
            model.addAttribute("debugInfo", "No entries found in holiday list");
        }

        return "admin/holidays";
    }

    @PostMapping("/update")
    public String updateHolidays(
            @RequestParam Integer userId,
            @RequestParam Integer days,
            RedirectAttributes redirectAttributes) {

        LoggerUtil.info(this.getClass(),
                String.format("Updating holiday days - User: %d, Days: %d",
                        userId, days));

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
    public String viewUserHistory(@PathVariable Integer userId, Model model) {
        Optional<User> user = userService.getUserById(userId);
        if (user.isEmpty()) {
            return "redirect:/admin/holidays";
        }

        List<WorkTimeTable> timeOffs = holidayHistoryService.getUserTimeOffHistory(user.get().getUsername());

        model.addAttribute("user", user.get());
        model.addAttribute("timeOffs", timeOffs);

        return "admin/holiday-history";
    }
}