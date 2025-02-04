package com.ctgraphdep.controller;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.statistics.RegisterStatistics;
import com.ctgraphdep.service.AdminStatisticsService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/admin/statistics")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminStatisticsController {
    private final AdminStatisticsService statisticsService;

    public AdminStatisticsController(AdminStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String getStatisticsPage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        LocalDate now = LocalDate.now();
        year = Optional.ofNullable(year).orElse(now.getYear());
        month = Optional.ofNullable(month).orElse(now.getMonthValue());

        RegisterStatistics statistics = statisticsService.calculateStatistics(year, month);
        Map<String, Map<String, Integer>> monthlyEntries = statisticsService.getMonthlyEntriesForYear(year);
        Map<Integer, Integer> dailyEntries = statisticsService.getDailyEntriesForMonth(year, month);

        model.addAttribute("statistics", statistics);
        model.addAttribute("monthlyEntries", monthlyEntries);
        model.addAttribute("dailyEntries", dailyEntries);
        model.addAttribute("currentYear", year);
        model.addAttribute("currentMonth", month);
        model.addAttribute("monthNames", WorkCode.MONTH_NAMES);

        return "admin/statistics";
    }
}