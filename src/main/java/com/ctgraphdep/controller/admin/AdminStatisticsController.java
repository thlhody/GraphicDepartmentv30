package com.ctgraphdep.controller.admin;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.dto.statistics.RegisterStatisticsDTO;
import com.ctgraphdep.service.AdminStatisticsService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/statistics")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminStatisticsController extends BaseController {

    private final AdminStatisticsService statisticsService;

    protected AdminStatisticsController(UserService userService, FolderStatus folderStatus, TimeValidationService timeValidationService, AdminStatisticsService statisticsService) {
        super(userService, folderStatus, timeValidationService);
        this.statisticsService = statisticsService;
    }

    /**
     * Initial page load - no data loaded, just renders the page
     */
    @GetMapping
    public String getStatisticsPage(Model model) {
        // Use determineYear and determineMonth from BaseController for default values
        int currentYear = determineYear(null);
        int currentMonth = determineMonth(null);

        model.addAttribute("currentYear", currentYear);
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute("monthNames", WorkCode.MONTH_NAMES);

        return "admin/statistics";
    }

    /**
     * AJAX endpoint to load statistics data asynchronously
     */
    @GetMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatisticsData(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        try {
            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Calculate statistics - this is the heavy operation
            RegisterStatisticsDTO statistics = statisticsService.calculateStatistics(selectedYear, selectedMonth);
            Map<String, Map<String, Integer>> monthlyEntries = statisticsService.getMonthlyEntriesForYear(selectedYear);
            Map<Integer, Integer> dailyEntries = statisticsService.getDailyEntriesForMonth(selectedYear, selectedMonth);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("statistics", statistics);
            response.put("monthlyEntries", monthlyEntries);
            response.put("dailyEntries", dailyEntries);
            response.put("year", selectedYear);
            response.put("month", selectedMonth);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to load statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}