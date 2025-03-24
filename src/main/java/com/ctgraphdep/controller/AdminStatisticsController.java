package com.ctgraphdep.controller;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.statistics.RegisterStatistics;
import com.ctgraphdep.service.AdminStatisticsService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequestMapping("/admin/statistics")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminStatisticsController extends BaseController {
    private final AdminStatisticsService statisticsService;

    protected AdminStatisticsController(UserService userService,
                                        FolderStatus folderStatus,
                                        TimeValidationService timeValidationService,
                                        AdminStatisticsService statisticsService) {
        super(userService, folderStatus, timeValidationService);
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public String getStatisticsPage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        // Use determineYear and determineMonth from BaseController
        int selectedYear = determineYear(year);
        int selectedMonth = determineMonth(month);

        RegisterStatistics statistics = statisticsService.calculateStatistics(selectedYear, selectedMonth);
        Map<String, Map<String, Integer>> monthlyEntries = statisticsService.getMonthlyEntriesForYear(selectedYear);
        Map<Integer, Integer> dailyEntries = statisticsService.getDailyEntriesForMonth(selectedYear, selectedMonth);

        model.addAttribute("statistics", statistics);
        model.addAttribute("monthlyEntries", monthlyEntries);
        model.addAttribute("dailyEntries", dailyEntries);
        model.addAttribute("currentYear", selectedYear);
        model.addAttribute("currentMonth", selectedMonth);
        model.addAttribute("monthNames", WorkCode.MONTH_NAMES);

        return "admin/statistics";
    }
}