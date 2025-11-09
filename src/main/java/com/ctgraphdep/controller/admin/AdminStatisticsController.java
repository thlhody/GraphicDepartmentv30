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
     * @param type "monthly" (fast - only selected month) or "yearly" (slow - all 12 months)
     */
    @GetMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatisticsData(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "monthly") String type) {

        try {
            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Calculate statistics for selected month (17 users * ~100 entries = ~1,700 entries)
            RegisterStatisticsDTO statistics = statisticsService.calculateStatistics(selectedYear, selectedMonth);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("statistics", statistics);
            response.put("year", selectedYear);
            response.put("month", selectedMonth);
            response.put("type", type);

            // Conditionally load yearly data only if requested
            if ("yearly".equalsIgnoreCase(type)) {
                // This is the expensive operation: 17 users * 12 months * ~100 entries = ~20,400 entries
                Map<String, Map<String, Integer>> monthlyEntries = statisticsService.getMonthlyEntriesForYear(selectedYear);
                Map<Integer, Integer> dailyEntries = statisticsService.getDailyEntriesForMonth(selectedYear, selectedMonth);
                response.put("monthlyEntries", monthlyEntries);
                response.put("dailyEntries", dailyEntries);
            } else {
                // For monthly statistics, return empty data for yearly charts
                response.put("monthlyEntries", null);
                response.put("dailyEntries", null);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to load statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}